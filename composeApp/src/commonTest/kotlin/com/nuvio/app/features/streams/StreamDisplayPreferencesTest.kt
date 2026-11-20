package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamDisplayPreferencesTest {

    @Test
    fun everyLinkIsShownUntilTheSwitchIsTurnedOn() {
        assertFalse(mergeDuplicatesFromStoredValue(null))
        assertFalse(mergeDuplicatesFromStoredValue(""))
        assertFalse(mergeDuplicatesFromStoredValue("0"))
        assertFalse(mergeDuplicatesFromStoredValue("true"))
    }

    @Test
    fun onlyAnExplicitOneMergesTheList() {
        assertTrue(mergeDuplicatesFromStoredValue("1"))
    }
}
