package com.nuvio.app.features.addons

/**
 * Add-ons installed automatically the first time a profile has none, so the app opens with
 * working catalogs and subtitles instead of an empty home screen.
 *
 * These are the official upstream add-ons rather than self-hosted mirrors, and the user stays in
 * control: removing one is remembered, because seeding only ever happens once per profile.
 */
internal data class DefaultAddonSource(
    val manifestUrl: String,
)

internal val DefaultAddonSources: List<DefaultAddonSource> = listOf(
    // Catalogs and metadata for movies and series; the base every Stremio-compatible app expects.
    DefaultAddonSource(manifestUrl = "https://v3-cinemeta.strem.io/manifest.json"),
    // Subtitle sources for the player.
    DefaultAddonSource(manifestUrl = "https://opensubtitles-v3.strem.io/manifest.json"),
)
