package com.nuvio.app.features.settings

import com.nuvio.app.features.addons.AddAddonResult
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.servers.ServerRepository

internal enum class SourcesTransferImportMode {
    MERGE,
    REPLACE,
}

internal enum class SourcesTransferSkipReason {
    SERVER_REQUIRES_SIGN_IN,
    INVALID_ENTRY,
}

internal data class SourcesTransferSkipped(
    val name: String,
    val url: String,
    val reason: SourcesTransferSkipReason,
)

internal data class SourcesTransferFailed(
    val name: String,
    val url: String,
    val message: String,
)

internal data class SourcesTransferImportReport(
    val imported: List<SourcesTransferEntry> = emptyList(),
    val skipped: List<SourcesTransferSkipped> = emptyList(),
    val failed: List<SourcesTransferFailed> = emptyList(),
)

internal object SourcesTransferRepository {

    fun buildDocument(exportedAt: String? = null): SourcesTransferDocument {
        AddonRepository.initialize()
        val addonEntries = AddonRepository.uiState.value.addons
            .distinctBy { addon -> addon.manifestUrl }
            .mapIndexed { index, addon ->
                SourcesTransferEntry(
                    type = SOURCE_TYPE_ADDON,
                    name = addon.userSetName?.takeIf(String::isNotBlank)
                        ?: addon.manifest?.name?.takeIf(String::isNotBlank)
                        ?: addon.manifestUrl,
                    url = addon.manifestUrl,
                    enabled = addon.enabled,
                    order = index,
                )
            }

        ServerRepository.ensureLoaded()
        val serverEntries = ServerRepository.uiState.value.connections
            .mapIndexed { index, connection ->
                SourcesTransferEntry(
                    type = SOURCE_TYPE_SERVER,
                    name = connection.name,
                    url = connection.address,
                    enabled = connection.enabled,
                    order = addonEntries.size + index,
                )
            }

        return SourcesTransferDocument(
            version = SOURCES_TRANSFER_VERSION,
            exportedAt = exportedAt,
            sources = addonEntries + serverEntries,
        )
    }

    fun analyze(document: SourcesTransferDocument): SourcesTransferAnalysis {
        AddonRepository.initialize()
        ServerRepository.ensureLoaded()
        return analyzeSourcesTransfer(
            document = document,
            installedAddonUrls = AddonRepository.uiState.value.addons.map { it.manifestUrl },
            connectedServerAddresses = ServerRepository.uiState.value.connections.map { it.address },
        )
    }

    fun saveBackup(document: SourcesTransferDocument) {
        SourcesTransferStorage.saveBackupPayload(SourcesTransferCodec.encode(document))
    }

    fun storedBackup(): SourcesTransferParseResult? =
        SourcesTransferStorage.loadBackupPayload()?.let(SourcesTransferCodec::decode)

    fun hasBackup(): Boolean = SourcesTransferStorage.loadBackupPayload() != null

    suspend fun apply(
        analysis: SourcesTransferAnalysis,
        mode: SourcesTransferImportMode,
        keepBackup: Boolean = false,
    ): SourcesTransferImportReport {
        if (!keepBackup) {
            saveBackup(buildDocument())
        }

        val imported = mutableListOf<SourcesTransferEntry>()
        val skipped = mutableListOf<SourcesTransferSkipped>()
        val failed = mutableListOf<SourcesTransferFailed>()

        if (mode == SourcesTransferImportMode.REPLACE) {
            AddonRepository.uiState.value.addons
                .map { addon -> addon.manifestUrl }
                .distinct()
                .forEach { url -> AddonRepository.removeAddon(url) }
        }

        analysis.servers.forEach { entry ->
            skipped += SourcesTransferSkipped(
                name = entry.name,
                url = entry.url,
                reason = SourcesTransferSkipReason.SERVER_REQUIRES_SIGN_IN,
            )
        }

        analysis.invalid.forEach { entry ->
            skipped += SourcesTransferSkipped(
                name = entry.name,
                url = entry.url,
                reason = SourcesTransferSkipReason.INVALID_ENTRY,
            )
        }

        analysis.addons.forEach { entry ->
            when (val result = AddonRepository.addAddon(entry.url)) {
                is AddAddonResult.Success -> {
                    val manifestUrl = result.manifest.transportUrl.ifBlank { entry.url }
                    if (!entry.enabled) {
                        AddonRepository.setAddonEnabled(manifestUrl, false)
                    }
                    imported += entry.copy(url = manifestUrl)
                }

                is AddAddonResult.Error -> failed += SourcesTransferFailed(
                    name = entry.name,
                    url = entry.url,
                    message = result.message,
                )
            }
        }

        return SourcesTransferImportReport(
            imported = imported,
            skipped = skipped,
            failed = failed,
        )
    }
}
