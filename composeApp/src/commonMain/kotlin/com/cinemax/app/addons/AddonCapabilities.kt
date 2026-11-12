package com.cinemax.app.addons

/**
 * Decides which installed addon can answer a request.
 *
 * Stremio addons declare the resources and types they serve, and stream addons
 * usually serve only some id prefixes. A catalog addon therefore does not have
 * to be the addon that returns sources. Addons installed before these fields
 * were persisted have empty lists and stay usable for any request.
 */
object AddonCapabilities {
    fun supports(addon: InstalledAddon, resource: String, type: String): Boolean {
        if (addon.resources.isNotEmpty() && resource !in addon.resources) return false
        if (addon.types.isNotEmpty() && type !in addon.types) return false
        return true
    }

    fun supportsMeta(addon: InstalledAddon, type: String): Boolean =
        supports(addon, "meta", type)

    fun supportsStreams(addon: InstalledAddon, type: String, mediaId: String): Boolean =
        supports(addon, "stream", type) &&
            (addon.idPrefixes.isEmpty() || addon.idPrefixes.any { mediaId.startsWith(it) })
}
