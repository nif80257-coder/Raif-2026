package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleOrderingTest {
    private fun subtitle(language: String, display: String) =
        AddonSubtitle(
            id = "$language-$display",
            url = "https://example.com/$language-$display.srt",
            language = language,
            display = display,
        )

    @Test
    fun `arabic tracks come before the rest`() {
        val english = subtitle("en", "English — OpenSubtitles")
        val arabic = subtitle("ar", "العربية — SubDL")
        val french = subtitle("fr", "Français — SubSource")

        // Arabic first, then the rest by display name: "English…" before "Français…".
        assertEquals(
            listOf(arabic, english, french),
            orderedAddonSubtitles(listOf(english, french, arabic)),
        )
    }

    @Test
    fun `a list of one is returned untouched`() {
        val only = subtitle("en", "English")

        assertEquals(listOf(only), orderedAddonSubtitles(listOf(only)))
    }

    @Test
    fun `the order does not depend on arrival order`() {
        val arabicA = subtitle("ar", "العربية — SubDL")
        val arabicB = subtitle("ar", "العربية — SubSource")
        val english = subtitle("en", "English — OpenSubtitles")

        assertEquals(
            orderedAddonSubtitles(listOf(english, arabicB, arabicA)),
            orderedAddonSubtitles(listOf(arabicA, english, arabicB)),
        )
    }

    @Test
    fun `an empty list stays empty`() {
        assertEquals(emptyList(), orderedAddonSubtitles(emptyList()))
    }
}
