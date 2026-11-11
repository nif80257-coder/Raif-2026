package com.nuvio.app.features.settings

import com.nuvio.app.features.addons.AddonManifest
import kotlinx.serialization.Serializable

internal const val RaifUpdatesDailyIntervalMs = 24L * 60L * 60L * 1000L

@Serializable
internal data class AddonManifestSnapshot(
    val version: String = "",
    val catalogs: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val resources: List<String> = emptyList(),
)

internal fun AddonManifest.toManifestSnapshot(): AddonManifestSnapshot = AddonManifestSnapshot(
    version = version,
    catalogs = catalogs.map { catalog -> catalog.type + "/" + catalog.id }.sorted(),
    types = types.map(String::lowercase).distinct().sorted(),
    resources = resources
        .map { resource -> resource.name.lowercase() + ":" + resource.types.map(String::lowercase).sorted().joinToString("+") }
        .distinct()
        .sorted(),
)

internal data class AddonSnapshotDiff(
    val addedCatalogs: List<String> = emptyList(),
    val removedCatalogs: List<String> = emptyList(),
    val addedTypes: List<String> = emptyList(),
    val removedTypes: List<String> = emptyList(),
    val addedResources: List<String> = emptyList(),
    val removedResources: List<String> = emptyList(),
) {
    val hasChanges: Boolean
        get() = addedCatalogs.isNotEmpty() ||
            removedCatalogs.isNotEmpty() ||
            addedTypes.isNotEmpty() ||
            removedTypes.isNotEmpty() ||
            addedResources.isNotEmpty() ||
            removedResources.isNotEmpty()
}

internal fun diffManifestSnapshots(
    previous: AddonManifestSnapshot?,
    current: AddonManifestSnapshot,
): AddonSnapshotDiff {
    val previousCatalogs = previous?.catalogs.orEmpty().toSet()
    val previousTypes = previous?.types.orEmpty().toSet()
    val previousResources = previous?.resources.orEmpty().toSet()
    return AddonSnapshotDiff(
        addedCatalogs = (current.catalogs.toSet() - previousCatalogs).sorted(),
        removedCatalogs = (previousCatalogs - current.catalogs.toSet()).sorted(),
        addedTypes = (current.types.toSet() - previousTypes).sorted(),
        removedTypes = (previousTypes - current.types.toSet()).sorted(),
        addedResources = (current.resources.toSet() - previousResources).sorted(),
        removedResources = (previousResources - current.resources.toSet()).sorted(),
    )
}

internal enum class AddonUpdateState {
    NOT_CHECKED,
    CHANGED,
    UNCHANGED,
    ERROR,
}

internal data class AddonUpdateResult(
    val manifestUrl: String,
    val name: String,
    val state: AddonUpdateState,
    val previousVersion: String? = null,
    val currentVersion: String? = null,
    val diff: AddonSnapshotDiff = AddonSnapshotDiff(),
    val error: String? = null,
)

internal data class RaifAppReleaseDocument(
    val version: String,
    val releaseDate: String? = null,
    val notes: String? = null,
    val downloadUrl: String? = null,
)

internal data class RaifAppUpdateState(
    val installedVersion: String,
    val latest: RaifAppReleaseDocument? = null,
    val updateAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)
