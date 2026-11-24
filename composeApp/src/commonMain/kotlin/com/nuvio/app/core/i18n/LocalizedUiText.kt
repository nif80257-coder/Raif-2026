package com.nuvio.app.core.i18n

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.player_picture_in_picture
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_play
import nuvio.composeapp.generated.resources.action_play_episode
import nuvio.composeapp.generated.resources.action_resume
import nuvio.composeapp.generated.resources.action_resume_episode
import nuvio.composeapp.generated.resources.compose_player_episode_code_episode_only
import nuvio.composeapp.generated.resources.compose_player_episode_code_full
import nuvio.composeapp.generated.resources.streams_episode_fallback_title
import nuvio.composeapp.generated.resources.compose_player_no_subtitle_lines_found
import nuvio.composeapp.generated.resources.compose_player_subtitle_lines_load_error
import nuvio.composeapp.generated.resources.continue_watching_up_next
import nuvio.composeapp.generated.resources.continue_watching_up_next_episode
import nuvio.composeapp.generated.resources.date_month_april
import nuvio.composeapp.generated.resources.date_month_august
import nuvio.composeapp.generated.resources.date_month_december
import nuvio.composeapp.generated.resources.date_month_february
import nuvio.composeapp.generated.resources.date_month_january
import nuvio.composeapp.generated.resources.date_month_july
import nuvio.composeapp.generated.resources.date_month_june
import nuvio.composeapp.generated.resources.date_month_march
import nuvio.composeapp.generated.resources.date_month_may
import nuvio.composeapp.generated.resources.date_month_november
import nuvio.composeapp.generated.resources.date_month_october
import nuvio.composeapp.generated.resources.date_month_september
import nuvio.composeapp.generated.resources.date_month_short_apr
import nuvio.composeapp.generated.resources.date_month_short_aug
import nuvio.composeapp.generated.resources.date_month_short_dec
import nuvio.composeapp.generated.resources.date_month_short_feb
import nuvio.composeapp.generated.resources.date_month_short_jan
import nuvio.composeapp.generated.resources.date_month_short_jul
import nuvio.composeapp.generated.resources.date_month_short_jun
import nuvio.composeapp.generated.resources.date_month_short_mar
import nuvio.composeapp.generated.resources.date_month_short_may
import nuvio.composeapp.generated.resources.date_month_short_nov
import nuvio.composeapp.generated.resources.date_month_short_oct
import nuvio.composeapp.generated.resources.date_month_short_sep
import nuvio.composeapp.generated.resources.media_anime
import nuvio.composeapp.generated.resources.media_channels
import nuvio.composeapp.generated.resources.media_movie
import nuvio.composeapp.generated.resources.media_movies
import nuvio.composeapp.generated.resources.media_series
import nuvio.composeapp.generated.resources.media_tv
import nuvio.composeapp.generated.resources.p2p_error_unknown
import nuvio.composeapp.generated.resources.settings_stream_badge_enter_url
import nuvio.composeapp.generated.resources.settings_stream_badge_import_failed
import nuvio.composeapp.generated.resources.settings_stream_badge_import_limit
import nuvio.composeapp.generated.resources.settings_stream_badge_url_scheme_invalid
import nuvio.composeapp.generated.resources.unit_bytes_b
import nuvio.composeapp.generated.resources.unit_bytes_gb
import nuvio.composeapp.generated.resources.unit_bytes_kb
import nuvio.composeapp.generated.resources.unit_bytes_mb
import org.jetbrains.compose.resources.getString

fun localizedMediaTypeLabel(type: String): String {
    val fallback = type.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    return when (type.trim().lowercase()) {
        "movie" -> resourceString("Movies") { getString(Res.string.media_movies) }
        "series" -> resourceString("Series") { getString(Res.string.media_series) }
        "anime" -> resourceString("Anime") { getString(Res.string.media_anime) }
        "channel" -> resourceString("Channels") { getString(Res.string.media_channels) }
        "tv" -> resourceString("TV") { getString(Res.string.media_tv) }
        else -> fallback
    }
}

