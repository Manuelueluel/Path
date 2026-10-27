package it.traccia.feature.gpx.backup

import it.traccia.core.common.Sport
import it.traccia.core.geo.Polyline
import it.traccia.core.storage.route.Route
import it.traccia.core.storage.route.RouteSegment
import it.traccia.core.storage.route.RoutingSettings
import it.traccia.core.storage.route.SegmentKind
import it.traccia.feature.gpx.GpxConversion
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A route to back up, with its GPX already written. */
data class BackupEntry(
    val route: Route,
    val gpx: String,
)

/** What an archive holds: exact routes from the manifest, or plain GPX files to import. */
data class BackupContents(
    val routes: List<Route>,
    /** GPX files without a manifest entry (e.g. archives made by hand or by other apps), by file name. */
    val looseGpx: Map<String, ByteArray>,
)

/**
 * Library backup (M8.6): a ZIP with one GPX per route, readable by any app, plus a manifest
 * that restores control points, segments, sport and routing exactly. Pure: tested on streams.
 */
object BackupArchive {
    const val MANIFEST = "path-library.json"
    private const val FORMAT_VERSION = 1
    private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    fun write(
        entries: List<BackupEntry>,
        out: OutputStream,
    ) {
        val names = HashSet<String>()
        val manifest = ArrayList<BackupRoute>()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (route, gpx) ->
                val file = uniqueName(GpxConversion.fileName(route.name), names)
                zip.putNextEntry(ZipEntry(file))
                zip.write(gpx.toByteArray())
                zip.closeEntry()
                manifest += BackupRoute.from(route, file)
            }
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(Manifest.serializer(), Manifest(FORMAT_VERSION, manifest)).toByteArray())
            zip.closeEntry()
        }
    }

    /** @throws BackupFormatException when [input] is not a ZIP with routes or GPX files. */
    fun read(input: InputStream): BackupContents {
        val files = LinkedHashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val name = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && (name == MANIFEST || name.endsWith(".gpx", ignoreCase = true))) {
                    files[name] = zip.readLimited()
                }
            }
        }
        val manifest = files.remove(MANIFEST)?.let(::decodeManifest)
        val routes = manifest?.routes.orEmpty().map { it.toRoute() }
        manifest?.routes?.forEach { files.remove(it.file) }
        if (routes.isEmpty() && files.isEmpty()) throw BackupFormatException("No routes in the archive")
        return BackupContents(routes, files)
    }

    private fun decodeManifest(bytes: ByteArray): Manifest =
        try {
            json.decodeFromString(Manifest.serializer(), bytes.decodeToString())
        } catch (e: SerializationException) {
            throw BackupFormatException("Invalid manifest", e)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("Invalid manifest", e)
        }

    private fun ZipInputStream.readLimited(): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw BackupFormatException("Entry too large")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun uniqueName(
        fileName: String,
        taken: MutableSet<String>,
    ): String {
        val base = fileName.removeSuffix(".gpx")
        var candidate = fileName
        var n = 2
        while (!taken.add(candidate.lowercase())) candidate = "${base}_${n++}.gpx"
        return candidate
    }

    private const val BUFFER = 64 * 1024

    @Serializable
    private data class Manifest(
        val formatVersion: Int,
        val routes: List<BackupRoute>,
    )

    @Serializable
    private data class BackupRoute(
        val file: String,
        val name: String,
        val description: String = "",
        val sport: String,
        val straightLine: Boolean = false,
        val routingOptions: List<String> = emptyList(),
        /** Encoded polyline of the control points. */
        val points: String,
        val segments: List<BackupSegment>,
        val distanceMeters: Double = 0.0,
        val ascentMeters: Double = 0.0,
        val descentMeters: Double = 0.0,
        val createdAtMillis: Long = 0,
        val updatedAtMillis: Long = 0,
    ) {
        fun toRoute(): Route =
            Route(
                name = name,
                description = description,
                sport = Sport.entries.firstOrNull { it.name == sport } ?: Sport.HIKING,
                routing = RoutingSettings(straightLine, routingOptions.toSet()),
                points = Polyline.decode(points),
                segments =
                    segments.map { s ->
                        val kind = SegmentKind.entries.firstOrNull { it.name == s.kind } ?: SegmentKind.ROUTED
                        RouteSegment(Polyline.decode(s.geometry), kind)
                    },
                distanceMeters = distanceMeters,
                ascentMeters = ascentMeters,
                descentMeters = descentMeters,
                createdAtMillis = createdAtMillis,
                updatedAtMillis = updatedAtMillis,
            )

        companion object {
            fun from(
                route: Route,
                file: String,
            ) = BackupRoute(
                file = file,
                name = route.name,
                description = route.description,
                sport = route.sport.name,
                straightLine = route.routing.straightLine,
                routingOptions = route.routing.options.sorted(),
                points = Polyline.encode(route.points),
                segments = route.segments.map { BackupSegment(it.kind.name, Polyline.encode(it.geometry)) },
                distanceMeters = route.distanceMeters,
                ascentMeters = route.ascentMeters,
                descentMeters = route.descentMeters,
                createdAtMillis = route.createdAtMillis,
                updatedAtMillis = route.updatedAtMillis,
            )
        }
    }

    @Serializable
    private data class BackupSegment(
        val kind: String,
        val geometry: String,
    )
}

class BackupFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
