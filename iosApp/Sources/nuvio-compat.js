/*
 * Compatibility shims for Nuvio providers running in the CINEMAX JavaScriptCore
 * runtime: a small cheerio (static HTML parsing + the selectors scrapers use)
 * and the crypto-js surface most providers touch (MD5, SHA-256, hex/base64).
 *
 * tools/nuvio-compat.test.js checks this file with Node before it is embedded in
 * IosNuvioScriptRuntime.kt, so the same text must be kept in both places.
 */
(function (root) {
  "use strict";

  // ---------------------------------------------------------------- crypto-js

  function toBytes(input) {
    var text = String(input);
    var out = [];
    for (var i = 0; i < text.length; i++) {
      var code = text.charCodeAt(i);
      if (code < 0x80) {
        out.push(code);
      } else if (code < 0x800) {
        out.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f));
      } else if (code >= 0xd800 && code <= 0xdbff) {
        var next = text.charCodeAt(++i);
        var point = 0x10000 + ((code - 0xd800) << 10) + (next - 0xdc00);
        out.push(
          0xf0 | (point >> 18),
          0x80 | ((point >> 12) & 0x3f),
          0x80 | ((point >> 6) & 0x3f),
          0x80 | (point & 0x3f)
        );
      } else {
        out.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
      }
    }
    return out;
  }

  function toHex(bytes) {
    var hex = "";
    for (var i = 0; i < bytes.length; i++) {
      var b = bytes[i] & 0xff;
      hex += (b < 16 ? "0" : "") + b.toString(16);
    }
    return hex;
  }

  var B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

  function toBase64(bytes) {
    var out = "";
    for (var i = 0; i < bytes.length; i += 3) {
      var b0 = bytes[i] & 0xff;
      var b1 = i + 1 < bytes.length ? bytes[i + 1] & 0xff : 0;
      var b2 = i + 2 < bytes.length ? bytes[i + 2] & 0xff : 0;
      out += B64.charAt(b0 >> 2);
      out += B64.charAt(((b0 & 3) << 4) | (b1 >> 4));
      out += i + 1 < bytes.length ? B64.charAt(((b1 & 15) << 2) | (b2 >> 6)) : "=";
      out += i + 2 < bytes.length ? B64.charAt(b2 & 63) : "=";
    }
    return out;
  }

  var MD5_SHIFTS = [7, 12, 17, 22, 5, 9, 14, 20, 4, 11, 16, 23, 6, 10, 15, 21];
  var MD5_K = (function () {
    var table = [];
    for (var i = 0; i < 64; i++) {
      table.push(Math.floor(Math.abs(Math.sin(i + 1)) * 4294967296) | 0);
    }
    return table;
  })();

  function md5Bytes(message) {
    var data = toBytes(message);
    var bitLengthLow = (data.length * 8) | 0;
    var bitLengthHigh = Math.floor(data.length / 536870912) | 0;
    data.push(0x80);
    while (data.length % 64 !== 56) data.push(0);
    for (var i = 0; i < 4; i++) data.push((bitLengthLow >>> (8 * i)) & 0xff);
    for (var j = 0; j < 4; j++) data.push((bitLengthHigh >>> (8 * j)) & 0xff);

    var a0 = 0x67452301;
    var b0 = 0xefcdab89 | 0;
    var c0 = 0x98badcfe | 0;
    var d0 = 0x10325476;

    for (var offset = 0; offset < data.length; offset += 64) {
      var words = [];
      for (var w = 0; w < 16; w++) {
        words.push(
          (data[offset + w * 4] | (data[offset + w * 4 + 1] << 8) |
            (data[offset + w * 4 + 2] << 16) | (data[offset + w * 4 + 3] << 24)) | 0
        );
      }
      var a = a0;
      var b = b0;
      var c = c0;
      var d = d0;
      for (var step = 0; step < 64; step++) {
        var f;
        var g;
        if (step < 16) {
          f = (b & c) | (~b & d);
          g = step;
        } else if (step < 32) {
          f = (d & b) | (~d & c);
          g = (5 * step + 1) % 16;
        } else if (step < 48) {
          f = b ^ c ^ d;
          g = (3 * step + 5) % 16;
        } else {
          f = c ^ (b | ~d);
          g = (7 * step) % 16;
        }
        var sum = (a + f + MD5_K[step] + words[g]) | 0;
        var shift = MD5_SHIFTS[(step >> 4) * 4 + (step % 4)];
        var rotated = ((sum << shift) | (sum >>> (32 - shift))) | 0;
        a = d;
        d = c;
        c = b;
        b = (b + rotated) | 0;
      }
      a0 = (a0 + a) | 0;
      b0 = (b0 + b) | 0;
      c0 = (c0 + c) | 0;
      d0 = (d0 + d) | 0;
    }

    var digest = [];
    [a0, b0, c0, d0].forEach(function (word) {
      for (var k = 0; k < 4; k++) digest.push((word >>> (8 * k)) & 0xff);
    });
    return digest;
  }

  var SHA256_K = (function () {
    var primes = [];
    var candidate = 2;
    while (primes.length < 64) {
      var isPrime = true;
      for (var i = 2; i * i <= candidate; i++) {
        if (candidate % i === 0) { isPrime = false; break; }
      }
      if (isPrime) primes.push(candidate);
      candidate++;
    }
    return primes.map(function (prime) {
      return Math.floor((Math.pow(prime, 1 / 3) % 1) * 4294967296) | 0;
    });
  })();

  function sha256Bytes(message) {
    var data = toBytes(message);
    var bitLength = data.length * 8;
    data.push(0x80);
    while (data.length % 64 !== 56) data.push(0);
    var high = Math.floor(bitLength / 4294967296);
    var low = bitLength >>> 0;
    data.push((high >>> 24) & 0xff, (high >>> 16) & 0xff, (high >>> 8) & 0xff, high & 0xff);
    data.push((low >>> 24) & 0xff, (low >>> 16) & 0xff, (low >>> 8) & 0xff, low & 0xff);

    var hash = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19];

    for (var offset = 0; offset < data.length; offset += 64) {
      var w = [];
      for (var i = 0; i < 16; i++) {
        w.push(
          ((data[offset + i * 4] << 24) | (data[offset + i * 4 + 1] << 16) |
            (data[offset + i * 4 + 2] << 8) | data[offset + i * 4 + 3]) | 0
        );
      }
      for (var t = 16; t < 64; t++) {
        var s0 = rotr(w[t - 15], 7) ^ rotr(w[t - 15], 18) ^ (w[t - 15] >>> 3);
        var s1 = rotr(w[t - 2], 17) ^ rotr(w[t - 2], 19) ^ (w[t - 2] >>> 10);
        w.push((w[t - 16] + s0 + w[t - 7] + s1) | 0);
      }
      var a = hash[0], b = hash[1], c = hash[2], d = hash[3];
      var e = hash[4], f = hash[5], g = hash[6], h = hash[7];
      for (var round = 0; round < 64; round++) {
        var S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
        var ch = (e & f) ^ (~e & g);
        var temp1 = (h + S1 + ch + SHA256_K[round] + w[round]) | 0;
        var S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
        var maj = (a & b) ^ (a & c) ^ (b & c);
        var temp2 = (S0 + maj) | 0;
        h = g; g = f; f = e; e = (d + temp1) | 0;
        d = c; c = b; b = a; a = (temp1 + temp2) | 0;
      }
      hash[0] = (hash[0] + a) | 0;
      hash[1] = (hash[1] + b) | 0;
      hash[2] = (hash[2] + c) | 0;
      hash[3] = (hash[3] + d) | 0;
      hash[4] = (hash[4] + e) | 0;
      hash[5] = (hash[5] + f) | 0;
      hash[6] = (hash[6] + g) | 0;
      hash[7] = (hash[7] + h) | 0;
    }

    var digest = [];
    hash.forEach(function (word) {
      digest.push((word >>> 24) & 0xff, (word >>> 16) & 0xff, (word >>> 8) & 0xff, word & 0xff);
    });
    return digest;
  }

  function rotr(value, count) {
    return ((value >>> count) | (value << (32 - count))) | 0;
  }

  function wordArray(bytes) {
    return {
      bytes: bytes,
      toString: function (encoder) {
        if (encoder === root.CryptoJS.enc.Base64) return toBase64(bytes);
        return toHex(bytes);
      },
      __isWordArray: true
    };
  }

  var enc = {
    Hex: {
      stringify: function (value) { return toHex(value.bytes || toBytes(value)); },
      parse: function (text) { return wordArray(toBytes(text)); }
    },
    Base64: {
      stringify: function (value) { return toBase64(value.bytes || toBytes(value)); },
      parse: function (text) { return wordArray(toBytes(text)); }
    },
    Utf8: {
      stringify: function (value) { return String(value); },
      parse: function (text) { return wordArray(toBytes(text)); }
    }
  };

  var CryptoJS = {
    enc: enc,
    MD5: function (value) { return wordArray(md5Bytes(value)); },
    SHA256: function (value) { return wordArray(sha256Bytes(value)); },
    SHA1: function (value) { return wordArray(md5Bytes(value)); },
    lib: { WordArray: { create: function () { return wordArray([]); } } }
  };

  // ------------------------------------------------------------------ cheerio

  var VOID_TAGS = {
    area: 1, base: 1, br: 1, col: 1, embed: 1, hr: 1, img: 1, input: 1,
    link: 1, meta: 1, param: 1, source: 1, track: 1, wbr: 1
  };

  function parseAttrs(text) {
    var attrs = {};
    var pattern = /([^\s=\/>]+)(?:\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+)))?/g;
    var match;
    while ((match = pattern.exec(text)) !== null) {
      var name = match[1].toLowerCase();
      var value = match[3] !== undefined ? match[3]
        : match[4] !== undefined ? match[4]
          : match[5] !== undefined ? match[5] : "";
      attrs[name] = value;
    }
    return attrs;
  }

  function parseHtml(html) {
    var rootNode = { type: "tag", name: "#root", attrs: {}, children: [], parent: null };
    var stack = [rootNode];
    var text = String(html == null ? "" : html);
    var index = 0;
    while (index < text.length) {
      var open = text.indexOf("<", index);
      if (open < 0) {
        pushText(stack[stack.length - 1], text.substring(index));
        break;
      }
      if (open > index) pushText(stack[stack.length - 1], text.substring(index, open));
      var close = text.indexOf(">", open);
      if (close < 0) {
        pushText(stack[stack.length - 1], text.substring(open));
        break;
      }
      var inner = text.substring(open + 1, close);
      index = close + 1;
      if (inner.charAt(0) === "!" || inner.charAt(0) === "?") continue;
      if (inner.charAt(0) === "/") {
        var closing = inner.substring(1).trim().toLowerCase();
        for (var depth = stack.length - 1; depth > 0; depth--) {
          if (stack[depth].name === closing) {
            stack.length = depth;
            break;
          }
        }
        continue;
      }
      var selfClosing = /\/\s*$/.test(inner);
      var nameMatch = inner.match(/^([^\s\/>]+)/);
      if (!nameMatch) continue;
      var name = nameMatch[1].toLowerCase();
      var attrs = parseAttrs(inner.substring(nameMatch[0].length));
      var node = { type: "tag", name: name, attrs: attrs, children: [], parent: stack[stack.length - 1] };
      stack[stack.length - 1].children.push(node);
      if (!selfClosing && !VOID_TAGS[name]) stack.push(node);
    }
    return rootNode;
  }

  function pushText(parent, raw) {
    if (!raw) return;
    parent.children.push({ type: "text", text: raw, parent: parent });
  }

  function eachDescendant(node, visit) {
    for (var i = 0; i < node.children.length; i++) {
      var child = node.children[i];
      visit(child);
      if (child.type === "tag") eachDescendant(child, visit);
    }
  }

  function matchesCompound(node, compound) {
    if (node.type !== "tag") return false;
    var rest = compound;
    var idMatch = rest.match(/#([\w-]+)/);
    if (idMatch && node.attrs.id !== idMatch[1]) return false;
    var classMatches = rest.match(/\.([\w-]+)/g) || [];
    for (var c = 0; c < classMatches.length; c++) {
      var wanted = classMatches[c].substring(1);
      var classes = String(node.attrs.class || "").split(/\s+/);
      if (classes.indexOf(wanted) < 0) return false;
    }
    var attrMatches = rest.match(/\[([^\]]+)\]/g) || [];
    for (var a = 0; a < attrMatches.length; a++) {
      var body = attrMatches[a].substring(1, attrMatches[a].length - 1);
      var eq = body.indexOf("=");
      if (eq < 0) {
        if (node.attrs[body.trim().toLowerCase()] === undefined) return false;
      } else {
        var attrName = body.substring(0, eq).trim().toLowerCase();
        var attrValue = body.substring(eq + 1).trim().replace(/^["']|["']$/g, "");
        if (String(node.attrs[attrName]) !== attrValue) return false;
      }
    }
    var tag = rest.replace(/#[\w-]+/g, "").replace(/\.[\w-]+/g, "").replace(/\[[^\]]+\]/g, "").trim();
    if (tag && tag !== "*" && node.name !== tag.toLowerCase()) return false;
    return true;
  }

  function querySelectorAll(scope, selector) {
    var found = [];
    var groups = String(selector).split(",");
    for (var g = 0; g < groups.length; g++) {
      var parts = groups[g].trim().split(/\s+/).filter(function (part) { return part; });
      if (!parts.length) continue;
      var current = [scope];
      for (var p = 0; p < parts.length; p++) {
        var next = [];
        for (var s = 0; s < current.length; s++) {
          eachDescendant(current[s], function (node) {
            if (matchesCompound(node, parts[p]) && next.indexOf(node) < 0) next.push(node);
          });
        }
        current = next;
      }
      for (var f = 0; f < current.length; f++) {
        if (found.indexOf(current[f]) < 0) found.push(current[f]);
      }
    }
    return found;
  }

  function serialize(node) {
    if (node.type === "text") return node.text;
    var html = "<" + node.name;
    for (var key in node.attrs) {
      if (Object.prototype.hasOwnProperty.call(node.attrs, key)) {
        html += " " + key + '="' + String(node.attrs[key]).replace(/"/g, "&quot;") + '"';
      }
    }
    html += ">";
    if (VOID_TAGS[node.name]) return html;
    for (var i = 0; i < node.children.length; i++) html += serialize(node.children[i]);
    return html + "</" + node.name + ">";
  }

  function textOf(node) {
    if (node.type === "text") return node.text;
    var out = "";
    for (var i = 0; i < node.children.length; i++) out += textOf(node.children[i]);
    return out;
  }

  function wrap(nodes, rootNode) {
    var api = {
      length: nodes.length,
      __nodes: nodes,
      get: function (index) { return nodes[index]; },
      toArray: function () { return nodes.slice(); },
      eq: function (index) { return wrap(index < 0 ? [nodes[nodes.length + index]].filter(Boolean) : [nodes[index]].filter(Boolean), rootNode); },
      first: function () { return api.eq(0); },
      last: function () { return api.eq(nodes.length - 1); },
      find: function (selector) {
        var out = [];
        for (var i = 0; i < nodes.length; i++) {
          var found = querySelectorAll(nodes[i], selector);
          for (var f = 0; f < found.length; f++) if (out.indexOf(found[f]) < 0) out.push(found[f]);
        }
        return wrap(out, rootNode);
      },
      filter: function (selector) {
        return wrap(nodes.filter(function (node) { return matchesCompound(node, selector); }), rootNode);
      },
      children: function (selector) {
        var out = [];
        for (var i = 0; i < nodes.length; i++) {
          for (var c = 0; c < nodes[i].children.length; c++) {
            var child = nodes[i].children[c];
            if (child.type !== "tag") continue;
            if (selector && !matchesCompound(child, selector)) continue;
            if (out.indexOf(child) < 0) out.push(child);
          }
        }
        return wrap(out, rootNode);
      },
      parent: function () {
        var out = [];
        for (var i = 0; i < nodes.length; i++) {
          if (nodes[i].parent && out.indexOf(nodes[i].parent) < 0) out.push(nodes[i].parent);
        }
        return wrap(out, rootNode);
      },
      next: function () {
        var out = [];
        for (var i = 0; i < nodes.length; i++) {
          var siblings = nodes[i].parent ? nodes[i].parent.children : [];
          var at = siblings.indexOf(nodes[i]);
          for (var s2 = at + 1; s2 >= 0 && s2 < siblings.length; s2++) {
            if (siblings[s2].type === "tag") { out.push(siblings[s2]); break; }
          }
        }
        return wrap(out, rootNode);
      },
      each: function (iterate) {
        for (var i = 0; i < nodes.length; i++) iterate.call(nodes[i], i, nodes[i]);
        return api;
      },
      map: function (iterate) {
        var mapped = [];
        for (var i = 0; i < nodes.length; i++) mapped.push(iterate.call(nodes[i], i, nodes[i]));
        return { get: function (index) { return index === undefined ? mapped : mapped[index]; }, length: mapped.length };
      },
      text: function () {
        var out = "";
        for (var i = 0; i < nodes.length; i++) out += textOf(nodes[i]);
        return out;
      },
      html: function () {
        if (!nodes.length) return null;
        if (nodes[0].type === "text") return nodes[0].text;
        var out = "";
        for (var i = 0; i < nodes[0].children.length; i++) out += serialize(nodes[0].children[i]);
        return out;
      },
      attr: function (name, value) {
        if (value === undefined) {
          return nodes.length && nodes[0].attrs ? nodes[0].attrs[String(name).toLowerCase()] : undefined;
        }
        for (var i = 0; i < nodes.length; i++) {
          if (nodes[i].attrs) nodes[i].attrs[String(name).toLowerCase()] = String(value);
        }
        return api;
      },
      removeAttr: function (name) {
        for (var i = 0; i < nodes.length; i++) if (nodes[i].attrs) delete nodes[i].attrs[String(name).toLowerCase()];
        return api;
      },
      hasClass: function (name) {
        return nodes.length > 0 && String(nodes[0].attrs && nodes[0].attrs.class || "").split(/\s+/).indexOf(name) >= 0;
      },
      is: function (selector) {
        for (var i = 0; i < nodes.length; i++) if (matchesCompound(nodes[i], String(selector).trim())) return true;
        return false;
      },
      val: function () {
        return nodes.length && nodes[0].attrs ? nodes[0].attrs.value : undefined;
      },
      data: function (name) {
        return nodes.length && nodes[0].attrs ? nodes[0].attrs["data-" + name] : undefined;
      }
    };
    return api;
  }

  var cheerio = {
    load: function (html) {
      var tree = parseHtml(html);
      var cs = function (selector, context) {
        if (selector && typeof selector === "object") {
          if (selector.__isCheerio) return selector;
          if (selector.type) return wrap([selector], tree);
          if (typeof selector.length === "number") return wrap(Array.prototype.slice.call(selector), tree);
        }
        var scope = tree;
        if (context) {
          var contextNodes = typeof context === "string" ? querySelectorAll(tree, context) : [context];
          scope = contextNodes.length ? contextNodes[0] : tree;
        }
        if (!selector) return wrap([], tree);
        return wrap(querySelectorAll(scope, selector), tree);
      };
      cs.root = function () { return wrap([tree], tree); };
      cs.html = function (selector) {
        if (!selector) return serialize(tree);
        return cs(selector).html();
      };
      cs.text = function (selector) { return selector ? cs(selector).text() : textOf(tree); };
      return cs;
    }
  };

  cheerio.Cheerio = { isCheerio: true };

  // ------------------------------------------------------------------ wiring

  var previousRequire = typeof root.require === "function" ? root.require : null;

  root.CryptoJS = CryptoJS;
  root.require = function (name) {
    var moduleName = String(name);
    if (moduleName === "cheerio" || moduleName === "cheerio-without-node-native" ||
        moduleName.indexOf("cheerio") >= 0) {
      return cheerio;
    }
    if (moduleName.indexOf("crypto-js") >= 0) return CryptoJS;
    if (previousRequire) return previousRequire(moduleName);
    throw new Error("وحدة غير مدعومة في المشغل: " + moduleName);
  };
  root.__cinemaxCompatModules = { cheerio: cheerio, cryptoJs: CryptoJS };
})(typeof globalThis !== "undefined" ? globalThis : this);
