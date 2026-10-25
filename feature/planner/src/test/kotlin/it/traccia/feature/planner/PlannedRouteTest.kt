package it.traccia.feature.planner

import it.traccia.core.geo.GeoPoint
import it.traccia.core.storage.route.RouteSegment
import it.traccia.core.storage.route.SegmentKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlannedRouteTest {
    private val a = GeoPoint(45.0, 9.0)
    private val b = GeoPoint(45.1, 9.0)
    private val c = GeoPoint(45.2, 9.0)
    private val d = GeoPoint(45.3, 9.0)

    private fun seg(
        from: GeoPoint,
        to: GeoPoint,
    ) = RouteSegment(listOf(from, GeoPoint((from.latitude + to.latitude) / 2, 9.001), to), SegmentKind.ROUTED)

    /** a-b-c-d with all segments computed. */
    private val route =
        PlannedRoute(listOf(a, b, c, d), listOf(seg(a, b), seg(b, c), seg(c, d)))

    @Test
    fun `first tap creates the start, the next ones are joined to the previous point`() {
        val r = PlannedRoute().add(a)
        assertEquals(listOf(a), r.points)
        assertTrue(r.segments.isEmpty())
        val r2 = r.add(b).add(c)
        assertEquals(listOf(a, b, c), r2.points)
        assertEquals(listOf(0, 1), r2.pending)
    }

    @Test
    fun `deleting a middle point joins its neighbours`() {
        val r = route.deleteAt(1)
        assertEquals(listOf(a, c, d), r.points)
        assertNull(r.segments[0])
        assertEquals(seg(c, d), r.segments[1])
    }

    @Test
    fun `deleting the start or the end drops one segment`() {
        assertEquals(listOf(seg(b, c), seg(c, d)), route.deleteAt(0).segments)
        assertEquals(listOf(seg(a, b), seg(b, c)), route.deleteAt(3).segments)
        assertEquals(PlannedRoute(), PlannedRoute().add(a).deleteAt(0))
    }

    @Test
    fun `moving a point invalidates only the adjacent segments`() {
        val moved = GeoPoint(45.15, 9.1)
        val r = route.moveTo(2, moved)
        assertEquals(moved, r.points[2])
        assertEquals(listOf(seg(a, b), null, null), r.segments)
        assertEquals(listOf(null, seg(b, c), seg(c, d)), route.moveTo(0, moved).segments)
    }

    @Test
    fun `undo removes the last point and its segment`() {
        val r = route.undo()
        assertEquals(listOf(a, b, c), r.points)
        assertEquals(listOf(seg(a, b), seg(b, c)), r.segments)
        assertEquals(PlannedRoute(), PlannedRoute().undo())
    }

    @Test
    fun `close loop appends the start and can be undone`() {
        val r = route.closeLoop()
        assertEquals(a, r.points.last())
        assertTrue(r.isClosed)
        assertFalse(r.canCloseLoop)
        assertEquals(listOf(3), r.pending)
        assertEquals(route, r.undo())
    }

    @Test
    fun `close loop needs two points and is not repeated`() {
        assertFalse(PlannedRoute().add(a).canCloseLoop)
        val outAndBack = PlannedRoute().add(a).add(b).closeLoop()
        assertEquals(listOf(a, b, a), outAndBack.points)
        assertEquals(outAndBack, outAndBack.closeLoop())
    }

    @Test
    fun `after closing, a new tap continues from the start`() {
        val r = route.closeLoop().add(b)
        assertEquals(listOf(a, b, c, d, a, b), r.points)
        assertFalse(r.isClosed)
    }

    @Test
    fun `computed segment is stored only if its end points are unchanged`() {
        val pending = PlannedRoute().add(a).add(b)
        assertEquals(seg(a, b), pending.withSegment(0, a, b, seg(a, b)).segments[0])
        // The user moved b meanwhile: the late result is dropped.
        val moved = pending.moveTo(1, c)
        assertNull(moved.withSegment(0, a, b, seg(a, b)).segments[0])
    }

    @Test
    fun `track uses straight lines for pending segments`() {
        val r =
            PlannedRoute()
                .add(a)
                .add(b)
                .add(c)
                .withSegment(0, a, b, seg(a, b))
        assertEquals(seg(a, b).geometry + c, r.track)
    }

    @Test
    fun `settings change recomputes everything`() {
        assertEquals(listOf(0, 1, 2), route.invalidateAll().pending)
    }

    @Test
    fun `pending segments are saved as straight fallbacks`() {
        val r = PlannedRoute().add(a).add(b)
        assertEquals(listOf(RouteSegment(listOf(a, b), SegmentKind.FALLBACK)), r.completedSegments())
    }
}
