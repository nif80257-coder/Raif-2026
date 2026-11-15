package com.nuvio.app.core.ui

import platform.Foundation.NSUserDefaults

internal actual object RenderPreferencesStorage {
    private const val reducedEffectsKey = "reduced_effects"

    actual fun loadReducedEffects(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        if (defaults.objectForKey(reducedEffectsKey) == null) return null
        return defaults.boolForKey(reducedEffectsKey)
    }

    actual fun saveReducedEffects(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = reducedEffectsKey)
    }
}
