package com.nuvio.app.features.home

internal expect object HomeCatalogSettingsStorage {
    fun loadPayload(): String?

    /** The payload another profile stored, so one profile's setup can be copied onto this one. */
    fun loadPayload(profileId: Int): String?

    fun savePayload(payload: String)
}
