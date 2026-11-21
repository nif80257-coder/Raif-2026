package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamDedupeAndQualityTest {

    private fun stream(
        label: String,
        url: String,
        addonId: String,
        addonName: String = "Addon $addonId",
        headers: Map<String, String>? = null,
    ): StreamItem = StreamItem(
        name = label,
        url = url,
        addonName = addonName,
        addonId = addonId,
        behaviorHints = if (headers == null) {
            StreamBehaviorHints()
        } else {
            StreamBehaviorHints(proxyHeaders = StreamProxyHeaders(request = headers))
        },
    )

    @Test
    fun qualityRankOrdersResolutionHdrAndRemux() {
        val rankOf = { label: String ->
            stream(label = label, url = "https://example.com/x.mkv", addonId = "addon:rank").qualityRank()
        }
        val rank4kHdrRemux = rankOf("4K HDR Remux")
        val rank4kPlain = rankOf("2160p")
        val rank1080p = rankOf("1080p")
        val rank720p = rankOf("720p")

        assertTrue(rank4kHdrRemux > rank4kPlain)
        assertTrue(rank4kPlain > rank1080p)
        assertTrue(rank1080p > rank720p)
    }

    @Test
    fun sortedForGroupedDisplayPutsPreferredQualityFirst() {
        val streams = listOf(
            stream(label = "1080p", url = "https://example.com/1080.mkv", addonId = "addon:a"),
            stream(label = "720p", url = "https://example.com/720.mkv", addonId = "addon:a"),
            stream(label = "4K", url = "https://example.com/2160.mkv", addonId = "addon:a"),
        )

        // 200 is the rank of the 720p copy, so the list is asked for 720p first.
        val sortedByPreference = streams.sortedForGroupedDisplay(preferredQualityRank = 200)
        assertEquals("720p", sortedByPreference.first().streamLabel)

        val sortedDefault = streams.sortedForGroupedDisplay()
        assertEquals("4K", sortedDefault.first().streamLabel)
    }

    @Test
    fun sortedForGroupedDisplayDropsWithinGroupDuplicates() {
        val streams = listOf(
            stream(label = "1080p", url = "https://example.com/same.mkv", addonId = "addon:a"),
            stream(label = "1080p copy", url = "https://example.com/same.mkv", addonId = "addon:a"),
        )

        assertEquals(1, streams.sortedForGroupedDisplay().size)
    }

    @Test
    fun dedupeAcrossSourcesKeepsEachGroupAndRemovesDuplicates() {
        val groups = listOf(
            AddonStreamGroup(
                addonName = "First",
                addonId = "addon:first",
                streams = listOf(
                    stream(label = "1080p", url = "https://example.com/same.mkv", addonId = "addon:first"),
                ),
            ),
            AddonStreamGroup(
                addonName = "Second",
                addonId = "addon:second",
                streams = listOf(
                    // Same URL as the first group: a duplicate copy that must be dropped.
                    stream(label = "1080p", url = "https://example.com/same.mkv", addonId = "addon:second"),
                    // Different URL: must survive in its own group.
                    stream(label = "720p", url = "https://example.com/other.mkv", addonId = "addon:second"),
                ),
            ),
        )

        val deduped = groups.deduplicatedAcrossSources()

        assertEquals(2, deduped.size)
        assertEquals("addon:first", deduped[0].addonId)
        assertEquals(1, deduped[0].streams.size)
        assertEquals("addon:second", deduped[1].addonId)
        assertEquals(1, deduped[1].streams.size)
        assertEquals("720p", deduped[1].streams.single().streamLabel)
    }

    @Test
    fun dedupeAcrossSourcesTreatsDifferentRequestHeadersAsDifferentStreams() {
        val groups = listOf(
            AddonStreamGroup(
                addonName = "First",
                addonId = "addon:first",
                streams = listOf(
                    stream(
                        label = "1080p",
                        url = "https://example.com/same.mkv",
                        addonId = "addon:first",
                        headers = mapOf("User-Agent" to "client-a"),
                    ),
                ),
            ),
            AddonStreamGroup(
                addonName = "Second",
                addonId = "addon:second",
                streams = listOf(
                    stream(
                        label = "1080p",
                        url = "https://example.com/same.mkv",
                        addonId = "addon:second",
                        headers = mapOf("User-Agent" to "client-b"),
                    ),
                ),
            ),
        )

        val deduped = groups.deduplicatedAcrossSources()

        assertEquals(1, deduped[0].streams.size)
        assertEquals(1, deduped[1].streams.size)
    }
}
