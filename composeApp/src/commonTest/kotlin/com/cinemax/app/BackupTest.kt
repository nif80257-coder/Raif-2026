package com.cinemax.app

import com.cinemax.app.addons.InstalledAddon
import com.cinemax.app.addons.StremioMetaPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupTest {

    private val favorite = StremioMetaPreview(id = "tt1", type = "movie", name = "الأسد")
    private val progress = WatchProgress(
        item = favorite,
        positionMillis = 60_000L,
        durationMillis = 600_000L,
        updatedAtEpochMillis = 1_000L,
        videoId = null,
        videoLabel = null
    )
    private val addon = InstalledAddon(
        manifestUrl = "https://example.com/manifest.json",
        id = "example",
        name = "Example",
        version = "1.0.0",
        installedAtEpochMillis = 5L
    )

    @Test
    fun aSnapshotSurvivesTheRoundTrip() {
        val backup = CinemaxBackup(
            favorites = listOf(favorite),
            progress = listOf(progress),
            watched = listOf("tt1:1:2"),
            addons = listOf(addon)
        )

        val decoded = decodeBackup(encodeBackup(backup))

        assertEquals(backup, decoded)
        assertTrue(decoded!!.summary().contains("إضافة"))
    }

    @Test
    fun brokenOrEmptyTextYieldsNothing() {
        assertNull(decodeBackup(""))
        assertNull(decodeBackup("not json"))
        assertNull(decodeBackup("""{"version":99,"favorites":[]}"""))
    }

    @Test
    fun anEmptySnapshotIsValid() {
        val decoded = decodeBackup(encodeBackup(CinemaxBackup()))
        assertEquals(CinemaxBackup(), decoded)
    }

    @Test
    fun unknownFieldsFromANewerAppAreIgnored() {
        val decoded = decodeBackup("""{"version":2,"favorites":[],"somethingNew":true}""")
        assertEquals(2, decoded?.version)
    }

    @Test
    fun theFavouriteJsonHelperMatchesThePayload() {
        assertTrue(encodeFavoritesJson(listOf(favorite)).contains("tt1"))
    }
}
