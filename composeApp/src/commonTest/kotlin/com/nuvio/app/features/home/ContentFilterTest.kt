package com.nuvio.app.features.home

import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.AddonBehaviorHints
import com.nuvio.app.features.addons.AddonResource
import com.nuvio.app.features.addons.ManagedAddon
import kotlin.test.Test
import kotlin.test.assertEquals

class ContentFilterTest {
    private fun item(id: String, type: String = "movie") =
        MetaPreview(id = id, type = type, name = id)

    private fun addon(name: String, adult: Boolean) = ManagedAddon(
        manifestUrl = "https://$name.example/manifest.json",
        manifest = AddonManifest(
            id = name,
            name = name,
            description = "",
            version = "1.0.0",
            resources = listOf(AddonResource(name = "stream", types = listOf("movie"))),
            types = listOf("movie"),
            behaviorHints = AddonBehaviorHints(adult = adult),
            transportUrl = "https://$name.example/manifest.json",
        ),
    )

    @Test
    fun `hidden titles are dropped and the rest keep their order`() {
        val items = listOf(item("tt1"), item("tt2"), item("tt3"))
        val hidden = setOf(ContentFilterRepository.hiddenKey("movie", "tt2"))

        assertEquals(listOf("tt1", "tt3"), items.excludingHidden(hidden).map { it.id })
    }

    @Test
    fun `a hidden key is per type`() {
        val hidden = setOf(ContentFilterRepository.hiddenKey("series", "tt1"))

        assertEquals(listOf("tt1"), listOf(item("tt1")).excludingHidden(hidden).map { it.id })
    }

    @Test
    fun `no hidden keys keeps the list untouched`() {
        val items = listOf(item("tt1"), item("tt2"))

        assertEquals(items, items.excludingHidden(emptySet()))
    }

    @Test
    fun `adult-flagged sources are dropped only when the switch is on`() {
        val addons = listOf(addon("safe", adult = false), addon("adult", adult = true))

        assertEquals(2, addons.excludingAdultFlagged(hideAdult = false).size)
        assertEquals(
            listOf("safe"),
            addons.excludingAdultFlagged(hideAdult = true).map { it.manifest?.id },
        )
    }

    @Test
    fun `a source without a loaded manifest is kept either way`() {
        val addons = listOf(ManagedAddon(manifestUrl = "https://pending.example/manifest.json"))

        assertEquals(1, addons.excludingAdultFlagged(hideAdult = true).size)
    }
}
