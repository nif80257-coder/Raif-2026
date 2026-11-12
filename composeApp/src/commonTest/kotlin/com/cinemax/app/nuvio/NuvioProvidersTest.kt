package com.cinemax.app.nuvio

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class NuvioProvidersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesProviderRepositories() {
        val repository = json.decodeFromString<NuvioProviderRepository>(
            """
            {
              "name": "Example Repo",
              "version": "1.0.0",
              "scrapers": [
                {
                  "id": "example",
                  "name": "Example Provider",
                  "version": "1.0.2",
                  "filename": "providers/example.js",
                  "supportedTypes": ["movie", "tv"],
                  "enabled": true,
                  "contentLanguage": ["ar"],
                  "logo": "https://example.com/logo.png"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("Example Repo", repository.name)
        val scraper = repository.scrapers.single()
        assertEquals("providers/example.js", scraper.filename)
        assertEquals(listOf("movie", "tv"), scraper.supportedTypes)
        assertEquals(listOf("ar"), scraper.contentLanguage)
        assertTrue(scraper.enabled)
    }

    @Test
    fun resolvesRepositoryAndProviderUrls() {
        assertEquals(
            "https://example.com/repo/manifest.json",
            NuvioUrls.manifestUrl("https://example.com/repo")
        )
        assertEquals(
            "https://example.com/repo/manifest.json",
            NuvioUrls.manifestUrl("https://example.com/repo/manifest.json")
        )
        assertNull(NuvioUrls.manifestUrl("http://example.com/repo"))
        assertEquals(
            "https://example.com/repo/providers/example.js",
            NuvioUrls.resolve("https://example.com/repo/manifest.json", "providers/example.js")
        )
        assertNull(NuvioUrls.resolve("http://example.com/repo/manifest.json", "providers/example.js"))
    }

    @Test
    fun mapsItemIdentifiersToProviderArguments() {
        assertEquals("603", NuvioMediaIds.explicitTmdbId("tmdb:603"))
        assertEquals("603", NuvioMediaIds.explicitTmdbId("603"))
        assertNull(NuvioMediaIds.explicitTmdbId("tt0111161"))
        assertEquals("tv", NuvioMediaIds.mediaType("series"))
        assertEquals("tv", NuvioMediaIds.mediaType("tv"))
        assertEquals("movie", NuvioMediaIds.mediaType("movie"))
    }

    @Test
    fun providersOnlyServeDeclaredMediaTypes() {
        val entry = NuvioProviderEntry(
            providerKey = "key",
            repositoryName = "Repo",
            repository = InstalledNuvioRepository(
                manifestUrl = "https://example.com/repo/manifest.json",
                name = "Repo",
                installedAtEpochMillis = 0L
            ),
            descriptor = NuvioScraperDescriptor(id = "provider", name = "Provider", filename = "provider.js")
        )

        assertTrue(entry.supportsType("tv"))
        assertTrue(entry.supportsType("movie"))
        assertFalse(entry.supportsType("channel"))
    }

    @Test
    fun persistsRepositoriesProvidersAndTmdbIds() {
        val settings = MapSettings()
        val store = NuvioProviderStore(settings)
        val repository = InstalledNuvioRepository(
            manifestUrl = "https://example.com/repo/manifest.json",
            name = "Repo",
            version = "1.0.0",
            scrapers = listOf(
                NuvioScraperDescriptor(id = "provider", name = "Provider", filename = "provider.js")
            ),
            installedAtEpochMillis = 42L
        )

        store.saveRepositories(listOf(repository))
        assertEquals(listOf(repository), NuvioProviderStore(settings).loadRepositories())

        store.saveEnabledProviders(setOf("b", "a"))
        assertEquals(setOf("a", "b"), NuvioProviderStore(settings).loadEnabledProviders())

        store.saveTmdbId("tt1", "42")
        assertEquals("42", NuvioProviderStore(settings).loadTmdbId("tt1"))
    }
}
