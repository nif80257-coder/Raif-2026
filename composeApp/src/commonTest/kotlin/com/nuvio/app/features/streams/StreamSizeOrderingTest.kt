package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The list the viewer reads is ordered by quality first, then by how ready a row is to play, and
 * only then by size. Size is the tie-breaker: at the same quality and readiness the larger file is
 * the better copy, and the smaller one is a re-encode that costs the same band width for less.
 */
class StreamSizeOrderingTest {

    // Two copies at the same quality are two different files, so their addresses differ; the same
    // address twice would be one duplicate the list is right to collapse.
    private fun stream(label: String, sizeBytes: Long?): StreamItem = StreamItem(
        name = label,
        url = "https://example.org/$label-${sizeBytes ?: "unknown"}.mkv",
        addonName = "Test",
        addonId = "test",
        behaviorHints = StreamBehaviorHints(videoSize = sizeBytes),
    )

    @Test
    fun `the larger file comes first at the same quality`() {
        val smaller = stream("1080p WEB-DL", 1_500_000_000L)
        val larger = stream("1080p WEB-DL", 4_200_000_000L)

        val ordered = listOf(smaller, larger).sortedForGroupedDisplay()

        assertEquals(listOf(larger, smaller), ordered)
    }

    @Test
    fun `quality still outranks size`() {
        val uhd = stream("2160p WEB-DL", 900_000_000L)
        val hd = stream("1080p BluRay", 12_000_000_000L)

        val ordered = listOf(hd, uhd).sortedForGroupedDisplay()

        assertEquals(listOf(uhd, hd), ordered)
    }

    @Test
    fun `a row with no size sorts after a row that has one`() {
        val unknown = stream("1080p", null)
        val known = stream("1080p", 2_000_000_000L)

        val ordered = listOf(unknown, known).sortedForGroupedDisplay()

        assertEquals(listOf(known, unknown), ordered)
    }
}
