package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals

class AddonSearchQueryTest {
    private val addons = listOf(
        ManagedAddon(
            manifestUrl = "https://stremio.example/manifest.json",
            manifest = manifest(name = "Cinemeta"),
        ),
        ManagedAddon(
            manifestUrl = "https://torrentio.example/manifest.json",
            manifest = manifest(name = "Torrentio"),
        ),
    )

    @Test
    fun `a blank query keeps every add-on`() {
        assertEquals(addons, addons.matchingQuery(""))
        assertEquals(addons, addons.matchingQuery("   "))
    }

    @Test
    fun `the name matches regardless of case`() {
        assertEquals(listOf(addons[0]), addons.matchingQuery("cinemeta"))
        assertEquals(listOf(addons[0]), addons.matchingQuery("CINEMETA"))
    }

    @Test
    fun `the manifest host matches too`() {
        assertEquals(listOf(addons[1]), addons.matchingQuery("torrentio"))
    }

    @Test
    fun `a name the user set wins over the manifest name`() {
        val renamed = addons[0].copy(userSetName = "Movies")

        assertEquals(
            listOf(renamed),
            addons.toMutableList().apply { this[0] = renamed }.matchingQuery("movies"),
        )
    }

    @Test
    fun `an add-on whose manifest never loaded is still matched by its url`() {
        val broken = ManagedAddon(manifestUrl = "https://kodi.example/manifest.json")

        assertEquals(listOf(broken), listOf(broken).matchingQuery("kodi"))
    }

    @Test
    fun `a query that matches nothing returns an empty list`() {
        assertEquals(emptyList(), addons.matchingQuery("nothing-here"))
    }

    private fun manifest(name: String): AddonManifest =
        AddonManifest(
            id = name.lowercase(),
            name = name,
            description = "",
            version = "1.0.0",
            resources = emptyList(),
            types = emptyList(),
            transportUrl = "",
        )
}
