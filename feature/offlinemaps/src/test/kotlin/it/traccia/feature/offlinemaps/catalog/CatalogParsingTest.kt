package it.traccia.feature.offlinemaps.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CatalogParsingTest {
    /** Same shape as tools/pipeline/make_catalog.py writes. */
    private val sample =
        """
        {
          "formatVersion": 1,
          "regions": [
            {
              "id": "test-lecco",
              "name": "Lecco e Grigne (test)",
              "version": "2026-10-24",
              "bbox": [9.25, 45.78, 9.6, 46.05],
              "files": [
                {"type": "basemap", "size": 11841536, "sha256": "b70c", "url": "https://h/maps/test-lecco-basemap-2026-10-24.mbtiles"},
                {"type": "dem", "size": 6897664, "sha256": "7f60", "url": "https://h/maps/test-lecco-dem-2026-10-24.mbtiles"}
              ],
              "futureField": true
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `parses the pipeline catalog and ignores unknown fields`() {
        val region = parseCatalog(sample).regions.single()
        assertEquals("test-lecco", region.id)
        assertEquals(11841536L + 6897664L, region.totalBytes)
        assertEquals("test-lecco-dem-2026-10-24.mbtiles", region.files[1].fileName)
    }
}
