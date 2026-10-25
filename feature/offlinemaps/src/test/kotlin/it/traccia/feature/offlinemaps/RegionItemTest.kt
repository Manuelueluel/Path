package it.traccia.feature.offlinemaps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RegionItemTest {
    private fun statusOf(
        id: String,
        items: List<RegionItem>,
    ) = items.single { it.id == id }.status

    @Test
    fun `catalog regions are not installed by default`() {
        val items = resolveRegionItems(catalogOf(catalogRegion("lombardia")), emptyList(), emptyMap(), emptyMap())
        assertEquals(RegionStatus.NotInstalled, statusOf("lombardia", items))
        assertEquals(100, items.single().sizeBytes)
    }

    @Test
    fun `installed region with the same version has no update`() {
        val items = resolveRegionItems(catalogOf(catalogRegion("veneto")), listOf(installedRegion("veneto")), emptyMap(), emptyMap())
        assertEquals(RegionStatus.Installed(updateAvailable = false), statusOf("veneto", items))
    }

    @Test
    fun `newer catalog version is an update`() {
        val items =
            resolveRegionItems(
                catalogOf(catalogRegion("veneto", version = "2026-11-01")),
                listOf(installedRegion("veneto", version = "2026-10-24")),
                emptyMap(),
                emptyMap(),
            )
        assertEquals(RegionStatus.Installed(updateAvailable = true), statusOf("veneto", items))
    }

    @Test
    fun `running download wins over installed and paused`() {
        val items =
            resolveRegionItems(
                catalogOf(catalogRegion("veneto", version = "2026-11-01")),
                listOf(installedRegion("veneto")),
                mapOf("veneto" to DownloadState.Running(40, 100)),
                mapOf("veneto" to 40L),
            )
        assertEquals(RegionStatus.Downloading(40, 100), statusOf("veneto", items))
        assertEquals("2026-10-24", items.single().installedVersion)
    }

    @Test
    fun `partial files without work mean paused`() {
        val items = resolveRegionItems(catalogOf(catalogRegion("liguria")), emptyList(), emptyMap(), mapOf("liguria" to 30L))
        assertEquals(RegionStatus.Paused(30, 100), statusOf("liguria", items))
    }

    @Test
    fun `failed download reports its error`() {
        val items =
            resolveRegionItems(
                catalogOf(catalogRegion("liguria")),
                emptyList(),
                mapOf("liguria" to DownloadState.Failed(DownloadError.CHECKSUM)),
                emptyMap(),
            )
        assertEquals(RegionStatus.Failed(DownloadError.CHECKSUM), statusOf("liguria", items))
    }

    @Test
    fun `installed region removed from the catalog stays listed`() {
        val items = resolveRegionItems(catalogOf(), listOf(installedRegion("old", size = 7)), emptyMap(), emptyMap())
        val item = items.single()
        assertEquals(RegionStatus.Installed(updateAvailable = false), item.status)
        assertNull(item.catalogRegion)
        assertEquals(7, item.sizeBytes)
    }

    @Test
    fun `no catalog yet still shows installed regions`() {
        val items = resolveRegionItems(null, listOf(installedRegion("veneto")), emptyMap(), emptyMap())
        assertEquals(listOf("veneto"), items.map { it.id })
    }

    @Test
    fun `items are sorted by name`() {
        val items = resolveRegionItems(catalogOf(catalogRegion("veneto"), catalogRegion("abruzzo")), emptyList(), emptyMap(), emptyMap())
        assertEquals(listOf("abruzzo", "veneto"), items.map { it.id })
    }
}
