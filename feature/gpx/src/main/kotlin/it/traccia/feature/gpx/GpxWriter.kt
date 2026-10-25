package it.traccia.feature.gpx

import it.traccia.core.geo.GeoPoint
import java.util.Locale

/** What to put in the exported file (M7.1). */
enum class GpxContent { TRACK, ROUTE, BOTH }

/** Writes GPX 1.1 (M7.1). Pure: tested on its output. */
object GpxWriter {
    private const val CREATOR = "Path"

    /**
     * @param track full geometry, with elevations when known.
     * @param routePoints control points, written as `rte` when requested.
     */
    @Suppress("LongParameterList")
    fun write(
        name: String,
        description: String,
        track: List<GeoPoint>,
        routePoints: List<GeoPoint>,
        waypoints: List<GpxWaypoint>,
        content: GpxContent,
    ): String =
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<gpx version="1.1" creator="$CREATOR" xmlns="http://www.topografix.com/GPX/1/1" """)
            append("""xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """)
            append("""xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""").append('\n')
            append("  <metadata>\n")
            append("    <name>").append(escape(name)).append("</name>\n")
            if (description.isNotBlank()) append("    <desc>").append(escape(description)).append("</desc>\n")
            append("  </metadata>\n")
            waypoints.forEach { w ->
                append("  <wpt ").append(coords(w.point)).append(">")
                w.point.elevation?.let { append("<ele>").append(ele(it)).append("</ele>") }
                w.name?.let { append("<name>").append(escape(it)).append("</name>") }
                append("</wpt>\n")
            }
            if (content != GpxContent.TRACK) {
                append("  <rte>\n    <name>").append(escape(name)).append("</name>\n")
                routePoints.forEach { p -> point("rtept", p, "    ") }
                append("  </rte>\n")
            }
            if (content != GpxContent.ROUTE) {
                append("  <trk>\n    <name>").append(escape(name)).append("</name>\n")
                if (description.isNotBlank()) append("    <desc>").append(escape(description)).append("</desc>\n")
                append("    <trkseg>\n")
                track.forEach { p -> point("trkpt", p, "      ") }
                append("    </trkseg>\n  </trk>\n")
            }
            append("</gpx>\n")
        }

    private fun StringBuilder.point(
        tag: String,
        p: GeoPoint,
        indent: String,
    ) {
        append(indent)
            .append('<')
            .append(tag)
            .append(' ')
            .append(coords(p))
        val elevation = p.elevation
        if (elevation == null) {
            append("/>\n")
        } else {
            append("><ele>")
                .append(ele(elevation))
                .append("</ele></")
                .append(tag)
                .append(">\n")
        }
    }

    private fun coords(p: GeoPoint) = String.format(Locale.ROOT, """lat="%.6f" lon="%.6f"""", p.latitude, p.longitude)

    private fun ele(meters: Double) = String.format(Locale.ROOT, "%.1f", meters)

    fun escape(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
