package com.cinemax.app.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryFilterTest {

    @Test
    fun anEmptyOrBlankQueryMatchesEverything() {
        assertTrue(matchesLibraryQuery("Batman", ""))
        assertTrue(matchesLibraryQuery("Batman", "   "))
    }

    @Test
    fun aPartOfTheNameMatchesIgnoringCase() {
        assertTrue(matchesLibraryQuery("The Dark Knight", "dark"))
        assertTrue(matchesLibraryQuery("The Dark Knight", "KNIGHT"))
    }

    @Test
    fun aNameThatDoesNotContainTheQueryIsHidden() {
        assertFalse(matchesLibraryQuery("Inception", "batman"))
    }

    @Test
    fun arabicSpellingVariantsStillMatch() {
        assertTrue(matchesLibraryQuery("الأسد الملك", "الاسد"))
        assertTrue(matchesLibraryQuery("مصطفى", "مصطفي"))
    }
}
