package com.nuvio.app.features.party

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The single pending "continue where the party stopped" offer. It is kept per
 * signed-in account, only points at content that was actually played inside a
 * party, and is removed as soon as the user plays it or hides it.
 */
@Serializable
internal data class PartyResumeSuggestion(
    val metaType: String,
    val metaId: String,
    val videoId: String,
    val title: String = "",
    val poster: String? = null,
    val background: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val positionMs: Long = 0L,
    @SerialName("saved_at_epoch_ms") val savedAtEpochMs: Long = 0L,
)
