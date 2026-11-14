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
}
