package com.nuvio.app.features.addons

import com.nuvio.app.core.storage.SecretStore
import com.nuvio.app.features.profiles.MAX_PROFILES
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class StoredAddonConfigSecrets(
    val manifestUrl: String,
    val queryPairs: List<String> = emptyList(),
    /** The manifest path before its final `/manifest.json`; null when it carried no path secret. */
    val secretPath: String? = null,
)

@Serializable
private data class StoredAddonConfigSecretIndex(val manifestUrls: List<String> = emptyList())

internal data class SplitAddonConfigUrl(
    val safeManifestUrl: String,
    val secretQueryPairs: List<String>,
    val secretPath: String? = null,
)

/** A typed failure so UI code never has to inspect or display an exception's raw message. */
internal class AddonSecretStorageUnavailableException : IllegalStateException()

/**
 * Keeps query credentials and opaque path configuration in the platform's protected secret store.
 * The Stremio protocol still sends them to the add-on provider; local preferences, exports and
 * account sync keep the secret values out.
 */
internal object AddonConfigSecretStore {
    private const val SECRET_INDEX_NAME = "addon_config_secret_index"
    private val json = Json { ignoreUnknownKeys = true }

    fun protectConfiguredManifestUrl(
        profileId: Int,
        manifestUrl: String,
        fields: List<AddonConfigField>,
    ): String {
        val split = splitSecretAddonConfigUrl(manifestUrl, fields)
        if (split.secretQueryPairs.isNotEmpty() || split.secretPath != null) {
            require(isHttpsAddonUrl(split.safeManifestUrl)) {
                "Secret add-on settings require an HTTPS connection."
            }
            check(
                storeSecretQueryPairs(
                    profileId = profileId,
                    safeManifestUrl = split.safeManifestUrl,
                    queryPairs = split.secretQueryPairs,
                    secretPath = split.secretPath,
                ),
            ) {
                "Secure storage is unavailable for this add-on configuration."
            }
        }
        return split.safeManifestUrl
    }

    fun storeSecretQueryPairs(
        profileId: Int,
        safeManifestUrl: String,
        queryPairs: List<String>,
        secretPath: String? = null,
    ): Boolean {
        if (queryPairs.isEmpty() && secretPath == null) return true
        if (!isHttpsAddonUrl(safeManifestUrl) || !isSafePublicWebUrl(safeManifestUrl)) return false
        if (secretPath?.let { path -> path.isEmpty() || path.any { it.isISOControl() || it == '\\' } } == true) return false

        val previous = read(profileId, safeManifestUrl)
        val pairsByName = linkedMapOf<String, String>()
        previous?.queryPairs.orEmpty().forEach { pair -> pairsByName[normalizedKey(queryKey(pair))] = pair }
        queryPairs.forEach { pair -> pairsByName[normalizedKey(queryKey(pair))] = pair }
        val encoded = json.encodeToString(
            StoredAddonConfigSecrets(
                manifestUrl = safeManifestUrl,
                queryPairs = pairsByName.values.toList(),
                secretPath = secretPath ?: previous?.secretPath,
            ),
        )
        val key = storageKey(profileId, safeManifestUrl)
        if (!SecretStore.writeChecked(key, encoded)) return false
        // Keep the cleanup index even when read-back verification is temporarily unavailable.
        addToIndex(profileId, safeManifestUrl)
        return SecretStore.read(key) == encoded
    }

    fun requestManifestUrl(profileId: Int, manifestUrl: String): String =
        appendStoredSecrets(profileId, manifestUrl)

    fun storedSecretQueryPairs(profileId: Int, manifestUrl: String): List<String> =
        read(profileId, manifestUrl)?.queryPairs.orEmpty()

    fun appendStoredSecrets(profileId: Int, url: String): String {
        val manifestUrl = manifestUrlForResourceUrl(url) ?: return url
        val stored = read(profileId, manifestUrl) ?: return url
        val restoredPathUrl = stored.secretPath?.let { secretPath ->
            restoreSecretPath(url, manifestUrl, secretPath)
        } ?: url
        val explicitKeys = restoredPathUrl.substringAfter('?', "")
            .substringBefore('#')
            .split('&')
            .filter(String::isNotBlank)
            .mapTo(mutableSetOf()) { pair -> normalizedKey(queryKey(pair)) }
        val additionalPairs = stored.queryPairs.filterNot { pair ->
            normalizedKey(queryKey(pair)) in explicitKeys
        }
        return appendAddonSecretQueryPairs(restoredPathUrl, additionalPairs)
    }

    fun remove(profileId: Int, manifestUrl: String) {
        if (SecretStore.writeChecked(storageKey(profileId, manifestUrl), null)) {
            removeFromIndex(profileId, manifestUrl)
        }
    }

