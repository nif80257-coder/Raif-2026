package com.cinemax.app.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One addon from an exported sources file (Fusion / Stremio exports use this shape). */
@Serializable
data class SuggestedSource(val name: String = "", val url: String = "")

@Serializable
private data class SourcesFile(val sources: List<SuggestedSource> = emptyList())

private val sourcesJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

/** Parses one exported sources file, dropping entries without a URL. */
fun parseSuggestedSources(raw: String): List<SuggestedSource> = runCatching {
    sourcesJson.decodeFromString(SourcesFile.serializer(), raw)
        .sources
        .filter { it.url.isNotBlank() }
        .map { it.copy(name = it.name.trim().ifBlank { it.url.trim() }, url = it.url.trim()) }
}.getOrDefault(emptyList())

/**
 * Merges several exported lists into one, keeping the first entry per URL and
 * sorting by name, which is how the two exported files are de-duplicated.
 */
fun dedupeSources(lists: List<List<SuggestedSource>>): List<SuggestedSource> {
    val seen = mutableSetOf<String>()
    val merged = mutableListOf<SuggestedSource>()
    for (list in lists) {
        for (source in list) {
            val key = source.url.trimEnd('/').lowercase()
            if (key.isEmpty() || !seen.add(key)) continue
            merged.add(source)
        }
    }
    return merged.sortedBy { it.name.lowercase() }
}