fun localizedMovieTypeLabel(): String = resourceString("Movie") { getString(Res.string.media_movie) }


private val genericEpisodeTitleRegex = Regex("""(?i)^Episode\s+(\d+)$""")

/** Same idea as NuvioTV localizeEpisodeTitle: "Episode 12" -> localized word + number. */
fun localizedEpisodeTitle(episodeTitle: String?, episodeNumber: Int? = null): String? {
    val title = episodeTitle?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val match = genericEpisodeTitleRegex.matchEntire(title) ?: return title
    val n = match.groupValues[1].toIntOrNull() ?: episodeNumber ?: return title
    return resourceString("Episode $n") {
        "${getString(Res.string.streams_episode_fallback_title)} $n"
    }
}

fun localizedSeasonEpisodeCode(seasonNumber: Int?, episodeNumber: Int?): String? =
    when {
        seasonNumber != null && episodeNumber != null ->
            resourceString("S${seasonNumber} E${episodeNumber}") {
                getString(Res.string.compose_player_episode_code_full, seasonNumber, episodeNumber)
            }
        episodeNumber != null ->
            resourceString("E${episodeNumber}") {
                getString(Res.string.compose_player_episode_code_episode_only, episodeNumber)
            }
        else -> null
    }

fun localizedPlayLabel(seasonNumber: Int?, episodeNumber: Int?): String {
    val episodeCode = localizedSeasonEpisodeCode(seasonNumber, episodeNumber)
    return if (episodeCode != null) {
        resourceString("Play $episodeCode") { getString(Res.string.action_play_episode, episodeCode) }
    } else {
        resourceString("Play") { getString(Res.string.action_play) }
    }
}

fun localizedResumeLabel(seasonNumber: Int?, episodeNumber: Int?): String {
    val episodeCode = localizedSeasonEpisodeCode(seasonNumber, episodeNumber)
    return if (episodeCode != null) {
        resourceString("Resume $episodeCode") { getString(Res.string.action_resume_episode, episodeCode) }
    } else {
        resourceString("Resume") { getString(Res.string.action_resume) }
    }
}

fun localizedUpNextLabel(seasonNumber: Int?, episodeNumber: Int?): String =
    if (seasonNumber != null && episodeNumber != null) {
        resourceString("Next Up • S${seasonNumber} E${episodeNumber}") {
            getString(Res.string.continue_watching_up_next_episode, seasonNumber, episodeNumber)
        }
    } else {
        resourceString("Next Up") { getString(Res.string.continue_watching_up_next) }
    }

fun localizedMonthName(month: Int): String =
    when (month) {
        1 -> resourceString("January") { getString(Res.string.date_month_january) }
        2 -> resourceString("February") { getString(Res.string.date_month_february) }
        3 -> resourceString("March") { getString(Res.string.date_month_march) }
        4 -> resourceString("April") { getString(Res.string.date_month_april) }
        5 -> resourceString("May") { getString(Res.string.date_month_may) }
        6 -> resourceString("June") { getString(Res.string.date_month_june) }
        7 -> resourceString("July") { getString(Res.string.date_month_july) }
        8 -> resourceString("August") { getString(Res.string.date_month_august) }
        9 -> resourceString("September") { getString(Res.string.date_month_september) }
        10 -> resourceString("October") { getString(Res.string.date_month_october) }
        11 -> resourceString("November") { getString(Res.string.date_month_november) }
        12 -> resourceString("December") { getString(Res.string.date_month_december) }
        else -> month.toString()
    }

