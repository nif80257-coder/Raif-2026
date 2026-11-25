package com.nuvio.app.features.plugins

/**
 * Scrapers inside the shipped Nuvio provider repository that this app does not run.
 *
 * The repository itself is the one the app bundles — `tapframe/nuvio-providers`, the Nuvio
 * project's own — so it is installed whole. Its manifest also lists anime sources, and anime is
 * outside what this app carries, so those five are dropped when the repository is read instead of
 * being installed disabled: a disabled scraper still draws a row in settings and a switch the user
 * has to understand.
 *
 * Matched on the scraper's own id, which is what the manifest calls it. Anything else in that
 * repository, now or after an upstream update, is installed as it comes.
 */
internal val ExcludedProviderScraperIds: Set<String> = setOf(
    "animepahe",
    "hianime",
    "kurage",
    "anizone",
    "vidnest-anime",
)

/**
 * True when a scraper id from the bundled repository must not be installed.
 *
 * Ids stored by the app carry the repository URL as a prefix (`<manifest>:<scraper>`), so the
 * comparison looks at the part after the last colon.
 */
internal fun isExcludedProviderScraper(scraperId: String): Boolean {
    val bareId = scraperId.substringAfterLast(':')
    if (bareId in ExcludedProviderScraperIds) return true
    // A provider that calls itself anime is anime, whatever its id is spelled like upstream.
    return bareId.contains("anime", ignoreCase = true)
}
