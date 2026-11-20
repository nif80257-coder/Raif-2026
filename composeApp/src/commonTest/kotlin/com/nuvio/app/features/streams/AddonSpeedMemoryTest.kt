package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddonSpeedMemoryTest {

    @Test
    fun aRecordWithoutAResultCountReadsAsUnknown() {
        assertNull(parseResultCount(null))
        assertNull(parseResultCount(""))
        assertNull(parseResultCount("640|1700000000000"))
        assertNull(parseResultCount("640|1700000000000|nope"))
        assertNull(parseResultCount("640|1700000000000|-3"))
    }

    @Test
    fun theResultCountIsTheThirdFieldAndSurvivesZero() {
        assertEquals(12, parseResultCount("640|1700000000000|12"))
        assertEquals(0, parseResultCount("900|1700000000000|0"))
    }

    @Test
    fun theSpeedStaysTheFirstFieldWhateverFollowsIt() {
        val encoded = "1234|1700000000000|7"

        assertEquals(1234L, encoded.substringBefore('|').toLongOrNull())
    }
}
