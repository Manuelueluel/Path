package it.traccia.feature.gpx

import it.traccia.core.geo.GeoPoint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream

/** The file is not a readable GPX. */
class GpxFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Reads GPX 1.0/1.1 tracks, routes and waypoints (M7.4). Namespace-agnostic, tolerant of extensions. */
object GpxParser {
    @Throws(GpxFormatException::class)
    fun parse(input: InputStream): GpxDocument =
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(input, null)
            read(parser)
        } catch (e: XmlPullParserException) {
            throw GpxFormatException("Invalid XML: ${e.message}", e)
        } catch (e: IOException) {
            throw GpxFormatException("Cannot read the file", e)
        } catch (e: NumberFormatException) {
            throw GpxFormatException("Invalid coordinates", e)
        } catch (e: IllegalArgumentException) {
            // GeoPoint rejects coordinates out of range.
            throw GpxFormatException("Invalid coordinates", e)
        }

    private class Builder {
        var name: String? = null
        var description: String? = null
        val tracks = ArrayList<MutableList<GeoPoint>>()
        val routes = ArrayList<MutableList<GeoPoint>>()
        val waypoints = ArrayList<GpxWaypoint>()

        // Element being read.
        var lat: Double? = null
        var lon: Double? = null
        var ele: Double? = null
        var pointName: String? = null
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    private fun read(parser: XmlPullParser): GpxDocument {
        val b = Builder()
        val path = ArrayList<String>()
        var sawGpx = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    path += tag
                    when (tag) {
                        "gpx" -> sawGpx = true
                        "trk" -> b.tracks.add(ArrayList())
                        "rte" -> b.routes.add(ArrayList())
                        "trkpt", "rtept", "wpt" -> {
                            b.lat = parser.getAttributeValue(null, "lat")?.toDouble()
                            b.lon = parser.getAttributeValue(null, "lon")?.toDouble()
                            b.ele = null
                            b.pointName = null
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        val tag = path.lastOrNull()
                        val parent = path.getOrNull(path.size - 2)
                        when {
                            tag == "ele" && parent in POINT_TAGS -> b.ele = text.toDoubleOrNull()
                            tag == "name" && parent in POINT_TAGS -> b.pointName = text
                            tag == "name" &&
                                (parent == "metadata" || (parent == "trk" && b.name == null) || (parent == "rte" && b.name == null)) ->
                                b.name = text
                            tag == "desc" && (parent == "metadata" || parent == "trk") && b.description == null -> b.description = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "trkpt" -> pointOf(b)?.let { b.tracks.lastOrNull()?.add(it) }
                        "rtept" -> pointOf(b)?.let { b.routes.lastOrNull()?.add(it) }
                        "wpt" -> pointOf(b)?.let { b.waypoints += GpxWaypoint(it, b.pointName) }
                    }
                    path.removeLastOrNull()
                }
            }
        }
        if (!sawGpx) throw GpxFormatException("Not a GPX file")
        return GpxDocument(b.name, b.description, b.tracks.filter { it.isNotEmpty() }, b.routes.filter { it.isNotEmpty() }, b.waypoints)
    }

    private fun pointOf(b: Builder): GeoPoint? {
        val lat = b.lat ?: return null
        val lon = b.lon ?: return null
        return GeoPoint(lat, lon, b.ele)
    }

    private val POINT_TAGS = setOf("trkpt", "rtept", "wpt")
}
