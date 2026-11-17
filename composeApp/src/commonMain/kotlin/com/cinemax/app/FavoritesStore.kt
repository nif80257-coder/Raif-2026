package com.cinemax.app

import com.cinemax.app.addons.StremioMetaPreview
import com.cinemax.app.core.distinctTitles
import com.russhwolf.settings.Settings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A metadata-backed watchlist that survives addon and catalog changes. */
class FavoritesStore(
    private val settings: Settings = Settings()
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<StremioMetaPreview> = runCatching {
        json.decodeFromString(ListSerializer(StremioMetaPreview.serializer()), settings.getString(KEY, "[]"))
    }.getOrDefault(emptyList()).let { stored ->
        // One entry per title, even when two catalogs saved it under different ids.
        val unique = distinctTitles(stored)
        if (unique.size != stored.size) save(unique)
        unique
    }

    fun save(items: List<StremioMetaPreview>) {
        settings.putString(KEY, json.encodeToString(ListSerializer(StremioMetaPreview.serializer()), distinctTitles(items)))
    }

    private companion object { const val KEY = "cinemax.library.favorites.metadata.v2" }
}
