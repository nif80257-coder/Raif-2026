package com.cinemax.app.nuvio

import com.cinemax.app.plugins.PluginSecurityPolicy
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLParameter
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithContentsOfFile
import platform.JavaScriptCore.JSContext

actual fun createNuvioScriptRuntime(http: HttpClient): NuvioScriptRuntime = IosNuvioScriptRuntime(http)

/**
 * Runs Nuvio provider bundles (`module.exports = { getStreams }`) in JavaScriptCore.
 *
 * The bridge is JSON only: the prelude queues `fetch` calls and timers, and this
 * driver drains them, so no native JavaScript function bridging is involved.
 * The driver runs on the main dispatcher because a JSContext is not thread safe;
 * HTTP calls suspend, so waiting on a provider never blocks the UI.
 * Bundled modules that scrapers commonly require are provided by the shipped
 * `nuvio-compat.js` shim (a static-HTML cheerio and the crypto-js surface); the
 * shim is evaluated separately and a failure there leaves `require` untouched,
 * so a broken shim can never take working providers down with it.
 */
class IosNuvioScriptRuntime(private val http: HttpClient) : NuvioScriptRuntime {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val compatModule: String? by lazy { readBundledCompatScript() }

    override suspend fun streams(source: String, request: NuvioMediaRequest): List<NuvioStream> =
        withContext(Dispatchers.Main) {
            val context = JSContext()
            try {
                evaluate(context, PRELUDE)
                compatScript()?.let { shim -> runCatching { evaluate(context, shim) } }
                evaluate(context, source)
                evaluate(
                    context,
                    buildString {
                        append("globalThis.__cinemax_start(")
                        append(jsString(request.tmdbId))
                        append(", ")
                        append(jsString(request.mediaType))
                        append(", ")
                        append(request.season?.toString() ?: "null")
                        append(", ")
                        append(request.episode?.toString() ?: "null")
                        append(")")
                    }
                )
                drive(context)
            } finally {
                context.exception = null
            }
        }

    private fun evaluate(context: JSContext, script: String): String {
        val value = context.evaluateScript(script)
        val failure = context.exception
        if (failure != null) {
            context.exception = null
            throw IllegalStateException("JS: ${failure.toString()}")
        }
        return value?.toString() ?: ""
    }

    /** The cheerio/crypto-js shim bundled into the app, read once. */
    private fun compatScript(): String? = compatModule