    /** Deletes add-on secrets before account storage is wiped, while its non-secret index remains. */
    fun clearAllProfiles() {
        (1..MAX_PROFILES).forEach { profileId ->
            val indexed = loadIndex(profileId)
            val installed = AddonStorage.loadInstalledAddonUrls(profileId).mapNotNull { url ->
                runCatching { safeLocalManifestUrl(url) }.getOrNull()
            }
            var allSecretsRemoved = true
            (indexed + installed).distinct().forEach { url ->
                if (!SecretStore.writeChecked(storageKey(profileId, url), null)) {
                    allSecretsRemoved = false
                }
            }
            if (allSecretsRemoved) AddonStorage.saveStartupCache(profileId, SECRET_INDEX_NAME, "")
        }
    }

    /** A local persistence key with path/query credentials removed from its visible URL. */
    fun safeLocalManifestUrl(manifestUrl: String, fields: List<AddonConfigField> = emptyList()): String =
        splitSecretAddonConfigUrl(normalizeAddonManifestUrl(manifestUrl), fields).safeManifestUrl

    /** Sync and export never receive opaque path tokens or arbitrary query values. */
    fun safeRemoteManifestUrl(
        manifestUrl: String,
        fields: List<AddonConfigField> = emptyList(),
    ): String? {
        val normalized = runCatching { normalizeAddonManifestUrl(manifestUrl) }.getOrNull() ?: return null
        if (hasSensitiveAddonPath(normalized)) return null

        val split = splitSecretAddonConfigUrl(normalized, fields)
        val query = split.safeManifestUrl.substringAfter('?', "")
        if (query.isBlank()) return split.safeManifestUrl

        val allowedKeys = fields.asSequence()
            .filterNot(AddonConfigField::isSecret)
            .mapTo(mutableSetOf()) { field -> normalizedKey(field.key) }
        if (allowedKeys.isEmpty() && !isBuiltInSource(normalized)) {
            return split.safeManifestUrl.substringBefore('?')
        }
        val allowedPairs = query.split('&').filter { pair ->
            val key = normalizedKey(queryKey(pair))
            key in allowedKeys || (allowedKeys.isEmpty() && !isSecretConfigKey(queryKey(pair)))
        }
        val path = split.safeManifestUrl.substringBefore('?')
        return if (allowedPairs.isEmpty()) path else "$path?${allowedPairs.joinToString("&")}"
    }

    fun safeExportManifestUrl(
        manifestUrl: String,
        fields: List<AddonConfigField> = emptyList(),
    ): String? = safeRemoteManifestUrl(manifestUrl, fields)

    fun canSyncManifestUrl(manifestUrl: String, manifest: AddonManifest?): Boolean =
        safeRemoteManifestUrl(manifestUrl, manifest?.configFields.orEmpty()) != null

    private fun read(profileId: Int, manifestUrl: String): StoredAddonConfigSecrets? {
        val raw = SecretStore.read(storageKey(profileId, manifestUrl)) ?: return null
        val record = runCatching { json.decodeFromString<StoredAddonConfigSecrets>(raw) }.getOrNull()
            ?: return null
        return record.takeIf { stored -> stored.manifestUrl == manifestUrl }
    }

    private fun storageKey(profileId: Int, manifestUrl: String): String =
        "addon_config_${profileId}_${manifestUrl.stableAddonKeyHash()}"

    private fun loadIndex(profileId: Int): List<String> =
        AddonStorage.loadStartupCache(profileId, SECRET_INDEX_NAME)
            ?.takeIf(String::isNotBlank)
            ?.let { raw -> runCatching { json.decodeFromString<StoredAddonConfigSecretIndex>(raw).manifestUrls }.getOrNull() }
            .orEmpty()

    private fun addToIndex(profileId: Int, manifestUrl: String) {
        val updated = (loadIndex(profileId) + manifestUrl).distinct().sorted()
        AddonStorage.saveStartupCache(
            profileId = profileId,
            name = SECRET_INDEX_NAME,
            payload = json.encodeToString(StoredAddonConfigSecretIndex(updated)),
        )
    }

    private fun removeFromIndex(profileId: Int, manifestUrl: String) {
        val updated = loadIndex(profileId) - manifestUrl
        AddonStorage.saveStartupCache(
            profileId = profileId,
            name = SECRET_INDEX_NAME,
            payload = json.encodeToString(StoredAddonConfigSecretIndex(updated)),
        )
    }
}

internal fun splitSecretAddonConfigUrl(
    manifestUrl: String,
    fields: List<AddonConfigField>,
): SplitAddonConfigUrl {
    val configuredSecretKeys = fields
        .filter(AddonConfigField::isSecret)
        .mapTo(mutableSetOf()) { field -> normalizedKey(field.key) }
    val query = manifestUrl.substringAfter('?', "").substringBefore('#')
    val retained = mutableListOf<String>()
    val secrets = mutableListOf<String>()
    query.split('&').filter(String::isNotBlank).forEach { pair ->
        val key = queryKey(pair)
        if (normalizedKey(key) in configuredSecretKeys || isSecretConfigKey(key)) {
            secrets += pair
        } else {
            retained += pair
        }
    }

    val withoutQuery = manifestUrl.substringBefore('?').substringBefore('#')
    val pathSplit = splitSensitiveManifestPath(withoutQuery)
    val safeUrl = if (retained.isEmpty()) pathSplit.safeManifestUrl else "${pathSplit.safeManifestUrl}?${retained.joinToString("&")}"
    return SplitAddonConfigUrl(
        safeManifestUrl = safeUrl,
        secretQueryPairs = secrets,
        secretPath = pathSplit.secretPath,
    )
}

