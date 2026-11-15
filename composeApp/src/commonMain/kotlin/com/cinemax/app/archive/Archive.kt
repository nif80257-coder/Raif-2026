package com.cinemax.app.archive

import com.cinemax.app.addons.StremioMeta
import com.cinemax.app.addons.StremioMetaPreview
import com.cinemax.app.addons.StremioStream
import com.cinemax.app.addons.StremioSubtitle
import com.cinemax.app.core.subtitleLanguageName
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A built-in source of public-domain films from the Internet Archive, so the app
 * can show and play something on a fresh install, with no addon, provider, key or
 * torrent engine involved. Every stream it returns is a direct HTTPS file.
 */
const val ARCHIVE_ID_PREFIX = "archive:"
const val ARCHIVE_PROVIDER_NAME = "المكتبة الحرة"

/**
 * Genres offered for the archive shelf, as an Arabic label paired with the
 * subject the collection actually uses in its metadata.
 */
val ARCHIVE_GENRES: List<Pair<String, String>> = listOf(
    "أكشن" to "Action",
    "مغامرة" to "Adventure",
    "كوميدي" to "Comedy",
    "دراما" to "Drama",
    "رعب" to "Horror",
    "خيال علمي" to "Sci-Fi",
    "غربي" to "Western",
    "جريمة" to "Crime",
    "رومانسي" to "Romance",
    "وثائقي" to "Documentary",
    "رسوم متحركة" to "Animation",
    "صامت" to "Silent"
)

/** The archive subject behind an Arabic genre label, or null for "all". */
fun archiveGenreSubject(label: String?): String? =
    ARCHIVE_GENRES.firstOrNull { it.first == label }?.second

/** Sort orders offered for the archive shelf, as a label and the archive sort field. */
val ARCHIVE_SORTS: List<Pair<String, String>> = listOf(
    "الأكثر تحميلًا" to "downloads desc",
    "الأحدث إضافة" to "addeddate desc",
    "أبجديًا" to "title asc"
)

/** The archive sort expression behind a label, defaulting to most downloaded. */
fun archiveSortValue(label: String?): String =
    ARCHIVE_SORTS.firstOrNull { it.first == label }?.second ?: "downloads desc"

fun archiveItemId(identifier: String): String = ARCHIVE_ID_PREFIX + identifier

fun isArchiveItemId(id: String): Boolean = id.startsWith(ARCHIVE_ID_PREFIX)

fun archiveIdentifier(itemId: String): String = itemId.removePrefix(ARCHIVE_ID_PREFIX)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }
private val yearPattern = Regex("""\((18|19|20)\d{2}\)""")

/** The year inside a title such as "Its Love Again (1936)", when the addon has none. */
fun yearFromTitle(title: String): String? =
    yearPattern.find(title)?.value?.trim('(', ')')

