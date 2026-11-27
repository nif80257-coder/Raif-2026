package com.nuvio.app.features.plugins

import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.plugins_manifest_name_missing
import nuvio.composeapp.generated.resources.plugins_manifest_no_providers
import nuvio.composeapp.generated.resources.plugins_manifest_version_missing
import org.jetbrains.compose.resources.getString

internal object PluginManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Reads a repository manifest, refusing one that cannot be installed.
     *
     * The three refusals carry a localized sentence, and `require`'s message block cannot suspend:
     * it used to be resolved with `runBlocking`, which blocked the parsing thread for a message
     * nobody sees until the install fails. Reading the sentence here is already on a suspend path,
     * so the refusal is a plain throw and the caller sees the same `IllegalArgumentException`.
     */
    suspend fun parse(payload: String): PluginManifest {
        val manifest = json.decodeFromString<PluginManifest>(payload)
        if (manifest.name.isBlank()) {
            throw IllegalArgumentException(getString(Res.string.plugins_manifest_name_missing))
        }
        if (manifest.version.isBlank()) {
            throw IllegalArgumentException(getString(Res.string.plugins_manifest_version_missing))
        }
        if (manifest.scrapers.isEmpty()) {
            throw IllegalArgumentException(getString(Res.string.plugins_manifest_no_providers))
        }
        return manifest
    }
}
