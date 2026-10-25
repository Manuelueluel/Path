package it.traccia.feature.offlinemaps

import it.traccia.feature.offlinemaps.catalog.Catalog
import it.traccia.feature.offlinemaps.catalog.CatalogFile
import it.traccia.feature.offlinemaps.catalog.CatalogRegion
import it.traccia.feature.offlinemaps.installed.InstalledFile
import it.traccia.feature.offlinemaps.installed.InstalledRegion

internal fun catalogRegion(
    id: String,
    version: String = "2026-10-24",
    size: Long = 100,
) = CatalogRegion(
    id = id,
    name = id.replaceFirstChar { it.uppercase() },
    version = version,
    bbox = listOf(9.0, 45.0, 10.0, 46.0),
    files = listOf(CatalogFile("basemap", "https://example.org/$id-basemap-$version.mbtiles", size, "abc")),
)

internal fun catalogOf(vararg regions: CatalogRegion) = Catalog(formatVersion = 1, regions = regions.toList())

internal fun installedRegion(
    id: String,
    version: String = "2026-10-24",
    size: Long = 100,
) = InstalledRegion(id, id.replaceFirstChar { it.uppercase() }, version, listOf(InstalledFile("basemap", "/maps/$id.mbtiles", size)))
