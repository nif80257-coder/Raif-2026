package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamReleaseQualityTest {

    private fun stream(label: String, description: String? = null): StreamItem = StreamItem(
        name = label,
        description = description,
        url = "https://example.com/file.mkv",
        addonName = "Addon",
        addonId = "addon:one",
    )

    @Test
    fun readsTheTierFromTheTextASourceShows() {
        assertEquals(ReleaseTier.REMUX, stream("4K HDR Remux").releaseTier())
        assertEquals(ReleaseTier.BLURAY, stream("1080p BluRay x264-GRP").releaseTier())
        assertEquals(ReleaseTier.WEBDL, stream("Movie 2024 2160p WEB-DL DDP5.1").releaseTier())
        assertEquals(ReleaseTier.WEBRIP, stream("Movie 720p WEBRip x264").releaseTier())
        assertEquals(ReleaseTier.HDTV, stream("Show S01E02 HDTV").releaseTier())
        // A camera copy is its own tier.
        assertEquals(ReleaseTier.CAM, stream("Movie 2024 HDCAM").releaseTier())
        // A resolution alone says nothing about the source.
        assertNull(stream("Movie 1080p CH-HEB"))
    }

    @Test
    fun readsTheTierFromTheDescriptionWhenTheNameIsBare() {
        val item = stream(label = "Direct link", description = "BluRay REMUX · 42 GB")
        assertEquals(ReleaseTier.REMUX, item.releaseTier())
    }

    @Test
    fun cameraCopiesAndSizeOnlyGroupsAreJunk() {
        assertTrue(stream("Movie HDTS").isJunkRelease())
        assertTrue(stream("Movie 2024 CAM").isJunkRelease())
        assertTrue(stream("Movie 1080p", "YIFY").isJunkRelease())
        assertFalse(stream("Movie 1080p WEB-DL").isJunkRelease())
        assertFalse(stream("Movie 2160p Remux").isJunkRelease())
    }

    @Test
    fun aRemuxOutranksAWebReEncodeAtTheSameResolution() {
        val remux = stream("Movie 2160p Remux").qualityRank()
        val bluray = stream("Movie 2160p BluRay").qualityRank()
        val webDl = stream("Movie 2160p WEB-DL").qualityRank()
        val webRip = stream("Movie 2160p WEBRip").qualityRank()
        val cam = stream("Movie 2160p CAM").qualityRank()

        assertTrue(remux > bluray)
        assertTrue(bluray > webDl)
        assertTrue(webDl > webRip)
        assertTrue(webRip > cam)
    }

    @Test
    fun resolutionStillOutranksTheSource() {
        assertTrue(stream("Movie 2160p WEBRip").qualityRank() > stream("Movie 1080p Remux").qualityRank())
    }
}
