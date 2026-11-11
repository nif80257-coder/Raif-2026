package com.nuvio.app.features.party

import kotlinx.serialization.Serializable

/**
 * One finished or running watch party, kept on the device for the account that
 * took part in it. Nothing here leaves the device.
 */
@Serializable
internal data class PartyHistoryEntry(
    val id: String,
    val code: String,
    val title: String,
    val poster: String? = null,
    val metaType: String,
    val metaId: String,
    val videoId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String? = null,
    val startedAtEpochMs: Long,
    val members: Int = 1,
    val maxMembers: Int = 3,
    val host: Boolean = false,
    val ended: Boolean = false,
) {
    val media: PartyMedia
        get() = PartyMedia(
            type = metaType,
            metaId = metaId,
            metaType = metaType,
            videoId = videoId,
            season = season,
            episode = episode,
            title = title,
            episodeTitle = episodeTitle,
            poster = poster,
        )
}
