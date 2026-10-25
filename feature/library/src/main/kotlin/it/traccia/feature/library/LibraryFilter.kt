package it.traccia.feature.library

import it.traccia.core.common.Sport
import it.traccia.core.storage.route.RouteSummary
import java.text.Normalizer

enum class LibrarySort { RECENT, NAME, DISTANCE }

/** Search, sport filter and sort of the route list (M7.7). Accents and case are ignored. */
fun filterRoutes(
    routes: List<RouteSummary>,
    query: String,
    sport: Sport?,
    sort: LibrarySort,
): List<RouteSummary> {
    val needle = normalize(query.trim())
    val filtered = routes.filter { (sport == null || it.sport == sport) && (needle.isEmpty() || normalize(it.name).contains(needle)) }
    return when (sort) {
        LibrarySort.RECENT -> filtered.sortedByDescending { it.updatedAtMillis }
        LibrarySort.NAME -> filtered.sortedBy { normalize(it.name) }
        LibrarySort.DISTANCE -> filtered.sortedByDescending { it.distanceMeters }
    }
}

private val diacritics = Regex("\\p{M}+")

private fun normalize(text: String): String = diacritics.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()
