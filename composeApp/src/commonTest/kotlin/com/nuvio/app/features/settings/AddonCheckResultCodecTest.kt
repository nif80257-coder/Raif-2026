package com.nuvio.app.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddonCheckResultCodecTest {

    private fun result(
        manifestUrl: String = "https://example.com/manifest.json",
        status: AddonHealthStatus = AddonHealthStatus.SLOW,
        changedSinceLastCheck: Boolean? = null,
        videoId: String = AddonHealthCheckKnownVideoId,
    ) = AddonCheckResult(
        manifestUrl = manifestUrl,
        addonId = "org.example.addon",
        name = "Example | Source",
        enabled = true,
        status = status,
        responseTimeMs = 1_234L,
        manifestParsed = true,
        manifestError = null,
        manifestVersion = "1.2.3",
        supportedTypes = setOf(AddonSupportedType.STREAMS, AddonSupportedType.SUBTITLES),
        catalogsCount = 3,
        knownTest = AddonKnownContentTest(
            videoId = videoId,
            streamCount = 12,
            subtitleCount = 4,
            streamsTimedOut = false,
            subtitlesTimedOut = true,
            streamsError = null,
            subtitlesError = "timed out",
        ),
        changedSinceLastCheck = changedSinceLastCheck,
        checkedAtEpochMs = 1_700_000_000_000L,
    )

    @Test
    fun `a stored result comes back as it went in`() {
        val original = result(changedSinceLastCheck = true)

        val restored = decodeAddonCheckResult(original.manifestUrl, encodeAddonCheckResult(original))

        assertEquals(original, restored)
    }

    @Test
    fun `an unknown change state stays unknown rather than becoming false`() {
        val restored = decodeAddonCheckResult(
            "https://example.com/manifest.json",
            encodeAddonCheckResult(result(changedSinceLastCheck = null)),
        )

        assertNull(restored?.changedSinceLastCheck)
    }

    @Test
    fun `a name holding the separator does not break the record`() {
        val original = result()

        val restored = decodeAddonCheckResult(original.manifestUrl, encodeAddonCheckResult(original))

        assertEquals("Example | Source", restored?.name)
    }

    @Test
    fun `fields written by a newer build are ignored`() {
        val payload = encodeAddonCheckResult(result())
            .replaceFirst("{", """{"somethingNew":42,""")

        val restored = decodeAddonCheckResult("https://example.com/manifest.json", payload)

        assertEquals(AddonHealthStatus.SLOW, restored?.status)
    }

    @Test
    fun `an unreadable payload is dropped instead of guessed`() {
        assertNull(decodeAddonCheckResult("https://example.com/manifest.json", "not json"))
        assertNull(decodeAddonCheckResult("https://example.com/manifest.json", ""))
        assertNull(decodeAddonCheckResult("", encodeAddonCheckResult(result())))
    }

    @Test
    fun `a stored payload without a video id falls back to the known one`() {
        val payload = encodeAddonCheckResult(result()).replace(
            AddonHealthCheckKnownVideoId,
            "",
        )

        val restored = decodeAddonCheckResult("https://example.com/manifest.json", payload)

        assertEquals(AddonHealthCheckKnownVideoId, restored?.knownTest?.videoId)
    }

    @Test
    fun `an unknown status name falls back to ok`() {
        val payload = encodeAddonCheckResult(result()).replace("\"SLOW\"", "\"BRAND_NEW\"")

        val restored = decodeAddonCheckResult("https://example.com/manifest.json", payload)

        assertEquals(AddonHealthStatus.OK, restored?.status)
    }

    @Test
    fun `the map keeps the readable entries and skips the rest`() {
        val readableUrl = "https://example.com/manifest.json"
        val brokenUrl = "https://broken.example/manifest.json"
        val stored = mapOf(
            readableUrl to encodeAddonCheckResult(result(manifestUrl = readableUrl)),
            brokenUrl to "{",
        )

        val restored = decodeAddonCheckResults(stored)

        assertEquals(setOf(readableUrl), restored.keys)
        assertTrue(restored.getValue(readableUrl).manifestParsed)
    }

    @Test
    fun `every result of a map is written once`() {
        val results = mapOf(
            "https://a.example/manifest.json" to result("https://a.example/manifest.json"),
            "https://b.example/manifest.json" to result("https://b.example/manifest.json"),
        )

        val stored = encodeAddonCheckResults(results)

        assertEquals(results.keys, stored.keys)
        assertEquals(results, decodeAddonCheckResults(stored))
    }
}
