package com.nuvio.app.features.p2p

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicTrackersTest {

    @Test
    fun theSharedListIsLongEnoughToCoverTheStreamsThatNameOnlyAFew() {
        assertTrue(PublicTrackers.All.size >= 20, "expected a complete tracker list")
    }

    @Test
    fun everyTrackerIsAnAnnounceUrl() {
        PublicTrackers.All.forEach { tracker ->
            assertTrue(
                tracker.startsWith("udp://") || tracker.startsWith("http://") || tracker.startsWith("https://"),
                "not an announce URL: $tracker",
            )
            assertTrue(tracker.endsWith("/announce"), "not an announce path: $tracker")
        }
    }

    @Test
    fun theListHasNoDuplicates() {
        assertEquals(PublicTrackers.All.size, PublicTrackers.All.distinct().size)
    }

    @Test
    fun streamTrackersAreKeptAlongsideThePublicOnesWithoutRepeats() {
        val streamTrackers = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            " udp://stream.example.org:1337/announce ",
            "",
        )

        val combined = PublicTrackers.withStreamTrackers(streamTrackers)

        assertEquals(combined.size, combined.distinct().size)
        assertTrue("udp://stream.example.org:1337/announce" in combined)
        assertEquals(PublicTrackers.All.size + 1, combined.size)
    }
}
