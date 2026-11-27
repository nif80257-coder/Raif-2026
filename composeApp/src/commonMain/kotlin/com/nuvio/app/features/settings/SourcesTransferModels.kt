package com.nuvio.app.features.settings

import com.nuvio.app.features.addons.isSafeWebUrl
import com.nuvio.app.features.addons.normalizeAddonManifestUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val SOURCES_TRANSFER_VERSION = 1
internal const val SOURCE_TYPE_ADDON = "addon"
internal const val SOURCE_TYPE_SERVER = "server"

@Serializable
internal data class SourcesTransferEntry(
    val type: String,
    val name: String = "",
    val url: String,
    val enabled: Boolean = true,
    val order: Int = 0,
)

@Serializable
internal data class SourcesTransferDocument(
    val version: Int = SOURCES_TRANSFER_VERSION,
    @SerialName("exported_at") val exportedAt: String? = null,
    val sources: List<SourcesTransferEntry> = emptyList(),
)

internal enum class SourcesTransferParseFailure {
    EMPTY,
    MALFORMED,
    UNSUPPORTED_VERSION,
}

internal sealed interface SourcesTransferParseResult {
    data class Success(val document: SourcesTransferDocument) : SourcesTransferParseResult
    data class Failure(val reason: SourcesTransferParseFailure) : SourcesTransferParseResult
}

internal object SourcesTransferCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(document: SourcesTransferDocument): String = json.encodeToString(document)

    fun decode(payload: String): SourcesTransferParseResult {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) {
            return SourcesTransferParseResult.Failure(SourcesTransferParseFailure.EMPTY)
        }
        val document = runCatching { json.decodeFromString<SourcesTransferDocument>(trimmed) }
            .getOrNull()
            ?: return SourcesTransferParseResult.Failure(SourcesTransferParseFailure.MALFORMED)
        if (document.version != SOURCES_TRANSFER_VERSION) {
            return SourcesTransferParseResult.Failure(SourcesTransferParseFailure.UNSUPPORTED_VERSION)
        }
        return SourcesTransferParseResult.Success(document)
    }
}

internal enum class SourcesTransferInvalidReason {
    MISSING_URL,
    INVALID_URL,
    UNSUPPORTED_TYPE,
}

internal data class SourcesTransferInvalidEntry(
    val name: String,
    val url: String,
    val reason: SourcesTransferInvalidReason,
)

internal data class SourcesTransferDuplicateEntry(
    val name: String,
    val url: String,
)

internal data class SourcesTransferAnalysis(
    val addons: List<SourcesTransferEntry> = emptyList(),
    val servers: List<SourcesTransferEntry> = emptyList(),
    val duplicates: List<SourcesTransferDuplicateEntry> = emptyList(),
    val invalid: List<SourcesTransferInvalidEntry> = emptyList(),
) {
    val importableCount: Int get() = addons.size
    val incompatibleCount: Int get() = servers.size
    val duplicateCount: Int get() = duplicates.size
    val invalidCount: Int get() = invalid.size
    val totalCount: Int get() = importableCount + incompatibleCount + duplicateCount + invalidCount
}

internal fun analyzeSourcesTransfer(
    document: SourcesTransferDocument,
    installedAddonUrls: Collection<String> = emptyList(),
    connectedServerAddresses: Collection<String> = emptyList(),
): SourcesTransferAnalysis {
    val installed = installedAddonUrls.mapTo(mutableSetOf(), ::normalizeSourceUrl)
    val connected = connectedServerAddresses.mapTo(mutableSetOf(), ::normalizeSourceUrl)
    val seenAddonUrls = mutableSetOf<String>()
    val addons = mutableListOf<SourcesTransferEntry>()
    val servers = mutableListOf<SourcesTransferEntry>()
    val duplicates = mutableListOf<SourcesTransferDuplicateEntry>()
    val invalid = mutableListOf<SourcesTransferInvalidEntry>()

    document.sources
        .sortedBy { entry -> entry.order }
        .forEach { entry ->
            val url = entry.url.trim()
            val name = entry.name.ifBlank { url }
            val type = entry.type.trim().lowercase()
            when {
                url.isEmpty() -> invalid += SourcesTransferInvalidEntry(
                    name = name,
                    url = url,
                    reason = SourcesTransferInvalidReason.MISSING_URL,
                )

                !isSupportedSourceUrl(url) -> invalid += SourcesTransferInvalidEntry(
                    name = name,
                    url = url,
                    reason = SourcesTransferInvalidReason.INVALID_URL,
                )

                type == SOURCE_TYPE_ADDON -> {
                    val normalizedUrl = runCatching { normalizeAddonManifestUrl(url) }.getOrNull()
                    if (normalizedUrl == null) {
                        invalid += SourcesTransferInvalidEntry(
                            name = name,
                            url = url,
                            reason = SourcesTransferInvalidReason.INVALID_URL,
                        )
                        return@forEach
                    }
                    val key = normalizeSourceUrl(normalizedUrl)
                    if (!seenAddonUrls.add(key) || key in installed) {
                        duplicates += SourcesTransferDuplicateEntry(name = name, url = url)
                    } else {
                        addons += entry.copy(url = normalizedUrl, name = name)
                    }
                }

                type == SOURCE_TYPE_SERVER -> {
                    if (normalizeSourceUrl(url) in connected) {
                        duplicates += SourcesTransferDuplicateEntry(name = name, url = url)
                    } else {
                        servers += entry.copy(url = url, name = name)
                    }
                }

                else -> invalid += SourcesTransferInvalidEntry(
                    name = name,
                    url = url,
                    reason = SourcesTransferInvalidReason.UNSUPPORTED_TYPE,
                )
            }
        }

    return SourcesTransferAnalysis(
        addons = addons,
        servers = servers,
        duplicates = duplicates,
        invalid = invalid,
    )
}

