package com.cinemax.app.archive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchiveParsingTest {

    private val searchJson = """
        {"response":{"numFound":2,"docs":[
          {"identifier":"its-love-again-1936","title":"Its Love Again (1936)","description":"<p>A singer</p>"},
          {"identifier":"plain-item","title":"Plain Item","year":"1951"}
        ]}}
    """.trimIndent()

    private val metadataJson = """
        {"metadata":{"identifier":"its-love-again-1936","title":"Its Love Again","description":"A story",
          "date":"1936-01-01","subject":["Comedy","Romance"]},
         "files":[
           {"name":"Its Love Again (1936).mp4","format":"MPEG4","size":"183984774","height":"360"},
           {"name":"thumb.jpg","format":"Thumbnail","size":"9000"},
           {"name":"tiny.mp4","format":"MPEG4","size":"1000"}
         ]}
    """.trimIndent()

    @Test
    fun previewsCarryArchiveIdAndPoster() {
        val previews = parseArchivePreviews("""{"response":{"docs":[{"identifier":"abc","title":"A Film"}]}}""")
        val first = previews.single()
        assertTrue(isArchiveItemId(first.id))
        assertEquals("abc", archiveIdentifier(first.id))
        assertEquals("movie", first.type)
        assertTrue(first.poster?.contains("/services/img/abc") == true)
    }

    @Test
    fun previewsDropHtmlFromDescriptions() {
        val first = parseArchivePreviews(searchJson).first()
        assertEquals("A singer", first.description)
    }

    @Test
    fun yearComesFromTheTitleWhenTheFieldIsMissing() {
        assertEquals("1936", parseArchivePreviews(searchJson).first().releaseInfo)
        assertEquals("1951", parseArchivePreviews(searchJson)[1].releaseInfo)
    }

    @Test
    fun documentsWithoutIdentifierAreSkipped() {
        assertEquals(0, parseArchivePreviews("""{"response":{"docs":[{"title":"No id"}]}}""").size)
    }

    @Test
    fun metaReadsTitleDescriptionYearAndGenres() {
        val meta = parseArchiveMeta(metadataJson, "its-love-again-1936")
        assertEquals("Its Love Again", meta?.name)
        assertEquals("A story", meta?.description)
        assertEquals("1936", meta?.releaseInfo)
        assertEquals(listOf("Comedy", "Romance"), meta?.genres)
    }

    @Test
    fun streamsKeepOnlyRealVideoFilesBigEnoughToPlay() {
        val streams = parseArchiveStreams(metadataJson, "its-love-again-1936")
        assertEquals(1, streams.size)
        val stream = streams.single()
        assertEquals("360p", stream.name)
        assertTrue(stream.url!!.startsWith("https://archive.org/download/its-love-again-1936/"))
        assertTrue(stream.url!!.contains(".mp4"))
        assertTrue(stream.title!!.contains("175"))
    }

    @Test
    fun malformedJsonYieldsNoResultsInsteadOfThrowing() {
        assertEquals(emptyList(), parseArchivePreviews("not json"))
        assertEquals(emptyList(), parseArchiveStreams("[]", "x"))
        assertEquals(null, parseArchiveMeta("", "x"))
    }

    @Test
    fun yearHelperOnlyMatchesFourDigitYearsInParentheses() {
        assertEquals("1994", yearFromTitle("Nineteen (1994) Restored"))
        assertEquals(null, yearFromTitle("No year here"))
        assertEquals(null, yearFromTitle("Camera 2020 model"))
    }

    @Test
    fun sidecarSubtitlesAreAttachedToEveryVideoStream() {
        val raw = """
            {"metadata":{"title":"Fil m"},"files":[
              {"name":"film.mp4","size":"90000000","height":"720"},
              {"name":"film.en.srt","size":"1200"},
              {"name":"film_ar.vtt","size":"1300"},
              {"name":"poster.jpg","size":"9000"}
            ]}
        """.trimIndent()
        val stream = parseArchiveStreams(raw, "item").single()
        assertEquals(2, stream.subtitles.size)
        assertEquals(listOf("en", "ar"), stream.subtitles.map { it.lang })
        assertTrue(stream.subtitles.first().url.endsWith("/film.en.srt"))
    }

    @Test
    fun subtitleLanguageIsReadFromTheFileNameTail() {
        assertEquals("en", archiveSubtitleLanguage("movie.en.srt"))
        assertEquals("ar", archiveSubtitleLanguage("movie_ar.vtt"))
        assertEquals("und", archiveSubtitleLanguage("subtitles.srt"))
    }

    @Test
    fun subtitleLabelsUseTheLanguageNameWhenItIsKnown() {
        assertEquals("الإنجليزية", archiveSubtitleLabel("movie.en.srt"))
        assertEquals("العربية", archiveSubtitleLabel("movie_ar.vtt"))
        assertEquals("subtitles.srt", archiveSubtitleLabel("subtitles.srt"))
    }

    @Test
    fun genreLabelsMapToArchiveSubjects() {
        assertEquals("Western", archiveGenreSubject("غربي"))
        assertEquals("Action", archiveGenreSubject("أكشن"))
        assertEquals(null, archiveGenreSubject("الكل"))
        assertEquals(null, archiveGenreSubject(null))
    }

    @Test
    fun genreLabelsAreUnique() {
        assertEquals(ARCHIVE_GENRES.size, ARCHIVE_GENRES.map { it.first }.distinct().size)
        assertEquals(ARCHIVE_GENRES.size, ARCHIVE_GENRES.map { it.second }.distinct().size)
    }

    @Test
    fun sortLabelsMapToArchiveSortFields() {
        assertEquals("downloads desc", archiveSortValue("الأكثر تحميلًا"))
        assertEquals("addeddate desc", archiveSortValue("الأحدث إضافة"))
        assertEquals("title asc", archiveSortValue("أبجديًا"))
        assertEquals("downloads desc", archiveSortValue(null))
        assertEquals(ARCHIVE_SORTS.size, ARCHIVE_SORTS.map { it.first }.distinct().size)
    }

    @Test
    fun fileUrlsEncodeTheIdentifierAndName() {
        val url = archiveFileUrl("its-love-again-1936", "Its Love Again.mp4")
        assertEquals("https://archive.org/download/its-love-again-1936/Its%20Love%20Again.mp4", url)
    }
}
