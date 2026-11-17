package com.cinemax.app.addons

import kotlinx.serialization.Serializable

@Serializable
data class InstalledAddon(
    val manifestUrl: String,
    val id: String,
    val name: String,
    val version: String,
    val description: String? = null,
    val installedAtEpochMillis: Long,
    /** Resources declared by the manifest; empty for addons saved before this was stored. */
    val resources: List<String> = emptyList(),
    /** Content types declared by the manifest ("movie", "series", ...). */
    val types: List<String> = emptyList(),
    /** Id prefixes the addon accepts, e.g. ["tt"] for IMDb-based addons. */
    val idPrefixes: List<String> = emptyList(),
    /** Catalogs declared by the manifest, used for cross-addon search. */
    val catalogs: List<StremioCatalog> = emptyList(),
    /** True when the manifest advertises a configure page for this addon. */
    val configurable: Boolean = false
)