internal fun normalizeSourceUrl(url: String): String =
    url.trim().trimEnd('/').lowercase()

internal fun isSupportedSourceUrl(url: String): Boolean = isSafeWebUrl(url)

internal data class SourcesCredentialFinding(
    val sourceName: String,
    val field: String,
    val detail: String,
)

internal data class SourcesCredentialScan(
    val findings: List<SourcesCredentialFinding> = emptyList(),
) {
    val hasFindings: Boolean get() = findings.isNotEmpty()
}

private val sensitiveQueryNames = setOf(
    "token",
    "access_token",
    "refresh_token",
    "id_token",
    "api_key",
    "apikey",
    "key",
    "password",
    "pass",
    "pwd",
    "secret",
    "client_secret",
    "sig",
    "signature",
    "auth",
    "authorization",
    "credential",
    "credentials",
    "session",
)

internal fun scanSourcesForCredentials(
    entries: Collection<SourcesTransferEntry>,
): SourcesCredentialScan {
    val findings = mutableListOf<SourcesCredentialFinding>()

    entries.forEach { entry ->
        val url = entry.url.trim()
        val name = entry.name.ifBlank { url }
        if (url.isEmpty()) return@forEach

        val schemeIndex = url.indexOf("://")
        if (schemeIndex >= 0) {
            val authorityStart = schemeIndex + 3
            val authorityEnd = url.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
                .let { index -> if (index < 0) url.length else index }
            if (authorityEnd > authorityStart) {
                val authority = url.substring(authorityStart, authorityEnd)
                if (authority.contains('@')) {
                    findings += SourcesCredentialFinding(
                        sourceName = name,
                        field = "userinfo",
                        detail = url.substring(0, authorityEnd),
                    )
                }
            }
        }

        queryParams(url).forEach { (paramName, value) ->
            if (paramName.lowercase() in sensitiveQueryNames) {
                findings += SourcesCredentialFinding(
                    sourceName = name,
                    field = paramName,
                    detail = value,
                )
            }
        }
    }

    return SourcesCredentialScan(
        findings = findings
            .distinctBy { finding -> finding.sourceName + "|" + finding.field + "|" + finding.detail },
    )
}

internal fun redactSourcesTransferEntry(entry: SourcesTransferEntry): SourcesTransferEntry =
    entry.copy(url = redactSourceUrl(entry.url))

internal fun redactSourceUrl(url: String): String {
    val trimmed = url.trim()
    val schemeIndex = trimmed.indexOf("://")
    if (schemeIndex < 0) return trimmed

    val scheme = trimmed.substring(0, schemeIndex + 3)
    val authorityStart = schemeIndex + 3
    val authorityEnd = trimmed.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
        .let { index -> if (index < 0) trimmed.length else index }
    val authority = trimmed.substring(authorityStart, authorityEnd)
    val host = authority.substringAfterLast('@')
    val rest = trimmed.substring(authorityEnd)
    val queryIndex = rest.indexOf('?')
    val path = if (queryIndex < 0) rest else rest.substring(0, queryIndex)
    val queryAndFragment = if (queryIndex < 0) "" else rest.substring(queryIndex + 1)
    val fragmentIndex = queryAndFragment.indexOf('#')
    val query = if (fragmentIndex < 0) queryAndFragment else queryAndFragment.substring(0, fragmentIndex)
    val fragment = if (fragmentIndex < 0) "" else queryAndFragment.substring(fragmentIndex)
    val keptParams = query
        .split('&')
        .filter { pair -> pair.isNotBlank() && pair.substringBefore('=').lowercase() !in sensitiveQueryNames }
    val queryPart = if (keptParams.isEmpty()) "" else "?" + keptParams.joinToString("&")

    return scheme + host + path + queryPart + fragment
}

private fun queryParams(url: String): List<Pair<String, String>> {
    val queryIndex = url.indexOf('?')
    if (queryIndex < 0 || queryIndex == url.lastIndex) return emptyList()
    val queryAndFragment = url.substring(queryIndex + 1)
    val query = queryAndFragment.substringBefore('#')
    if (query.isBlank()) return emptyList()
    return query
        .split('&')
        .filter { pair -> pair.isNotBlank() && '=' in pair }
        .map { pair -> pair.substringBefore('=') to pair.substringAfter('=') }
}
