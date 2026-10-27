package it.traccia.feature.gpx.backup

import it.traccia.core.common.Sport
import it.traccia.core.geo.GeoPoint
import it.traccia.core.storage.route.Route
import it.traccia.core.storage.route.RouteSegment
import it.traccia.core.storage.route.RoutingSettings
import it.traccia.core.storage.route.SegmentKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    private val a = GeoPoint(45.85, 9.40)
    private val b = GeoPoint(45.86, 9.41)
    private val c = GeoPoint(45.87, 9.40)

    private fun route(name: String) =
        Route(
            name = name,
            description = "Giro del Resegone",
            sport = Sport.MTB,
            routing = RoutingSettings(straightLine = false, options = setOf("avoid_steps")),
            points = listOf(a, b, c),
            segments =
                listOf(
                    RouteSegment(listOf(a, GeoPoint(45.855, 9.405), b), SegmentKind.ROUTED),
                    RouteSegment(listOf(b, c), SegmentKind.FALLBACK),
                ),
            distanceMeters = 2500.0,
            ascentMeters = 300.0,
            descentMeters = 120.0,
            createdAtMillis = 1_000,
            updatedAtMillis = 2_000,
        )

    private fun archive(vararg routes: Route): ByteArray {
        val out = ByteArrayOutputStream()
        BackupArchive.write(routes.map { BackupEntry(it, "<gpx>${it.name}</gpx>") }, out)
        return out.toByteArray()
    }

    @Test
    fun `routes come back exactly from the manifest`() {
        val original = route("Resegone")
        val contents = BackupArchive.read(ByteArrayInputStream(archive(original)))
        assertEquals(listOf(original), contents.routes)
        assertTrue(contents.looseGpx.isEmpty())
    }

    @Test
    fun `each route has its own gpx file, also with equal names`() {
        val names =
            ZipInputStream(ByteArrayInputStream(archive(route("Anello"), route("Anello")))).use { zip ->
                generateSequence { zip.nextEntry }.map { it.name }.toList()
            }
        assertEquals(listOf("Anello.gpx", "Anello_2.gpx", BackupArchive.MANIFEST), names)
    }

    @Test
    fun `gpx files without a manifest are returned to be imported`() {
        val bytes =
            ByteArrayOutputStream()
                .also { out ->
                    ZipOutputStream(out).use { zip ->
                        zip.putNextEntry(ZipEntry("tracce/giro.gpx"))
                        zip.write("<gpx/>".toByteArray())
                        zip.putNextEntry(ZipEntry("note.txt"))
                        zip.write("ignored".toByteArray())
                    }
                }.toByteArray()
        val contents = BackupArchive.read(ByteArrayInputStream(bytes))
        assertTrue(contents.routes.isEmpty())
        assertEquals(setOf("giro.gpx"), contents.looseGpx.keys)
    }

    @Test
    fun `an archive without routes is rejected`() {
        val empty = ByteArrayOutputStream().also { ZipOutputStream(it).use { zip -> zip.putNextEntry(ZipEntry("a.txt")) } }.toByteArray()
        assertThrows<BackupFormatException> { BackupArchive.read(ByteArrayInputStream(empty)) }
        assertThrows<BackupFormatException> { BackupArchive.read(ByteArrayInputStream("not a zip".toByteArray())) }
    }
}
