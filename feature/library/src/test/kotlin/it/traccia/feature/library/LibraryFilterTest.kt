package it.traccia.feature.library

import it.traccia.core.common.Sport
import it.traccia.core.storage.route.RouteSummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryFilterTest {
    private fun route(
        id: Long,
        name: String,
        sport: Sport,
        km: Double,
        updated: Long,
    ) = RouteSummary(id, name, sport, km * 1000, 0.0, 0.0, updated, emptyList())

    private val routes =
        listOf(
            route(1, "Anello del Resegone", Sport.HIKING, 12.0, 300),
            route(2, "Giro dei Corni di Canzo", Sport.MTB, 35.0, 100),
            route(3, "Ponte di Lecco", Sport.RUNNING, 8.0, 200),
            route(4, "Città Alta", Sport.HIKING, 5.0, 50),
        )

    private fun ids(list: List<RouteSummary>) = list.map { it.id }

    @Test
    fun `recent first by default`() {
        assertEquals(listOf(1L, 3L, 2L, 4L), ids(filterRoutes(routes, "", null, LibrarySort.RECENT)))
    }

    @Test
    fun `search ignores case and accents`() {
        assertEquals(listOf(4L), ids(filterRoutes(routes, "citta", null, LibrarySort.RECENT)))
        assertEquals(listOf(1L), ids(filterRoutes(routes, "  RESEG ", null, LibrarySort.RECENT)))
    }

    @Test
    fun `sport filter`() {
        assertEquals(listOf(1L, 4L), ids(filterRoutes(routes, "", Sport.HIKING, LibrarySort.RECENT)))
    }

    @Test
    fun `sort by name and distance`() {
        assertEquals(listOf(1L, 4L, 2L, 3L), ids(filterRoutes(routes, "", null, LibrarySort.NAME)))
        assertEquals(listOf(2L, 1L, 3L, 4L), ids(filterRoutes(routes, "", null, LibrarySort.DISTANCE)))
    }
}
