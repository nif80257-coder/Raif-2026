package com.nuvio.app.features.search

import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchTextMatchTest {

    private data class Title(val name: String, val id: String)

    private val catalogue = listOf(
        Title(name = "Batman Begins", id = "begins"),
        Title(name = "The Batman", id = "the-batman"),
        Title(name = "Spider-Man", id = "spider"),
        Title(name = "Zodiac", id = "zodiac"),
    )

    @Test
    fun unrelatedTitlesAreDroppedOnceTheQueryMatchesSomething() {
        val results = rankByWordMatch(catalogue, "batman") { title -> title.name }

        assertEquals(listOf("begins", "the-batman"), results.map { it.id })
    }

    @Test
    fun aCatalogueWithNoMatchIsKeptAsTheSourceReturnedIt() {
        val results = rankByWordMatch(catalogue, "interstellar") { title -> title.name }

        assertEquals(catalogue, results)
    }

    @Test
    fun everyWordOfTheQueryHasToBeInTheTitle() {
        val results = rankByWordMatch(catalogue, "batman begins") { title -> title.name }

        assertEquals(listOf("begins"), results.map { it.id })
    }

    @Test
    fun arabicLetterFormsAndDiacriticsAreFoldedBeforeMatching() {
        val arabic = listOf(
            Title(name = "الرسالة", id = "message"),
            Title(name = "الفيل الأزرق", id = "blue-elephant"),
        )

        val results = rankByWordMatch(arabic, "الرساله") { title -> title.name }

        assertEquals(listOf("message"), results.map { it.id })
    }

    @Test
    fun aTitleListedByTwoCataloguesIsDrawnOnce() {
        val sections = listOf(
            section(
                key = "cinemeta",
                items = listOf(meta("tt0372784", "Batman Begins"), meta("tt1877830", "The Batman")),
            ),
            section(
                key = "arabcinemeta",
                items = listOf(meta("tt0372784", "Batman Begins"), meta("tt0111161", "Shawshank")),
            ),
            section(key = "empty-after-dedupe", items = listOf(meta("tt0372784", "Batman Begins"))),
        )

        val deduped = sections.deduplicatedAcrossSections()

        assertEquals(listOf("cinemeta", "arabcinemeta"), deduped.map { it.key })
        assertEquals(
            listOf("tt0372784", "tt1877830"),
            deduped.first().items.map { it.id },
        )
        assertEquals(listOf("tt0111161"), deduped.last().items.map { it.id })
    }

    private fun section(key: String, items: List<MetaPreview>): HomeCatalogSection = HomeCatalogSection(
        key = key,
        title = key,
        subtitle = "",
        addonName = key,
        target = CatalogTarget.Addon(
            manifestUrl = "https://$key.example/manifest.json",
            contentType = "movie",
            catalogId = "search",
        ),
        items = items,
    )

    private fun meta(id: String, name: String): MetaPreview = MetaPreview(
        id = id,
        type = "movie",
        name = name,
    )
}