internal fun appendAddonSecretQueryPairs(url: String, queryPairs: List<String>): String {
    if (queryPairs.isEmpty()) return url
    val secretKeys = queryPairs.mapTo(mutableSetOf()) { pair -> normalizedKey(queryKey(pair)) }
    val path = url.substringBefore('?').substringBefore('#')
    val retained = url.substringAfter('?', "")
        .substringBefore('#')
        .split('&')
        .filter(String::isNotBlank)
        .filterNot { pair -> normalizedKey(queryKey(pair)) in secretKeys }
    val merged = retained + queryPairs
    return if (merged.isEmpty()) path else "$path?${merged.joinToString("&")}"
}

private data class SensitivePathSplit(val safeManifestUrl: String, val secretPath: String?)

private fun splitSensitiveManifestPath(manifestUrl: String): SensitivePathSplit {
    val authorityStart = manifestUrl.indexOf("://")
    if (authorityStart < 0) return SensitivePathSplit(manifestUrl, null)
    val pathStart = manifestUrl.indexOf('/', authorityStart + 3)
    if (pathStart < 0) return SensitivePathSplit(manifestUrl, null)
    val path = manifestUrl.substring(pathStart)
    if (!hasSensitiveAddonPath(manifestUrl) || isProtectedAddonPath(manifestUrl)) {
        return SensitivePathSplit(manifestUrl, null)
    }
    val secretPath = path.removeSuffix("/manifest.json")
    if (secretPath.isEmpty() || secretPath == "/") return SensitivePathSplit(manifestUrl, null)
    val marker = "/__raif_secret_path_${secretPath.stableAddonKeyHash()}"
    val origin = manifestUrl.substring(0, pathStart)
    return SensitivePathSplit("$origin$marker/manifest.json", secretPath)
}

internal fun restoreSecretPath(url: String, manifestUrl: String, secretPath: String): String {
    val safeBase = manifestUrl.substringBefore('?').removeSuffix("/manifest.json")
    val urlPath = url.substringBefore('?').substringBefore('#')
    if (!urlPath.startsWith(safeBase)) return url
    val suffix = urlPath.removePrefix(safeBase)
    if (suffix.isNotEmpty() && !suffix.startsWith('/')) return url
    val origin = safeBase.substringBefore("://") + "://" + safeBase.substringAfter("://").substringBefore('/')
    return origin + secretPath + suffix + url.substringAfter('?', "").let { query ->
        query.substringBefore('#').takeIf(String::isNotBlank)?.let { "?$it" }.orEmpty()
    }
}

private fun isProtectedAddonPath(url: String): Boolean =
    url.substringAfter("://", "").substringBefore('?').substringBefore('#')
        .split('/')
        .any { segment -> segment.startsWith("__raif_secret_path_", ignoreCase = true) }

private fun manifestUrlForResourceUrl(url: String): String? {
    val parsed = runCatching { Url(url) }.getOrNull() ?: return null
    val path = parsed.encodedPath
    val manifestPath = if (path.endsWith("/manifest.json", ignoreCase = true)) {
        path
    } else {
        val markers = listOf("/stream/", "/catalog/", "/meta/", "/subtitles/", "/addon_catalog/")
            .mapNotNull { resource -> path.lastIndexOf(resource).takeIf { it >= 0 }?.let { it to resource } }
        val marker = markers.maxByOrNull { it.first } ?: return null
        "${path.substring(0, marker.first).trimEnd('/')}/manifest.json"
    }
    val authority = url.substringAfter("://", "").substringBeforeAny('/', '?', '#')
    val scheme = url.substringBefore("://")
    val query = url.substringAfter('?', "").substringBefore('#')
        .takeIf(String::isNotBlank)?.let { "?$it" }.orEmpty()
    return "$scheme://$authority$manifestPath$query"
}

private fun isHttpsAddonUrl(url: String): Boolean =
    runCatching { Url(url).protocol.name.equals("https", ignoreCase = true) }.getOrDefault(false)

private fun queryKey(pair: String): String = pair.substringBefore('=').let(::decodeQueryKey)

private fun decodeQueryKey(value: String): String {
    val output = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        if (value[index] == '%' && index + 2 < value.length) {
            val byte = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (byte != null && byte in 0x20..0x7E) {
                output.append(byte.toChar())
                index += 3
                continue
            }
        }
        output.append(if (value[index] == '+') ' ' else value[index])
        index++
    }
    return output.toString()
}

private fun normalizedKey(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)

private fun String.substringBeforeAny(vararg delimiters: Char): String {
    val end = indexOfAny(delimiters).takeIf { it >= 0 } ?: length
    return substring(0, end)
}

private fun String.stableAddonKeyHash(): String {
    var hash = -3750763034362895579L
    encodeToByteArray().forEach { byte -> hash = (hash xor (byte.toLong() and 0xFF)) * 1099511628211L }
    return hash.toString(16)
}
