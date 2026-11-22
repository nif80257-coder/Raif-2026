package com.nuvio.app.features.streams

import com.nuvio.app.features.addons.ManagedAddon

/**
 * How many installed sources a paused failure streak keeps out of this title's load.
 *
 * The streams sheet simply arrives thinner while a source is paused, so it says how many are
 * missing and offers one tap that clears the pause and asks again. Only sources that could serve
 * this exact title are counted: a paused subtitle-only add-on says nothing about the streams list.
 */
internal fun pausedStreamSourceCount(
    addons: List<ManagedAddon>,
    pausedManifestUrls: Set<String>,
    type: String,
    videoId: String,
): Int {
    if (pausedManifestUrls.isEmpty()) return 0
    return addons.count { addon ->
        addon.enabled &&
            addon.manifestUrl in pausedManifestUrls &&
            addon.manifest?.supportsStream(type, videoId) == true
    }
}
