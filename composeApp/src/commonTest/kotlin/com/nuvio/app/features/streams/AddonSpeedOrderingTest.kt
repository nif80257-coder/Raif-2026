package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.AddonManifest
import kotlin.test.Test
import kotlin.test.assertEquals

class AddonSpeedOrderingTest {
    private fun target(url: String) = InstalledStreamAddonTarget(
        addonName = url,
        addonId = url,
        manifestUrl = url,
        manifest = AddonManifest(
            id = url,
            name = url,
            description = "",
            version = "1.0.0",
            resources = emptyList(),
            types = listOf("movie"),
            transportUrl = url,
        ),
    )

    @Test
    fun `the fastest measured source is queued first`() {
        val slow = target("https://slow.example/manifest.json")
        val fast = target("https://fast.example/manifest.json")
        val middle = target("https://middle.example/manifest.json")

        val ordered = listOf(slow, middle, fast).orderedByRecordedSpeed(
            mapOf(
                slow.manifestUrl to 9_000L,
                middle.manifestUrl to 3_000L,
                fast.manifestUrl to 400L,
            ),
        )

        assertEquals(listOf(fast, middle, slow), ordered)
    }

    @Test
    fun `a source without a measurement keeps its place after the measured ones`() {
        val known = target("https://known.example/manifest.json")
        val fresh = target("https://fresh.example/manifest.json")

        val ordered = listOf(fresh, known).orderedByRecordedSpeed(mapOf(known.manifestUrl to 2_000L))

        assertEquals(listOf(known, fresh), ordered)
    }

    @Test
    fun `equal times keep the user's own order`() {
        val first = target("https://first.example/manifest.json")
        val second = target("https://second.example/manifest.json")

        val ordered = listOf(first, second).orderedByRecordedSpeed(
            mapOf(first.manifestUrl to 1_000L, second.manifestUrl to 1_000L),
        )

        assertEquals(listOf(first, second), ordered)
    }

    @Test
    fun `nothing measured leaves the list untouched`() {
        val first = target("https://first.example/manifest.json")
        val second = target("https://second.example/manifest.json")
        val addons = listOf(first, second)

        assertEquals(addons, addons.orderedByRecordedSpeed(emptyMap()))
    }

    @Test
    fun `a source with a failure streak is queued after the sources that answer`() {
        val fastButFailing = target("https://failing.example/manifest.json")
        val slowButAnswering = target("https://slow.example/manifest.json")

        val ordered = listOf(fastButFailing, slowButAnswering).orderedByRecordedSpeed(
            speeds = mapOf(
                fastButFailing.manifestUrl to 300L,
                slowButAnswering.manifestUrl to 8_000L,
            ),
            failureStreaks = mapOf(fastButFailing.manifestUrl to 2),
        )

        assertEquals(listOf(slowButAnswering, fastButFailing), ordered)
    }

    @Test
    fun `a zero streak does not move a source`() {
        val fast = target("https://fast.example/manifest.json")
        val slow = target("https://slow.example/manifest.json")

        val ordered = listOf(slow, fast).orderedByRecordedSpeed(
            speeds = mapOf(fast.manifestUrl to 400L, slow.manifestUrl to 9_000L),
            failureStreaks = mapOf(slow.manifestUrl to 0),
        )

        assertEquals(listOf(fast, slow), ordered)
    }

    @Test
    fun `a streak reorders even before any source has been timed`() {
        val failing = target("https://failing.example/manifest.json")
        val fresh = target("https://fresh.example/manifest.json")

        val ordered = listOf(failing, fresh).orderedByRecordedSpeed(
            speeds = emptyMap(),
            failureStreaks = mapOf(failing.manifestUrl to 1),
        )

        assertEquals(listOf(fresh, failing), ordered)
    }

    @Test
    fun `a source that answered with no links is queued after one that returns links`() {
        val quickButEmpty = target("https://empty.example/manifest.json")
        val slowerButUseful = target("https://useful.example/manifest.json")

        val ordered = listOf(quickButEmpty, slowerButUseful).orderedByRecordedSpeed(
            speeds = mapOf(
                quickButEmpty.manifestUrl to 300L,
                slowerButUseful.manifestUrl to 4_000L,
            ),
            resultCounts = mapOf(
                quickButEmpty.manifestUrl to 0,
                slowerButUseful.manifestUrl to 9,
            ),
        )

        assertEquals(listOf(slowerButUseful, quickButEmpty), ordered)
    }

    @Test
    fun `a source with an unknown result count keeps its measured place`() {
        val measured = target("https://measured.example/manifest.json")
        val silent = target("https://empty.example/manifest.json")

        val ordered = listOf(silent, measured).orderedByRecordedSpeed(
            speeds = mapOf(measured.manifestUrl to 5_000L, silent.manifestUrl to 400L),
        )

        assertEquals(listOf(silent, measured), ordered)
    }

    @Test
    fun `a failing source waits behind a silent one because it costs the slot time`() {
        val failing = target("https://failing.example/manifest.json")
        val silent = target("https://empty.example/manifest.json")
        val useful = target("https://useful.example/manifest.json")

        val ordered = listOf(failing, silent, useful).orderedByRecordedSpeed(
            speeds = mapOf(
                failing.manifestUrl to 200L,
                silent.manifestUrl to 800L,
                useful.manifestUrl to 3_000L,
            ),
            failureStreaks = mapOf(failing.manifestUrl to 3),
            resultCounts = mapOf(failing.manifestUrl to 20, useful.manifestUrl to 4),
        )

        assertEquals(listOf(useful, silent, failing), ordered)
    }

    @Test
    fun `a measured time is written in seconds with one decimal`() {
        assertEquals("0.0", formatSourceSpeedSeconds(0L))
        assertEquals("0.4", formatSourceSpeedSeconds(400L))
        assertEquals("0.6", formatSourceSpeedSeconds(560L))
        assertEquals("1.2", formatSourceSpeedSeconds(1_240L))
        assertEquals("9.9", formatSourceSpeedSeconds(9_949L))
    }
}
