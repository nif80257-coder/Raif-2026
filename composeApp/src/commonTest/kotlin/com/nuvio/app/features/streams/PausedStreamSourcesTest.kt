package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.AddonResource
import com.nuvio.app.features.addons.ManagedAddon
import kotlin.test.Test
import kotlin.test.assertEquals

class PausedStreamSourcesTest {
    private fun addon(
        url: String,
        enabled: Boolean = true,
        resourceName: String = "stream",
    ): ManagedAddon = ManagedAddon(
        manifestUrl = url,
        enabled = enabled,
        manifest = AddonManifest(
            id = url,
            name = url,
            description = "",
            version = "1.0.0",
            resources = listOf(AddonResource(name = resourceName, types = listOf("movie"))),
            types = listOf("movie"),
            transportUrl = url,
        ),
    )

    @Test
    fun `no pause means nothing to report`() {
        assertEquals(
            0,
            pausedStreamSourceCount(
                addons = listOf(addon("https://a.example/manifest.json")),
                pausedManifestUrls = emptySet(),
                type = "movie",
                videoId = "tt1",
            ),
        )
    }

    @Test
    fun `only paused sources that could serve this title are counted`() {
        val serving = addon("https://serving.example/manifest.json")
        val subtitlesOnly = addon("https://subs.example/manifest.json", resourceName = "subtitles")

        val count = pausedStreamSourceCount(
            addons = listOf(serving, subtitlesOnly),
            pausedManifestUrls = setOf(serving.manifestUrl, subtitlesOnly.manifestUrl),
            type = "movie",
            videoId = "tt1",
        )

        assertEquals(1, count)
    }

    @Test
    fun `a disabled source is not counted`() {
        val disabled = addon("https://off.example/manifest.json", enabled = false)

        assertEquals(
            0,
            pausedStreamSourceCount(
                addons = listOf(disabled),
                pausedManifestUrls = setOf(disabled.manifestUrl),
                type = "movie",
                videoId = "tt1",
            ),
        )
    }
}
