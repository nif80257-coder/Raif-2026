package com.nuvio.app.features.search

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
}
