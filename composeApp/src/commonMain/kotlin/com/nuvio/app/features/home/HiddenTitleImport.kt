package com.nuvio.app.features.home

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Reads the exclusion lists the Stremio ecosystem ships as `exclude/meta.json`: an array of
 * entries that name a title by id, either wrapped in `meta` or flat, or plain strings. The result
 * is the set of hidden-title keys [ContentFilterRepository] stores.
 */
internal object HiddenTitleImportParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(payload: String): Set<String> {
        val root = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: return emptySet()
        val entries = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["metas"] as? JsonArray)
                ?: (root["exclude"] as? JsonArray)
                ?: (root["items"] as? JsonArray)
                ?: return emptySet()

            else -> return emptySet()
        }
        return entries.flatMapTo(mutableSetOf()) { element -> element.hiddenKeys() }
    }

    private fun JsonElement.hiddenKeys(): List<String> = when (this) {
        is JsonPrimitive -> contentOrNull?.trim()?.takeIf(String::isNotBlank)?.toHiddenKeys().orEmpty()
        is JsonObject -> {
            val meta = this["meta"] as? JsonObject
            val id = ((meta?.get("id") ?: this["id"]) as? JsonPrimitive)
                ?.contentOrNull
                ?.trim()
                ?.takeIf(String::isNotBlank)
            if (id == null) {
                emptyList()
            } else {
                val type = ((meta?.get("type") ?: this["type"]) as? JsonPrimitive)
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                if (type == null) id.toHiddenKeys() else listOf(ContentFilterRepository.hiddenKey(type, id))
            }
        }

        else -> emptyList()
    }

    /** An id alone hides it in both catalog types; `series:tt1` names one type. */
    private fun String.toHiddenKeys(): List<String> {
        val separator = indexOf(':')
        if (separator > 0) {
            val type = substring(0, separator).trim()
            val id = substring(separator + 1).trim()
            if (type.isNotEmpty() && id.isNotEmpty()) {
                return listOf(ContentFilterRepository.hiddenKey(type, id))
            }
        }
        return listOf(
            ContentFilterRepository.hiddenKey("movie", this),
            ContentFilterRepository.hiddenKey("series", this),
        )
    }
}
