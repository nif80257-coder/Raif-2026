package com.nuvio.app.features.addons

import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.generic_addon
import org.jetbrains.compose.resources.getString

data class AddonManifest(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val logoUrl: String? = null,
    /** The `background` image some manifests ship for their card, resolved against the manifest. */
    val backgroundUrl: String? = null,
    val resources: List<AddonResource>,
    val types: List<String>,
    val idPrefixes: List<String> = emptyList(),
    val catalogs: List<AddonCatalog> = emptyList(),
    val behaviorHints: AddonBehaviorHints = AddonBehaviorHints(),
    val transportUrl: String,
    /** Catalogs of other add-ons this source can list, from the `addon_catalog` resource. */
    val addonCatalogs: List<AddonCatalogRef> = emptyList(),
    /** Inputs a source declares so it can be configured without opening its website. */
    val configFields: List<AddonConfigField> = emptyList(),
)

/** One entry of the manifest's `addonCatalogs`: an add-on directory this source serves. */
data class AddonCatalogRef(
    val type: String,
    val id: String,
    val name: String,
)

/**
 * A field from the manifest's `config` array. Stremio's newer manifests use it to describe the
 * settings a source needs; the app can then build the configured manifest URL itself.
 */
data class AddonConfigField(
    val key: String,
    val type: String? = null,
    val title: String? = null,
    val description: String? = null,
    val default: String? = null,
    val options: List<String> = emptyList(),
    val required: Boolean = false,
) {
    val isChoice: Boolean
        get() = options.isNotEmpty()

    /**
     * Sources that need an API token or a password declare it in `config[]`; the value is typed
     * into a masked field so it is not shown on screen while it is entered.
     */
    val isSecret: Boolean
        get() = type?.equals("password", ignoreCase = true) == true ||
            key.lowercase().let { name -> SECRET_CONFIG_KEY_HINTS.any(name::contains) }
}

private val SECRET_CONFIG_KEY_HINTS = listOf("token", "apikey", "api_key", "password", "secret")

/** True when this source serves a directory of other add-ons through `addon_catalog`. */
fun AddonManifest.servesAddonDirectory(): Boolean =
    addonCatalogs.isNotEmpty() &&
        resources.any { resource -> resource.name.equals("addon_catalog", ignoreCase = true) }

data class AddonResource(
    val name: String,
    val types: List<String>,
    val idPrefixes: List<String> = emptyList(),
)

fun AddonManifest.supportsResource(name: String, type: String, id: String): Boolean =
    resources.any { resource ->
        resource.name == name &&
            resource.types.contains(type) &&
            (resource.idPrefixes.isEmpty() || resource.idPrefixes.any { id.startsWith(it) })
    }

data class AddonCatalog(
    val type: String,
    val id: String,
    val name: String,
    val extra: List<AddonExtraProperty> = emptyList(),
)

data class AddonExtraProperty(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String> = emptyList(),
    val optionsLimit: Int? = null,
)

data class AddonBehaviorHints(
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false,
    val adult: Boolean = false,
    val p2p: Boolean = false,
)

data class ManagedAddon(
    val manifestUrl: String,
    val manifest: AddonManifest? = null,
    val userSetName: String? = null,
    val enabled: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
) {
    val isActive: Boolean
        get() = enabled && manifest != null

    val displayTitle: String
        get() = userSetName?.takeIf { it.isNotBlank() && it != manifest?.name }
            ?: manifest?.name
            ?: manifestUrl.substringBefore("?").substringAfterLast("/").ifBlank {
                runBlocking { getString(Res.string.generic_addon) }
            }
}

data class AddonsUiState(
    val addons: List<ManagedAddon> = emptyList(),
)

data class AddonOverview(
    val totalAddons: Int,
    val activeAddons: Int,
    val totalCatalogs: Int,
)

internal fun List<ManagedAddon>.toOverview(): AddonOverview =
    AddonOverview(
        totalAddons = size,
        activeAddons = count { it.isActive },
        totalCatalogs = filter { it.enabled }.sumOf { it.manifest?.catalogs?.size ?: 0 },
    )

internal fun List<ManagedAddon>.enabledAddons(): List<ManagedAddon> =
    filter { it.enabled }

internal fun List<ManagedAddon>.hasPendingEnabledManifests(): Boolean =
    any { addon -> addon.enabled && addon.isRefreshing }

internal fun List<ManagedAddon>.isWaitingForFirstEnabledManifest(): Boolean {
    val enabledAddons = enabledAddons()
    return enabledAddons.isNotEmpty() &&
        enabledAddons.none { addon -> addon.manifest != null } &&
        enabledAddons.any { addon -> addon.isRefreshing }
}

internal fun List<ManagedAddon>.firstEnabledManifestError(): String? =
    asSequence()
        .filter { addon -> addon.enabled && addon.manifest == null }
        .mapNotNull { addon -> addon.errorMessage?.takeIf(String::isNotBlank) }
        .firstOrNull()

/**
 * The name to match on without resolving a localized fallback: filtering runs for every add-on
 * on every keystroke, and [displayTitle]'s `runBlocking` lookup would block the frame each time.
 */
internal fun ManagedAddon.searchableTitle(): String =
    userSetName?.takeIf { it.isNotBlank() && it != manifest?.name }
        ?: manifest?.name
        ?: manifestUrl.substringBefore("?").substringAfterLast("/")

/**
 * The installed add-ons whose name or manifest URL contains [query], ignoring case.
 *
 * A blank query returns the list unchanged, so the search field starts out showing every
 * installed add-on.
 */
internal fun List<ManagedAddon>.matchingQuery(query: String): List<ManagedAddon> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return this
    return filter { addon ->
        addon.searchableTitle().lowercase().contains(needle) ||
            addon.manifestUrl.lowercase().contains(needle)
    }
}

/**
 * The opening of a description on one line, for a compact add-on row: whitespace collapsed and
 * cut at a word boundary rather than mid-word.
 */
internal fun String.summaryLine(maxChars: Int = 120): String {
    val flat = trim().replace(Regex("\\s+"), " ")
    if (flat.length <= maxChars) return flat
    val cut = flat.take(maxChars)
    val boundary = cut.substringBeforeLast(' ', cut).trimEnd().trimEnd('.', ',', ';', ':')
    return "$boundary…"
}

/**
 * The repository a source belongs to: the host its manifest lives on, which is what the add-on
 * screen groups the list by so one repository can be folded away as a whole.
 */
internal fun ManagedAddon.repositoryHost(): String = manifestUrl.repositoryHost()

internal fun String.repositoryHost(): String {
    val withoutScheme = substringAfter("://", "")
    if (withoutScheme.isBlank()) return ""
    return withoutScheme
        .substringBefore('/')
        .substringBefore('?')
        .removePrefix("www.")
        .lowercase()
}

sealed interface AddAddonResult {
    data class Success(val manifest: AddonManifest) : AddAddonResult
    data class Error(val message: String) : AddAddonResult
}