/** Turns one `advancedsearch.php` document into a preview card. */
fun archivePreviewFrom(document: JsonObject): StremioMetaPreview? {
    val identifier = (document["identifier"] as? JsonPrimitive)?.contentOrNull ?: return null
    val title = (document["title"] as? JsonPrimitive)?.contentOrNull
        ?.takeIf { it.isNotBlank() } ?: identifier
    val description = (document["description"] as? JsonPrimitive)?.contentOrNull
        ?.replace(Regex("<[^>]+>"), "")
        ?.trim()
    val year = (document["year"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: yearFromTitle(title)
    val poster = "https://archive.org/services/img/$identifier"
    return StremioMetaPreview(
        id = archiveItemId(identifier),
        type = "movie",
        name = title,
        poster = poster,
        background = poster,
        description = description?.takeIf { it.isNotBlank() },
        releaseInfo = year,
        imdbRating = null
    )
}

fun parseArchivePreviews(raw: String): List<StremioMetaPreview> =
    runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return emptyList()
        val response = root["response"] as? JsonObject ?: return emptyList()
        val docs = response["docs"] as? JsonArray ?: return emptyList()
        docs.mapNotNull { element -> (element as? JsonObject)?.let { archivePreviewFrom(it) } }
            .distinctBy { it.id }
    }.getOrDefault(emptyList())

/** "1 س 32 د" from an archive `length` value ("5520" or "1:32:00"), or null. */
fun archiveRuntime(length: String?): String? {
    val raw = length?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val seconds = if (raw.contains(':')) {
        raw.split(':').mapNotNull { it.toIntOrNull() }
            .fold(0L) { total, part -> total * 60 + part }
    } else {
        raw.toDoubleOrNull()?.toLong() ?: return null
    }
    if (seconds <= 0L) return null
    val hours = seconds / 3600L
    val minutes = (seconds % 3600L) / 60L
    return when {
        hours > 0L && minutes > 0L -> "$hours س $minutes د"
        hours > 0L -> "$hours س"
        minutes > 0L -> "$minutes د"
        else -> null
    }
}

/** Metadata for the detail screen, read from `metadata/{identifier}`. */
fun parseArchiveMeta(raw: String, identifier: String): StremioMeta? = runCatching {
    val root = json.parseToJsonElement(raw) as? JsonObject ?: return null
    val metadata = root["metadata"] as? JsonObject ?: return null
    val title = (metadata["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: (metadata["identifier"] as? JsonPrimitive)?.contentOrNull
        ?: identifier
    val description = (metadata["description"] as? JsonPrimitive)?.contentOrNull
        ?.replace(Regex("<[^>]+>"), "")
        ?.trim()
    val year = (metadata["year"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: (metadata["date"] as? JsonPrimitive)?.contentOrNull?.take(4)?.takeIf { it.isNotBlank() }
        ?: yearFromTitle(title)
    val subjects = (metadata["subject"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .orEmpty()
    val poster = "https://archive.org/services/img/$identifier"
    StremioMeta(
        id = archiveItemId(identifier),
        type = "movie",
        name = title,
        poster = poster,
        background = poster,
        description = description?.takeIf { it.isNotBlank() },
        releaseInfo = year,
        runtime = archiveRuntime((metadata["length"] as? JsonPrimitive)?.contentOrNull),
        genres = subjects.take(4)
    )
}.getOrNull()

/**
 * Language code for a sidecar subtitle name: "film.en.srt" and "film_ar.vtt"
 * both work, anything else is reported as unknown ("und").
 */
fun archiveSubtitleLanguage(fileName: String): String {
    val stem = fileName.substringBeforeLast('.')
    val tail = stem.substringAfterLast('.').ifEmpty { stem.substringAfterLast('_') }
    return tail.lowercase().takeIf { it.length in 2..3 && it.all { char -> char in 'a'..'z' } }
        ?: "und"
}

/** "الإنجليزية" for "film.en.srt", or the file name when the language is unknown. */
fun archiveSubtitleLabel(fileName: String): String {
    val language = subtitleLanguageName(archiveSubtitleLanguage(fileName))
    return language ?: fileName
}

/**
 * Direct HTTPS files of one archive item, biggest first, labelled with the file's
 * height when the item reports it. Sidecar `.srt` / `.vtt` files next to the video
 * are attached as subtitles, which these older films often ship with.
 */
fun parseArchiveStreams(raw: String, identifier: String): List<StremioStream> = runCatching {
    val root = json.parseToJsonElement(raw) as? JsonObject ?: return emptyList()
    val files = root["files"] as? JsonArray ?: return emptyList()
    val entries = files.mapNotNull { element -> element as? JsonObject }
        .mapNotNull { file ->
            val name = (file["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            name to file
        }
    val subtitles = entries
        .filter { (name, _) ->
            val lower = name.lowercase()
            lower.endsWith(".srt") || lower.endsWith(".vtt")
        }
        .map { (name, _) ->
            StremioSubtitle(
                id = name,
                url = archiveFileUrl(identifier, name),
                lang = archiveSubtitleLanguage(name),
                label = archiveSubtitleLabel(name)
            )
        }
    entries
        .filter { (name, _) ->
            val lower = name.lowercase()
            lower.endsWith(".mp4") || lower.endsWith(".m4v") ||
                lower.endsWith(".webm") || lower.endsWith(".mkv")
        }
        .mapNotNull { (name, file) ->
            val size = (file["size"] as? JsonPrimitive)?.longOrNull ?: 0L
            if (size in 1L until 5_000_000L) return@mapNotNull null
            val height = (file["height"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
            val label = if (height != null && height > 0) "${height}p" else "ملف"
            StremioStream(
                name = label,
                title = listOfNotNull(
                    name,
                    size.takeIf { it > 0L }?.let { "${it / (1024L * 1024L)} م.ب" }
                ).joinToString(" • "),
                url = archiveFileUrl(identifier, name),
                subtitles = subtitles
            )
        }
        .sortedByDescending { it.name?.replace("p", "")?.toIntOrNull() ?: 0 }
}.getOrDefault(emptyList())

/** The public HTTPS URL of one file inside an archive item. */
fun archiveFileUrl(identifier: String, fileName: String): String =
    "https://archive.org/download/${identifier.encodeURLParameter()}/${fileName.encodeURLParameter()}"

/** Search and metadata calls against the Internet Archive. */
class ArchiveClient(private val http: HttpClient) {

    suspend fun popular(
        rows: Int = 60,
        page: Int = 1,
        genre: String? = null,
        sort: String = "downloads desc"
    ): List<StremioMetaPreview> {
        val subject = archiveGenreSubject(genre)
        val base = "collection:feature_films AND mediatype:movies"
        val query = if (subject == null) base else "$base AND subject:\"$subject\""
        return query(query, rows = rows, page = page, sort = sort)
    }

    suspend fun search(text: String, rows: Int = 24): List<StremioMetaPreview> {
        val clean = text.replace(Regex("""[^\p{L}\p{N}\s]"""), " ").trim()
        if (clean.isEmpty()) return emptyList()
        return query(
            "collection:feature_films AND mediatype:movies AND (title:($clean) OR description:($clean))",
            rows = rows,
            page = 1,
            sort = "downloads desc"
        )
    }

    suspend fun meta(identifier: String): StremioMeta? {
        val raw = http.get("https://archive.org/metadata/$identifier").bodyAsText()
        return parseArchiveMeta(raw, identifier)
    }

    suspend fun streams(identifier: String): List<StremioStream> {
        val raw = http.get("https://archive.org/metadata/$identifier").bodyAsText()
        return parseArchiveStreams(raw, identifier)
    }

    private suspend fun query(query: String, rows: Int, page: Int, sort: String): List<StremioMetaPreview> {
        val url = buildString {
            append("https://archive.org/advancedsearch.php?q=")
            append(query.encodeURLParameter())
            append("&fl[]=identifier&fl[]=title&fl[]=year&fl[]=description")
            append("&rows=").append(rows)
            append("&page=").append(page)
            append("&sort[]=").append(sort.replace(" ", "+").encodeURLParameter())
            append("&output=json")
        }
        return parseArchivePreviews(http.get(url).bodyAsText())
    }
}
