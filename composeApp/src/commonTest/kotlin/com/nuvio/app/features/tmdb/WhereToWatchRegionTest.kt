package com.nuvio.app.features.tmdb

import kotlin.test.Test
import kotlin.test.assertEquals

class WhereToWatchRegionTest {
    @Test
    fun aRegionInTheLanguageWins() {
        assertEquals("BR", watchRegion("pt-BR"))
        assertEquals("US", watchRegion("en-US"))
        assertEquals("SA", watchRegion("ar-SA"))
    }

    @Test
    fun aLanguageAloneFallsBackToItsDefaultRegion() {
        assertEquals("SA", watchRegion("ar"))
        assertEquals("PT", watchRegion("pt"))
        assertEquals("ES", watchRegion("es"))
        assertEquals("FR", watchRegion("fr"))
    }

    @Test
    fun anUnknownLanguageAsksTmdbAboutTheUnitedStates() {
        assertEquals("US", watchRegion("xx"))
        assertEquals("US", watchRegion(null))
    }
}
