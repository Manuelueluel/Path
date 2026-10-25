package it.traccia.feature.offlinemaps.catalog

import kotlinx.serialization.Serializable

/** The list of downloadable region packs, published next to the packs by the pipeline. */
@Serializable
data class Catalog(
    val formatVersion: Int,
    val regions: List<CatalogRegion>,
)

@Serializable
data class CatalogRegion(
    val id: String,
    val name: String,
    /** Data version (ISO date): a greater version than the installed one is an update. */
    val version: String,
    /** west, south, east, north */
    val bbox: List<Double>,
    val files: List<CatalogFile>,
) {
    val totalBytes: Long get() = files.sumOf { it.size }
}

@Serializable
data class CatalogFile(
    /** basemap, trails or dem: matches the routed tile layer. */
    val type: String,
    val url: String,
    val size: Long,
    val sha256: String,
    /** Name to save the file with, when it differs from the published one (BRouter segments). */
    val target: String? = null,
) {
    val fileName: String get() = url.substringAfterLast('/')

    /** Routing segments are shared by neighbouring regions: one copy for all of them. */
    val isShared: Boolean get() = type == TYPE_ROUTING

    val localName: String get() = target ?: fileName

    /** Key of the installed file: unique within a region (a region can have several segments). */
    fun fileKey(): String = if (isShared) "$TYPE_ROUTING:$localName" else type

    companion object {
        const val TYPE_ROUTING = "routing"
    }
}