    private suspend fun drive(context: JSContext): List<NuvioStream> {
        var idle = 0
        repeat(MAX_STEPS) {
            val pending = parseArray(evaluate(context, "globalThis.__cinemax_pendingRequests()"))
            for (element in pending) {
                val request = element as? JsonObject ?: continue
                val id = (request["id"] as? JsonPrimitive)?.intOrNull ?: continue
                val url = (request["url"] as? JsonPrimitive)?.contentOrNull ?: continue
                val method = (request["method"] as? JsonPrimitive)?.contentOrNull ?: "GET"
                val body = (request["body"] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
                val payload = performRequest(url, method, request["headers"] as? JsonObject, body)
                evaluate(
                    context,
                    "globalThis.__cinemax_settle($id, ${jsJson(json.encodeToString(JsonObject.serializer(), payload))})"
                )
            }

            val dueTimers = parseArray(evaluate(context, "globalThis.__cinemax_dueTimers()"))
            for (element in dueTimers) {
                val id = (element as? JsonPrimitive)?.intOrNull ?: continue
                evaluate(context, "globalThis.__cinemax_fireTimer($id)")
            }

            val outcome = parseObject(evaluate(context, "globalThis.__cinemax_outcome()"))
            if ((outcome["done"] as? JsonPrimitive)?.booleanOrNull == true) {
                if ((outcome["failed"] as? JsonPrimitive)?.booleanOrNull == true) {
                    throw IllegalStateException(
                        (outcome["error"] as? JsonPrimitive)?.contentOrNull ?: "فشل تشغيل المصدر"
                    )
                }
                return parseStreams(outcome["value"])
            }

            val pendingTimers = evaluate(context, "globalThis.__cinemax_timerCount()").toIntOrNull() ?: 0
            if (pending.isEmpty() && pendingTimers == 0) {
                idle += 1
                if (idle >= IDLE_LIMIT) return emptyList()
            } else {
                idle = 0
            }
            if (pending.isEmpty() && dueTimers.isEmpty()) {
                delay(SLEEP_MILLIS)
            }
        }
        return emptyList()
    }

    /**
     * Rewrites a request through the viewer's edge proxy when one is set and the
     * host matches. The proxy URL is expected to end with `?destination=`.
     */
    private fun proxiedUrl(url: String): String {
        val defaults = NSUserDefaults.standardUserDefaults
        val prefix = defaults.stringForKey("cinemax.nuvio.proxy")?.trim().orEmpty()
        if (prefix.isEmpty() || !prefix.startsWith("https://")) return url
        val hosts = defaults.stringForKey("cinemax.nuvio.proxyHosts")?.trim().orEmpty()
        val matches = hosts.isEmpty() || hosts.split(',').map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .any { it in url.lowercase() }
        if (!matches) return url
        return prefix + url.encodeURLParameter()
    }

    /** The ShowBox/FebBox cookie the viewer pasted, for the hosts that need it. */
    private fun providerCookie(url: String): String? {
        val cookie = NSUserDefaults.standardUserDefaults
            .stringForKey("cinemax.nuvio.showboxCookie")?.trim().orEmpty()
        if (cookie.isEmpty()) return null
        val host = url.substringAfter("://", "").substringBefore('/').lowercase()
        val needsIt = "febbox" in host || "showbox" in host || "pstream" in host
        return if (needsIt) "ui=$cookie" else null
    }

    private suspend fun performRequest(url: String, method: String, headers: JsonObject?, body: String?): JsonObject {
        // A provider that only answers through a user-supplied edge proxy gets that
        // proxy here, and the cookie some hosts demand is added without the script
        // having to know about it.
        val target = proxiedUrl(url)
        if (!NuvioUrls.isSecure(target)) {
            return buildJsonObject {
                put("ok", false)
                put("status", 0)
                put("url", url)
                put("error", "insecure-url")
            }
        }
        return try {
            val response = http.request(target) {
                this.method = HttpMethod.parse(method.uppercase())
                headers?.forEach { (name, value) ->
                    (value as? JsonPrimitive)?.contentOrNull?.let { header(name, it) }
                }
                providerCookie(url)?.let { header("Cookie", it) }
                if (body != null) setBody(body)
            }
            val text = response.bodyAsText()
            if (text.length.toLong() > PluginSecurityPolicy.MAX_RESPONSE_BYTES) {
                throw IllegalStateException("response-too-large")
            }
            buildJsonObject {
                put("ok", response.status.value in 200..299)
                put("status", response.status.value)
                put("url", response.call.request.url.toString())
                put("headers", buildJsonObject {
                    response.headers.entries().forEach { entry ->
                        put(entry.key, entry.value.joinToString(", "))
                    }
                })
                put("body", text)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            buildJsonObject {
                put("ok", false)
                put("status", 0)
                put("url", url)
                put("error", error.message ?: "request-failed")
            }
        }
    }

    private fun parseArray(text: String): List<JsonElement> = runCatching {
        json.parseToJsonElement(text) as? JsonArray
    }.getOrNull()?.toList() ?: emptyList()

    private fun parseObject(text: String): JsonObject = runCatching {
        json.parseToJsonElement(text) as? JsonObject
    }.getOrNull() ?: JsonObject(emptyMap())

    private fun parseStreams(element: JsonElement?): List<NuvioStream> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { entry ->
            runCatching { json.decodeFromString(NuvioStream.serializer(), entry.toString()) }
                .getOrNull()
                ?.takeIf { !it.url.isNullOrBlank() }
        }
    }

    private companion object {
        const val MAX_STEPS = 400
        const val IDLE_LIMIT = 20
        const val SLEEP_MILLIS = 50L
    }
}

private fun jsString(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\u2028' -> append("\\u2028")
            '\u2029' -> append("\\u2029")
            else -> if (character < ' ') {
                append("\\u" + character.code.toString(16).padStart(4, '0'))
            } else {
                append(character)
            }
        }
    }
    append('"')
}

private fun jsJson(value: String): String =
    value.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

/**
 * Host shims for Nuvio providers: CommonJS module wrapper, `console`, base64,
 * `fetch` over queued requests, `setTimeout`, a tiny axios shim, and the JSON
 * entry points the Kotlin driver calls. No `$` characters, so the string stays
 * free of Kotlin templates.
 */
