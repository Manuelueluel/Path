package it.traccia.feature.planner

import it.traccia.core.common.AppError
import it.traccia.core.common.AppResult
import it.traccia.core.common.SpeedReference
import it.traccia.core.common.Sport
import it.traccia.core.geo.GeoPoint
import it.traccia.core.storage.prefs.InMemoryUserPreferences
import it.traccia.core.storage.prefs.UserPreferences
import it.traccia.core.storage.route.Route
import it.traccia.core.storage.route.RouteRepository
import it.traccia.core.storage.route.RouteSegment
import it.traccia.core.storage.route.RouteSummary
import it.traccia.core.storage.route.SegmentKind
import it.traccia.feature.elevation.ElevationProfile
import it.traccia.feature.planner.draft.DraftStore
import it.traccia.feature.planner.draft.PlannerDraft
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val a = GeoPoint(45.0, 9.0)
    private val b = GeoPoint(45.01, 9.0)
    private val c = GeoPoint(45.02, 9.0)

    /** Routes through a fake mid point; unreachable targets fail. */
    private val unreachable = mutableSetOf<GeoPoint>()
    private val routerCalls = mutableListOf<Pair<GeoPoint, GeoPoint>>()
    private var routerGate: CompletableDeferred<Unit>? = null
    private val repository = FakeRepository()
    private val drafts = FakeDrafts()
    private val commands = PlannerCommands()
    private val preferences = InMemoryUserPreferences(UserPreferences(defaultSport = Sport.MTB))

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): PlannerViewModel {
        val vm =
            PlannerViewModel(
                router = { from, to, _, _ ->
                    routerCalls += from to to
                    routerGate?.await()
                    if (to in unreachable) {
                        AppResult.Failure(AppError.NotFound("route"))
                    } else {
                        AppResult.Success(listOf(from, GeoPoint((from.latitude + to.latitude) / 2, 9.001), to))
                    }
                },
                routingOptions = { sport -> if (sport == Sport.GRAVEL) listOf("prefer_unpaved") else listOf("avoid_steps") },
                elevation = { track -> fakeProfile(track) },
                routes = repository,
                drafts = drafts,
                commands = commands,
                preferences = preferences,
            )
        advanceUntilIdle()
        return vm
    }

    private fun fakeProfile(track: List<GeoPoint>): ElevationProfile =
        ElevationProfile(emptyList(), ascentMeters = 120.0, descentMeters = 30.0, 0.0, 0.0, 0.0, emptyList()).takeIf { track.size > 1 }
            ?: error("no track")

    private fun PlannerViewModel.tap(vararg points: GeoPoint) = points.forEach { onEvent(PlannerEvent.MapTapped(it)) }

    @Test
    fun `a fresh planner and a new route start with the default sport`() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertEquals(Sport.MTB, vm.uiState.value.sport)
            vm.onEvent(PlannerEvent.SportSelected(Sport.RUNNING))
            vm.tap(a, b)
            advanceUntilIdle()
            vm.onEvent(PlannerEvent.NewRoute)
            advanceUntilIdle()
            assertEquals(Sport.MTB, vm.uiState.value.sport)
        }

    @Test
    fun `changing the reference speeds updates the time estimate`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.tap(a, b)
            advanceUntilIdle()
            val before =
                vm.uiState.value.stats
                    ?.durationSeconds ?: error("no estimate")
            preferences.update { p ->
                p.copy(speeds = mapOf(Sport.MTB to SpeedReference.defaultFor(Sport.MTB).copy(flatKmh = 24.0)))
            }
            advanceUntilIdle()
            val after =
                vm.uiState.value.stats
                    ?.durationSeconds ?: error("no estimate")
            assertTrue(after < before)
        }

    @Test
    fun `taps draw a routed line`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.tap(a, b, c)
            advanceUntilIdle()
            val route = vm.uiState.value.route
            assertEquals(listOf(a, b, c), route.points)
            assertTrue(route.segments.all { it?.kind == SegmentKind.ROUTED })
            assertEquals(listOf(a to b, b to c), routerCalls)
            assertFalse(vm.uiState.value.isRouting)
        }

    @Test
    fun `unroutable segment becomes a dashed straight line`() =
        runTest(dispatcher) {
            unreachable += b
            val vm = viewModel()
            vm.tap(a, b)
            advanceUntilIdle()
            assertEquals(
                RouteSegment(listOf(a, b), SegmentKind.FALLBACK),
                vm.uiState.value.route.segments
                    .single(),
            )
        }

    @Test
    fun `stats include distance, climbing and time`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.tap(a, b)
            advanceUntilIdle()
            val stats = checkNotNull(vm.uiState.value.stats)
            assertTrue(stats.distanceMeters > 1000)
            assertEquals(120.0, stats.ascentMeters)
            assertTrue(checkNotNull(stats.durationSeconds) > 0)
        }

    @Test
    fun `tap on a point selects it and never adds a point`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.tap(a, b)
            vm.onEvent(PlannerEvent.PointTapped(0))
            assertEquals(0, vm.uiState.value.selectedPoint)
            assertEquals(2, vm.uiState.value.route.points.size)
            // With a point selected, a tap on the map only closes the "Cancella" option.
            vm.tap(c)
            assertNull(vm.uiState.value.selectedPoint)
            assertEquals(2, vm.uiState.value.route.points.size)
        }

    @Test
    fun `deleting a middle point joins the neighbours`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.tap(a, b, c)
            advanceUntilIdle()
            vm.onEvent(PlannerEvent.PointTapped(1))
            vm.onEvent(PlannerEvent.DeleteSelected)
            advanceUntilIdle()
            assertEquals(listOf(a, c), vm.uiState.value.route.points)
            assertEquals(
                SegmentKind.ROUTED,
                vm.uiState.value.route.segments
                    .single()
                    ?.kind,
            )
            assertTrue(a to c in routerCalls)
        }

    @Test
    fun `dragging moves the point on release and recomputes its segments`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.tap(a, b, c)
            advanceUntilIdle()
            val moved = GeoPoint(45.015, 9.01)
            vm.onEvent(PlannerEvent.DragStarted(1))
            vm.onEvent(PlannerEvent.DragMoved(moved))
            assertEquals(b, vm.uiState.value.route.points[1])
            assertEquals(
                moved,
                vm.uiState.value.drag
                    ?.position,
            )
            vm.onEvent(PlannerEvent.DragEnded(moved))
            advanceUntilIdle()
            assertEquals(moved, vm.uiState.value.route.points[1])
            assertNull(vm.uiState.value.drag)
            assertTrue(a to moved in routerCalls && moved to c in routerCalls)
        }

    @Test
    fun `obsolete computations are cancelled`() =
        runTest(dispatcher) {
            routerGate = CompletableDeferred()
            val vm = viewModel()
            vm.tap(a, b)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isRouting)
            vm.onEvent(PlannerEvent.Undo)
            routerGate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(a), vm.uiState.value.route.points)
            assertFalse(vm.uiState.value.isRouting)
        }

    @Test
    fun `close loop and undo`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.tap(a, b)
            vm.onEvent(PlannerEvent.CloseLoop)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.route.isClosed)
            vm.onEvent(PlannerEvent.Undo)
            assertEquals(listOf(a, b), vm.uiState.value.route.points)
        }

    @Test
    fun `clear all can be restored`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.tap(a, b)
            advanceUntilIdle()
            vm.onEvent(PlannerEvent.ClearAll)
            assertTrue(vm.uiState.value.route.isEmpty)
            assertEquals(PlannerEffect.Cleared, vm.effects.first())
            vm.onEvent(PlannerEvent.RestoreCleared)
            assertEquals(listOf(a, b), vm.uiState.value.route.points)
        }

    @Test
    fun `restore is gone once the snackbar is`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.tap(a, b)
            vm.onEvent(PlannerEvent.ClearAll)
            vm.onEvent(PlannerEvent.ClearedSnackbarGone)
            vm.onEvent(PlannerEvent.RestoreCleared)
            assertTrue(vm.uiState.value.route.isEmpty)
        }

    @Test
    fun `changing sport recomputes the whole route`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.tap(a, b, c)
            advanceUntilIdle()
            routerCalls.clear()
            vm.onEvent(PlannerEvent.SportSelected(Sport.GRAVEL))
            advanceUntilIdle()
            assertEquals(listOf(a to b, b to c), routerCalls)
            assertEquals(listOf("prefer_unpaved"), vm.uiState.value.availableOptions)
        }

    @Test
    fun `straight line profile does not route`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onEvent(PlannerEvent.StraightLineSelected(true))
            vm.tap(a, b)
            advanceUntilIdle()
            assertTrue(routerCalls.isEmpty())
            assertEquals(
                SegmentKind.STRAIGHT,
                vm.uiState.value.route.segments
                    .single()
                    ?.kind,
            )
        }

    @Test
    fun `save asks a name once and then updates the same route`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.tap(a, b)
            advanceUntilIdle()
            vm.onEvent(PlannerEvent.SaveClicked)
            assertTrue(vm.uiState.value.isSaveDialogOpen)
            vm.onEvent(PlannerEvent.SaveConfirmed("  Anello  ", Sport.HIKING))
            advanceUntilIdle()
            assertEquals("Anello", repository.saved.last().name)
            assertEquals(1L, vm.uiState.value.routeId)
            vm.onEvent(PlannerEvent.SaveConfirmed("Anello 2", Sport.HIKING))
            advanceUntilIdle()
            assertEquals(1L, repository.saved.last().id)
        }

    @Test
    fun `open from the library loads the route`() =
        runTest(dispatcher) {
            repository.stored =
                Route(
                    id = 9,
                    name = "Salvato",
                    sport = Sport.MTB,
                    points = listOf(a, b),
                    segments = listOf(RouteSegment(listOf(a, b), SegmentKind.ROUTED)),
                )
            val vm = viewModel()
            commands.open(9)
            advanceUntilIdle()
            val s = vm.uiState.value
            assertEquals(listOf(a, b), s.route.points)
            assertEquals(Sport.MTB, s.sport)
            assertEquals("Salvato", s.routeName)
            assertNull(commands.openRoute.value)
        }

    @Test
    fun `draft is saved and restored`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.tap(a, b)
            advanceUntilIdle()
            assertEquals(listOf(a, b), drafts.draft?.route?.points)
            val restored = viewModel()
            assertEquals(listOf(a, b), restored.uiState.value.route.points)
        }

    private class FakeRepository : RouteRepository {
        val saved = mutableListOf<Route>()
        var stored: Route? = null
        override val summaries: Flow<List<RouteSummary>> = flowOf(emptyList())
        override val shownOnMap: StateFlow<Set<Long>> = MutableStateFlow(emptySet())
        override val shownRoutes: Flow<List<Route>> = flowOf(emptyList())

        override suspend fun get(id: Long) = stored?.takeIf { it.id == id }

        override suspend fun save(route: Route): Long {
            val id = if (route.id == 0L) 1L else route.id
            saved += route.copy(id = id)
            return id
        }

        override suspend fun rename(
            id: Long,
            name: String,
        ) = Unit

        override suspend fun duplicate(
            id: Long,
            name: String,
        ): Long? = null

        override suspend fun delete(id: Long) = Unit

        override fun setShownOnMap(ids: Set<Long>) = Unit
    }

    private class FakeDrafts : DraftStore {
        var draft: PlannerDraft? = null

        override suspend fun load() = draft

        override suspend fun save(draft: PlannerDraft) {
            this.draft = draft
        }
    }
}
