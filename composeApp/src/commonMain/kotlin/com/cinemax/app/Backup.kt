package com.cinemax.app

import com.cinemax.app.addons.InstalledAddon
import com.cinemax.app.addons.StremioMetaPreview
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A portable snapshot of what the viewer built up: the saved list, the resume
 * points, the hand-marked episodes and the installed addons. Enough to move to a
 * new phone without re-adding everything by hand.
 */
@Serializable
data class CinemaxBackup(
    val version: Int = 1,
    val favorites: List<StremioMetaPreview> = emptyList(),
    val progress: List<WatchProgress> = emptyList(),
    val watched: List<String> = emptyList(),
    val addons: List<InstalledAddon> = emptyList()
)

private val backupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Serialises the snapshot to one line of text that can be copied anywhere. */
fun encodeBackup(backup: CinemaxBackup): String = backupJson.encodeToString(backup)

/**
 * Reads a snapshot back. A payload from a newer app version is accepted as long
 * as the fields it uses are known; a broken one yields null instead of throwing.
 */
fun decodeBackup(raw: String): CinemaxBackup? {
    if (raw.isBlank()) return null
    return runCatching { backupJson.decodeFromString<CinemaxBackup>(raw) }.getOrNull()
        ?.takeIf { it.version in 1..MAX_SUPPORTED_VERSION }
}

private const val MAX_SUPPORTED_VERSION = 5

/** Honest count of what a snapshot carries, for the settings line. */
fun CinemaxBackup.summary(): String =
    "${favorites.size} محفوظًا • ${progress.size} قيد المشاهدة • ${watched.size} حلقة معلّمة • ${addons.size} إضافة"

/** Exposed for tests that build a payload from raw lists. */
internal fun encodeFavoritesJson(items: List<StremioMetaPreview>): String =
    backupJson.encodeToString(ListSerializer(StremioMetaPreview.serializer()), items)