private val PRELUDE = """
(function () {
  var requests = [];
  var timers = [];
  var logs = [];
  var nextRequestId = 1;
  var nextTimerId = 1;
  var outcome = { done: false, failed: false, value: null, error: null };
  var modules = {};
  var moduleObject = { exports: {} };

  globalThis.module = moduleObject;
  globalThis.exports = moduleObject.exports;
  globalThis.global = globalThis;
  globalThis.window = globalThis;
  globalThis.process = { env: {}, platform: "cinemax-ios", version: "1.0.0" };

  function pushLog(level, args) {
    var parts = [];
    for (var i = 0; i < args.length; i++) {
      var value = args[i];
      try {
        parts.push(typeof value === "string" ? value : JSON.stringify(value));
      } catch (error) {
        parts.push(String(value));
      }
    }
    logs.push(level + ": " + parts.join(" "));
    if (logs.length > 100) logs.shift();
  }

  globalThis.console = {
    log: function () { pushLog("log", arguments); },
    info: function () { pushLog("info", arguments); },
    warn: function () { pushLog("warn", arguments); },
    error: function () { pushLog("error", arguments); },
    debug: function () { pushLog("debug", arguments); }
  };

  var base64Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

  globalThis.btoa = function (input) {
    var text = String(input);
    var output = "";
    var index = 0;
    while (index < text.length) {
      var c1 = text.charCodeAt(index++) & 255;
      var has2 = index < text.length;
      var c2 = has2 ? text.charCodeAt(index++) & 255 : 0;
      var has3 = index < text.length;
      var c3 = has3 ? text.charCodeAt(index++) & 255 : 0;
      var e1 = c1 >> 2;
      var e2 = ((c1 & 3) << 4) | (c2 >> 4);
      var e3 = ((c2 & 15) << 2) | (c3 >> 6);
      var e4 = c3 & 63;
      output += base64Alphabet.charAt(e1);
      output += base64Alphabet.charAt(e2);
      output += has2 ? base64Alphabet.charAt(e3) : "=";
      output += has3 ? base64Alphabet.charAt(e4) : "=";
    }
    return output;
  };

  globalThis.atob = function (input) {
    var text = String(input).replace(/[^A-Za-z0-9+/=]/g, "");
    var output = "";
    var index = 0;
    while (index < text.length) {
      var c1 = base64Alphabet.indexOf(text.charAt(index++));
      var c2 = base64Alphabet.indexOf(text.charAt(index++));
      var c3 = base64Alphabet.indexOf(text.charAt(index++));
      var c4 = base64Alphabet.indexOf(text.charAt(index++));
      output += String.fromCharCode((c1 << 2) | (c2 >> 4));
      if (c3 >= 0) output += String.fromCharCode(((c2 & 15) << 4) | (c3 >> 2));
      if (c4 >= 0) output += String.fromCharCode(((c3 & 3) << 6) | c4);
    }
    return output;
  };

  function makeHeaders(raw) {
    var map = {};
    if (raw) {
      for (var key in raw) {
        if (Object.prototype.hasOwnProperty.call(raw, key)) {
          map[String(key).toLowerCase()] = String(raw[key]);
        }
      }
    }
    return {
      get: function (name) {
        var value = map[String(name).toLowerCase()];
        return value === undefined ? null : value;
      },
      has: function (name) {
        return Object.prototype.hasOwnProperty.call(map, String(name).toLowerCase());
      },
      forEach: function (callback) {
        for (var key in map) {
          if (Object.prototype.hasOwnProperty.call(map, key)) callback(map[key], key);
        }
      },
      toJSON: function () { return map; }
    };
  }

  function makeResponse(payload) {
    var body = typeof payload.body === "string" ? payload.body : "";
    var status = typeof payload.status === "number" ? payload.status : 0;
    return {
      ok: payload.ok === true,
      status: status,
      statusText: payload.statusText || "",
      url: payload.url || "",
      headers: makeHeaders(payload.headers),
      text: function () { return Promise.resolve(body); },
      json: function () {
        return Promise.resolve(body).then(function (value) { return JSON.parse(value); });
      }
    };
  }

  globalThis.fetch = function (input, init) {
    var options = init || {};
    var url = String(input);
    var method = options.method ? String(options.method).toUpperCase() : "GET";
    var headerMap = {};
    if (options.headers) {
      for (var key in options.headers) {
        if (Object.prototype.hasOwnProperty.call(options.headers, key)) {
          headerMap[String(key)] = String(options.headers[key]);
        }
      }
    }
    var body = options.body === undefined || options.body === null ? null : String(options.body);
    var id = nextRequestId++;
    return new Promise(function (resolve, reject) {
      requests.push({
        id: id,
        url: url,
        method: method,
        headers: headerMap,
        body: body,
        resolve: resolve,
        reject: reject
      });
    });
  };

  globalThis.setTimeout = function (callback, delay) {
    var args = [];
    for (var i = 2; i < arguments.length; i++) args.push(arguments[i]);
    var id = nextTimerId++;
    timers.push({ id: id, callback: callback, args: args, due: Date.now() + (Number(delay) || 0) });
    return id;
  };

  globalThis.clearTimeout = function (id) {
    for (var i = 0; i < timers.length; i++) {
      if (timers[i].id === id) {
        timers.splice(i, 1);
        return;
      }
    }
  };

  globalThis.__cinemax_pendingRequests = function () {
    var list = [];
    for (var i = 0; i < requests.length; i++) {
      var request = requests[i];
      list.push({
        id: request.id,
        url: request.url,
        method: request.method,
        headers: request.headers,
        body: request.body
      });
    }
    return JSON.stringify(list);
  };

  globalThis.__cinemax_settle = function (id, payload) {
    var index = -1;
    for (var i = 0; i < requests.length; i++) {
      if (requests[i].id === id) {
        index = i;
        break;
      }
    }
    if (index < 0) return;
    var request = requests[index];
    requests.splice(index, 1);
    if (payload && payload.error && !payload.status) {
      request.reject(new Error(String(payload.error)));
      return;
    }
    request.resolve(makeResponse(payload || {}));
  };

  globalThis.__cinemax_dueTimers = function () {
    var now = Date.now();
    var due = [];
    for (var i = 0; i < timers.length; i++) {
      if (timers[i].due <= now) due.push(timers[i].id);
    }
    return JSON.stringify(due);
  };

  globalThis.__cinemax_timerCount = function () {
    return String(timers.length);
  };

  globalThis.__cinemax_fireTimer = function (id) {
    for (var i = 0; i < timers.length; i++) {
      if (timers[i].id === id) {
        var timer = timers[i];
        timers.splice(i, 1);
        try {
          timer.callback.apply(null, timer.args);
        } catch (error) {
          pushLog("error", [String(error)]);
        }
        return;
      }
    }
  };

  globalThis.__cinemax_outcome = function () {
    return JSON.stringify({
      done: outcome.done,
      failed: outcome.failed,
      value: outcome.value,
      error: outcome.error
    });
  };

  globalThis.__cinemax_start = function (tmdbId, mediaType, season, episode) {
    try {
      var exported = moduleObject.exports || {};
      var handler = exported.getStreams || exported.default;
      if (typeof handler !== "function" && typeof globalThis.getStreams === "function") {
        handler = globalThis.getStreams;
      }
      if (typeof handler !== "function") {
        outcome = { done: true, failed: true, value: null, error: "السكربت لا يصدّر getStreams" };
        return;
      }
      var result = handler(tmdbId, mediaType, season, episode);
      Promise.resolve(result).then(function (value) {
        outcome = { done: true, failed: false, value: value, error: null };
      }, function (error) {
        outcome = { done: true, failed: true, value: null, error: String(error) };
      });
    } catch (error) {
      outcome = { done: true, failed: true, value: null, error: String(error) };
    }
  };

  function axiosResponse(responsePromise) {
    return responsePromise.then(function (response) {
      return response.text().then(function (text) {
        var data = text;
        try {
          data = JSON.parse(text);
        } catch (error) {
          data = text;
        }
        return { data: data, status: response.status, headers: response.headers, config: {} };
      });
    });
  }

  var axiosShim = {
    get: function (url, config) {
      return axiosResponse(globalThis.fetch(url, config || {}));
    },
    post: function (url, body, config) {
      var options = config || {};
      options.method = "POST";
      options.body = typeof body === "string" ? body : JSON.stringify(body);
      return axiosResponse(globalThis.fetch(url, options));
    },
    request: function (config) {
      return axiosResponse(globalThis.fetch(config.url, config));
    }
  };

  function copyInto(target, source) {
    for (var key in (source || {})) {
      if (Object.prototype.hasOwnProperty.call(source, key)) target[key] = source[key];
    }
    return target;
  }

  function baseConfig(base, override) {
    var result = {};
    copyInto(result, base);
    copyInto(result, override);
    var headers = {};
    copyInto(headers, (base || {}).headers);
    copyInto(headers, (override || {}).headers);
    result.headers = headers;
    return result;
  }

  axiosShim.create = function (defaults) {
    var base = defaults || {};
    base.headers = copyInto({}, base.headers);
    return {
      defaults: base,
      get: function (url, config) { return axiosShim.get(url, baseConfig(base, config)); },
      post: function (url, body, config) { return axiosShim.post(url, body, baseConfig(base, config)); },
      request: function (config) { return axiosShim.request(baseConfig(base, config)); }
    };
  };

  axiosShim.isAxiosError = function () { return false; };

  globalThis.URLSearchParams = function (init) {
    this.pairs = [];
    var text = init === undefined || init === null ? "" : String(init);
    if (text.charAt(0) === "?") text = text.substring(1);
    if (text.length > 0) {
      var chunks = text.split("&");
      for (var i = 0; i < chunks.length; i++) {
        var chunk = chunks[i];
        if (chunk.length === 0) continue;
        var split = chunk.indexOf("=");
        var key = split < 0 ? chunk : chunk.substring(0, split);
        var value = split < 0 ? "" : chunk.substring(split + 1);
        try {
          this.pairs.push([decodeURIComponent(key), decodeURIComponent(value)]);
        } catch (error) {
          this.pairs.push([key, value]);
        }
      }
    }
  };
  globalThis.URLSearchParams.prototype.get = function (name) {
    for (var i = 0; i < this.pairs.length; i++) {
      if (this.pairs[i][0] === String(name)) return this.pairs[i][1];
    }
    return null;
  };
  globalThis.URLSearchParams.prototype.has = function (name) {
    return this.get(name) !== null;
  };
  globalThis.URLSearchParams.prototype.append = function (name, value) {
    this.pairs.push([String(name), String(value)]);
  };
  globalThis.URLSearchParams.prototype.set = function (name, value) {
    for (var i = 0; i < this.pairs.length; i++) {
      if (this.pairs[i][0] === String(name)) {
        this.pairs[i][1] = String(value);
        return;
      }
    }
    this.pairs.push([String(name), String(value)]);
  };
  globalThis.URLSearchParams.prototype.toString = function () {
    var parts = [];
    for (var i = 0; i < this.pairs.length; i++) {
      parts.push(encodeURIComponent(this.pairs[i][0]) + "=" + encodeURIComponent(this.pairs[i][1]));
    }
    return parts.join("&");
  };

  globalThis.URL = function (input, base) {
    var href = String(input);
    if (base && href.indexOf("://") < 0) {
      href = String(base).replace(/\/+$/, "") + "/" + href.replace(/^\/+/, "");
    }
    this.href = href;
    var schemeEnd = href.indexOf("://");
    this.protocol = schemeEnd < 0 ? "" : href.substring(0, schemeEnd + 1);
    var rest = schemeEnd < 0 ? href : href.substring(schemeEnd + 3);
    var hashIndex = rest.indexOf("#");
    this.hash = hashIndex < 0 ? "" : rest.substring(hashIndex);
    if (hashIndex >= 0) rest = rest.substring(0, hashIndex);
    var queryIndex = rest.indexOf("?");
    this.search = queryIndex < 0 ? "" : rest.substring(queryIndex);
    if (queryIndex >= 0) rest = rest.substring(0, queryIndex);
    var slash = rest.indexOf("/");
    this.host = slash < 0 ? rest : rest.substring(0, slash);
    this.hostname = this.host.split(":")[0];
    this.pathname = slash < 0 ? "/" : rest.substring(slash);
    this.origin = this.protocol + "//" + this.host;
    this.searchParams = new globalThis.URLSearchParams(this.search);
  };
  globalThis.URL.prototype.toString = function () { return this.href; };

  globalThis.require = function (name) {
    if (Object.prototype.hasOwnProperty.call(modules, name)) return modules[name];
    if (name === "axios") return axiosShim;
    throw new Error("وحدة غير مدعومة في المشغل: " + name);
  };

  globalThis.TextEncoder = function () {};
  globalThis.TextEncoder.prototype.encode = function (input) {
    var text = String(input);
    var encoded = "";
    try {
      encoded = unescape(encodeURIComponent(text));
    } catch (error) {
      encoded = text;
    }
    var bytes = new Uint8Array(encoded.length);
    for (var i = 0; i < encoded.length; i++) {
      bytes[i] = encoded.charCodeAt(i);
    }
    return bytes;
  };

  globalThis.TextDecoder = function () {};
  globalThis.TextDecoder.prototype.decode = function (input) {
    if (!input) return "";
    var encoded = "";
    for (var i = 0; i < input.length; i++) {
      encoded += String.fromCharCode(input[i] & 255);
    }
    try {
      return decodeURIComponent(escape(encoded));
    } catch (error) {
      return encoded;
    }
  };
})();
"""


/** Reads the cheerio/crypto-js shim that ships beside the app binary. */
@OptIn(ExperimentalForeignApi::class)
private fun readBundledCompatScript(): String? {
    val path = NSBundle.mainBundle.pathForResource("nuvio-compat", ofType = "js") ?: return null
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val length = data.length.toInt()
    if (length <= 0) return null
    val raw = data.bytes ?: return null
    return raw.reinterpret<ByteVar>().readBytes(length).decodeToString()
}