fun localizedShortMonthName(month: Int): String =
    when (month) {
        1 -> resourceString("Jan") { getString(Res.string.date_month_short_jan) }
        2 -> resourceString("Feb") { getString(Res.string.date_month_short_feb) }
        3 -> resourceString("Mar") { getString(Res.string.date_month_short_mar) }
        4 -> resourceString("Apr") { getString(Res.string.date_month_short_apr) }
        5 -> resourceString("May") { getString(Res.string.date_month_short_may) }
        6 -> resourceString("Jun") { getString(Res.string.date_month_short_jun) }
        7 -> resourceString("Jul") { getString(Res.string.date_month_short_jul) }
        8 -> resourceString("Aug") { getString(Res.string.date_month_short_aug) }
        9 -> resourceString("Sep") { getString(Res.string.date_month_short_sep) }
        10 -> resourceString("Oct") { getString(Res.string.date_month_short_oct) }
        11 -> resourceString("Nov") { getString(Res.string.date_month_short_nov) }
        12 -> resourceString("Dec") { getString(Res.string.date_month_short_dec) }
        else -> month.toString()
    }

fun localizedNoSubtitleLinesFound(): String =
    resourceString("No subtitle lines found") { getString(Res.string.compose_player_no_subtitle_lines_found) }

fun localizedSubtitleLinesLoadError(): String =
    resourceString("Unable to load subtitle lines") { getString(Res.string.compose_player_subtitle_lines_load_error) }

fun localizedBadgeImportFailed(): String =
    resourceString("Badge import failed.") { getString(Res.string.settings_stream_badge_import_failed) }

fun localizedBadgeEnterUrl(): String =
    resourceString("Enter a badge JSON URL.") { getString(Res.string.settings_stream_badge_enter_url) }

fun localizedBadgeUrlSchemeInvalid(): String =
    resourceString("Badge URL must start with http:// or https://.") { getString(Res.string.settings_stream_badge_url_scheme_invalid) }

fun localizedBadgeImportLimit(limit: Int): String =
    resourceString("You can import up to $limit badge URLs.") { getString(Res.string.settings_stream_badge_import_limit, limit) }

fun localizedP2pUnknownTorrentError(): String =
    resourceString("Unknown torrent error") { getString(Res.string.p2p_error_unknown) }

fun localizedByteUnit(unit: String): String =
    when (unit) {
        "GB" -> resourceString("GB") { getString(Res.string.unit_bytes_gb) }
        "MB" -> resourceString("MB") { getString(Res.string.unit_bytes_mb) }
        "KB" -> resourceString("KB") { getString(Res.string.unit_bytes_kb) }
        else -> resourceString("B") { getString(Res.string.unit_bytes_b) }
    }

fun localizedPlayerPictureInPicture(): String =
    resourceString("RAIF is playing in Picture in Picture mode") { getString(Res.string.player_picture_in_picture) }

/** Strings already read from the resources, keyed by the English text they replace. */
private val resolvedStrings = mutableStateMapOf<String, String>()

/** Strings a read is already running for, so one is not asked twice. */
private val resolvingStrings = mutableSetOf<String>()
private val resolvingLock = SynchronizedObject()
private val resourceStringScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * The viewer's language for [fallback], read without blocking the frame.
 *
 * These helpers are called straight from composition — a title, a label, a menu line — and the
 * resources call behind them suspends. `runBlocking` here froze the frame that drew the text
 * until it arrived, on the main thread, for every one of the hundred-odd strings below. Instead
 * the English text is returned at once and the translated one is read in the background into a
 * snapshot map: the read of that map happens during composition, so the view redraws with the
 * viewer's language as soon as it lands, and every later call returns it without asking again.
 */
private fun resourceString(
    fallback: String,
    provider: suspend () -> String,
): String {
    resolvedStrings[fallback]?.let { translated -> return translated }

    val shouldStart = synchronized(resolvingLock) { resolvingStrings.add(fallback) }
    if (shouldStart) {
        resourceStringScope.launch {
            val translated = runCatching { provider() }.getOrNull()
            synchronized(resolvingLock) { resolvingStrings.remove(fallback) }
            if (!translated.isNullOrBlank()) {
                resolvedStrings[fallback] = translated
            }
        }
    }
    return fallback
}
