package com.cinemax.app.archive

import com.cinemax.app.addons.StremioMeta
import com.cinemax.app.addons.StremioMetaPreview
import com.cinemax.app.addons.StremioStream
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
        genres = subjects.take(4)
    )
}.getOrNull()

/**
 * Direct HTTPS files of one archive item, biggest first, labelled with the file's
 * height when the item reports it.
 */
fun parseArchiveStreams(raw: String, identifier: String): List<StremioStream> = runCatching {
    val root = json.parseToJsonElement(raw) as? JsonObject ?: return emptyList()
    val files = root["files"] as? JsonArray ?: return emptyList()
    files.mapNotNull { element -> element as? JsonObject }
        .mapNotNull { file ->
            val name = (file["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val lower = name.lowercase()
            if (!lower.endsWith(".mp4") && !lower.endsWith(".m4v") && !lower.endsWith(".webm") &&
                !lower.endsWith(".mkv")
            ) {
                return@mapNotNull null
            }
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
                url = "https://archive.org/download/${identifier.encodeURLParameter()}/${name.encodeURLParameter()}"
            )
        }
        .sortedByDescending { it.name?.replace("p", "")?.toIntOrNull() ?: 0 }
}.getOrDefault(emptyList())

/** Search and metadata calls against the Internet Archive. */
class ArchiveClient(private val http: HttpClient) {

    suspend fun popular(rows: Int = 24, page: Int = 1): List<StremioMetaPreview> = query(
        "collection:feature_films AND mediatype:movies",
        rows = rows,
        page = page,
        sort = "downloads desc"
    )

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
