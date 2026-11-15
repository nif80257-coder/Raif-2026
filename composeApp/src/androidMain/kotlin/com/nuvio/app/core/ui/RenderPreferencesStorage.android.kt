package com.nuvio.app.core.ui

import android.content.Context
import android.content.SharedPreferences

internal actual object RenderPreferencesStorage {
    private const val preferencesName = "nuvio_render_preferences"
    private const val reducedEffectsKey = "reduced_effects"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadReducedEffects(): Boolean? =
        preferences?.let { if (it.contains(reducedEffectsKey)) it.getBoolean(reducedEffectsKey, false) else null }

    actual fun saveReducedEffects(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(reducedEffectsKey, enabled)
            ?.apply()
    }
}
