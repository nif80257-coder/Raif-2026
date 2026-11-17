package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.qualityRank

internal fun sourceTrackPreferenceStorageKey(parentMetaId: String, sourceId: String): String =
    "$parentMetaId|source:${sourceId.trim()}"

internal fun loadPreferredSourceQualityRank(parentMetaId: String, sourceId: String): Int? =
    PlayerTrackPreferenceStorage
        .load(sourceTrackPreferenceStorageKey(parentMetaId, sourceId))
        ?.preferredQualityRank

internal fun persistStreamQualityPreference(parentMetaId: String, stream: StreamItem) {
    if (parentMetaId.isBlank()) return
    val sourceId = stream.addonId.takeIf(String::isNotBlank)
        ?: stream.playerSourceIdentityKey()
        ?: return
    val key = sourceTrackPreferenceStorageKey(parentMetaId, sourceId)
    val current = PlayerTrackPreferenceStorage.load(key) ?: PersistedPlayerTrackPreference()
    val qualityRank = stream.qualityRank()
    if (current.preferredQualityRank == qualityRank) return
    PlayerTrackPreferenceStorage.save(key, current.copy(preferredQualityRank = qualityRank))
}
