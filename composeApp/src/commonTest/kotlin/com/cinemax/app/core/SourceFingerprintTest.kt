package com.cinemax.app.core

import com.cinemax.app.addons.StremioStream
import com.cinemax.app.addons.StreamOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SourceFingerprintTest {

    private fun option(
        addon: String,
        url: String? = null,
        hash: String? = null,
        fileIdx: Int? = null,
        name: String = "1080p"
    ) = StreamOption(
        addonId = addon,
        addonName = addon.uppercase(),
        stream = StremioStream(name = name, url = url, infoHash = hash, fileIdx = fileIdx)
    )

    @Test
    fun trackingAndSignatureParametersAreIgnored() {
        val first = normalizeStreamUrl("https://cdn.example.com/movie.mp4?utm_source=app&token=abc&b=2&a=1")
        val second = normalizeStreamUrl("https://www.cdn.example.com/movie.mp4?a=1&b=2&token=xyz&sig=9")
        assertEquals(first, second)
    }

    @Test
    fun meaningfulQueryParametersAreKept() {
        assertNotEquals(
            normalizeStreamUrl("https://cdn.example.com/v?quality=1080"),
            normalizeStreamUrl("https://cdn.example.com/v?quality=720")
        )
    }

    @Test
    fun theSameTorrentSharesAFingerprintAcrossAddons() {
        val first = streamFingerprint(option("addon-a", hash = "ABC123"))
        val second = streamFingerprint(option("addon-b", hash = "abc123"))
        assertEquals(first, second)
        assertNotEquals(first, streamFingerprint(option("addon-c", hash = "abc123", fileIdx = 4)))
    }

    @Test
    fun mergeShowsOneRowAndEveryProvider() {
        val merged = mergeSources(
            listOf(
                option("addon-a", url = "https://cdn.example.com/m.mp4?token=1"),
                option("addon-b", url = "https://cdn.example.com/m.mp4?token=2"),
                option("addon-c", url = "https://other.example.com/m.mp4")
            )
        )
        assertEquals(2, merged.size)
        val shared = merged.first { it.providers.size == 2 }
        assertEquals(listOf("ADDON-A", "ADDON-B"), shared.providers)
    }

    @Test
    fun theBestDescribedEntryRepresentsTheGroup() {
        val merged = mergeSources(
            listOf(
                option("addon-a", url = "https://cdn.example.com/m.mp4", name = "1080p"),
                option("addon-b", url = "https://cdn.example.com/m.mp4", name = "2160p 4K")
            )
        )
        assertEquals("2160p 4K", merged.single().option.stream.name)
        assertEquals(2, merged.single().providers.size)
    }
}
