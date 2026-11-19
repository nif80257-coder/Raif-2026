package com.nuvio.app.features.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HiddenTitleImportTest {
    @Test
    fun readsWrappedMetaEntries() {
        val payload = """[{"meta":{"id":"tt1","type":"series"}},{"meta":{"id":"tt2"}}]"""

        assertEquals(
            setOf("series:tt1", "movie:tt2", "series:tt2"),
            HiddenTitleImportParser.parse(payload),
        )
    }

    @Test
    fun readsFlatEntriesAndPlainStrings() {
        val payload = """{"metas":[{"id":"tt3","type":"movie"},"tt4","series:tt5"]}"""

        assertEquals(
            setOf("movie:tt3", "movie:tt4", "series:tt4", "series:tt5"),
            HiddenTitleImportParser.parse(payload),
        )
    }

    @Test
    fun ignoresEntriesWithoutAnIdAndBrokenJson() {
        assertTrue(HiddenTitleImportParser.parse("""[{"name":"x"}]""").isEmpty())
        assertTrue(HiddenTitleImportParser.parse("not json").isEmpty())
        assertTrue(HiddenTitleImportParser.parse("[]").isEmpty())
    }
}
