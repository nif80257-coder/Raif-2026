package com.cinemax.app.core

/**
 * Finds the media-selection option that matches a preferred language code.
 * Exact matches win; otherwise a tag such as "ar-SA" matches the wanted "ar".
 */
fun languageIndex(candidates: List<String?>, wanted: String?): Int? {
    if (wanted.isNullOrBlank()) return null
    val target = wanted.trim().lowercase()
    candidates.forEachIndexed { index, candidate ->
        if (candidate?.lowercase() == target) return index
    }
    candidates.forEachIndexed { index, candidate ->
        val code = candidate?.lowercase() ?: return@forEachIndexed
        if (code == target || code.startsWith("$target-") || code.startsWith("${target}_")) return index
    }
    return null
}
