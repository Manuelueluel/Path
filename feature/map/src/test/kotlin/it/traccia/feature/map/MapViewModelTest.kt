package it.traccia.feature.map

import it.traccia.core.storage.route.Route
import it.traccia.core.storage.route.RouteRepository
import it.traccia.core.storage.route.RouteSummary
import it.traccia.core.ui.permission.AppPermission
import it.traccia.core.ui.permission.PermissionChecker
import it.traccia.core.ui.permission.PermissionResult
import it.traccia.feature.heatmap.Heatmap
import it.traccia.feature.heatmap.HeatmapSettings
import it.traccia.feature.heatmap.HeatmapSource
import it.traccia.feature.heatmap.MapArea
import it.traccia.feature.heatmap.cache.CacheAge
import it.traccia.feature.heatmap.config.HeatmapConfig
import it.traccia.feature.heatmap.session.SessionState
import it.traccia.feature.map.prefs.CameraSnapshot
import it.traccia.feature.map.prefs.MapPreferences
import it.traccia.feature.map.prefs.MapPreferencesRepository
import it.traccia.feature.map.style.MapStyle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.style.sources.GeometryTileProvider
import org.maplibre.geojson.FeatureCollection

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val prefs = FakePreferences()
    private val permissions = FakePermissionChecker()
    private var elevation: Double? = 1234.4
    private val heatmap = FakeHeatmap()
    private val area = MapArea(9.40, 45.80, 9.55, 45.90, 13.0)

    private fun viewModel() =
        MapViewModel(
            preferences = prefs,
            elevation = { _, _ -> elevation },
            permissionChecker = permissions,
            styleLoader = { style -> "json-${style.name}" },
            contourProvider =
                object : GeometryTileProvider {
                    override fun getFeaturesForBounds(
                        bounds: LatLngBounds,
                        zoomLevel: Int,
                    ): FeatureCollection = error("not used")
                },
            heatmap = heatmap,
            routes = FakeRoutes(),
        )

    @Test
    fun `loads preferences and the matching style json`() {
        prefs.state.update { it.copy(style = MapStyle.MINIMAL, contoursVisible = false) }
        val state = viewModel().uiState.value
        assertTrue(state.isReady)
        assertEquals(MapStyle.MINIMAL, state.style)
        assertEquals("json-MINIMAL", state.styleJson)
        assertFalse(state.contoursVisible)
        assertEquals(MapPreferences.DEFAULT_CAMERA, state.initialCamera)
    }

    @Test
    fun `selecting a style persists it and reloads the json`() {
        val vm = viewModel()
        vm.onEvent(MapEvent.StyleSelected(MapStyle.MINIMAL))
        assertEquals(MapStyle.MINIMAL, prefs.state.value.style)
        assertEquals("json-MINIMAL", vm.uiState.value.styleJson)
    }

    @Test
    fun `toggles are persisted`() {
        val vm = viewModel()
        vm.onEvent(MapEvent.ContoursToggled(false))
        vm.onEvent(MapEvent.HillshadeToggled(false))
        assertFalse(vm.uiState.value.contoursVisible)
        assertFalse(vm.uiState.value.hillshadeVisible)
    }

    @Test
    fun `initial camera is not overwritten by later saves`() {
        val vm = viewModel()
        vm.onEvent(MapEvent.CameraIdle(CameraSnapshot(46.0, 10.0, 14.0)))
        assertEquals(MapPreferences.DEFAULT_CAMERA, vm.uiState.value.initialCamera)
        assertEquals(CameraSnapshot(46.0, 10.0, 14.0), prefs.state.value.camera)
    }

    @Test
    fun `camera move updates scale bar and clears the stale elevation`() {
        val vm = viewModel()
        vm.onEvent(MapEvent.CameraMoved(45.86, 9.47, 13.0))
        val state = vm.uiState.value
        assertEquals(45.86, state.center?.latitude)
        assertNull(state.center?.elevation)
        assertTrue(checkNotNull(state.scaleBar).widthDp <= 96f)
    }

    @Test
    fun `elevation is looked up when the camera settles`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.onEvent(MapEvent.CameraIdle(CameraSnapshot(45.86, 9.47, 13.0)))
            advanceTimeBy(500)
            assertEquals(
                1234.4,
                vm.uiState.value.center
                    ?.elevation,
            )
        }

    @Test
    fun `moving again cancels the pending elevation`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.onEvent(MapEvent.CameraIdle(CameraSnapshot(45.86, 9.47, 13.0)))
            vm.onEvent(MapEvent.CameraMoved(45.9, 9.5, 13.0))
            advanceTimeBy(500)
            assertNull(
                vm.uiState.value.center
                    ?.elevation,
            )
        }

    @Test
    fun `center on me without permission asks for it`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.onEvent(MapEvent.CenterOnMeClicked)
            assertEquals(MapEffect.RequestLocationPermission, vm.effects.first())
        }

    @Test
    fun `center on me with permission centers`() =
        runTest(main.dispatcher) {
            permissions.granted = true
            val vm = viewModel()
            vm.onEvent(MapEvent.CenterOnMeClicked)
            assertEquals(MapEffect.CenterOnUser, vm.effects.first())
            assertTrue(vm.uiState.value.isLocationEnabled)
        }

    @Test
    fun `granted permission enables location and centers`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.onEvent(MapEvent.LocationPermissionResult(PermissionResult(AppPermission.LOCATION, true, false)))
            assertEquals(MapEffect.CenterOnUser, vm.effects.first())
            assertTrue(vm.uiState.value.isLocationEnabled)
        }

    @Test
    fun `permanent denial offers system settings`() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.onEvent(MapEvent.LocationPermissionResult(PermissionResult(AppPermission.LOCATION, false, false)))
            assertEquals(MapEffect.LocationPermissionDenied, vm.effects.first())
            assertFalse(vm.uiState.value.isLocationEnabled)
        }

    @Test
    fun `resume picks up a permission granted in system settings`() {
        val vm = viewModel()
        permissions.granted = true
        vm.onEvent(MapEvent.Resumed)
        assertTrue(vm.uiState.value.isLocationEnabled)
    }

    @Test
    fun `layer sheet opens and closes`() {
        val vm = viewModel()
        vm.onEvent(MapEvent.LayersClicked)
        assertTrue(vm.uiState.value.isLayerSheetOpen)
        vm.onEvent(MapEvent.LayersDismissed)
        assertFalse(vm.uiState.value.isLayerSheetOpen)
    }

    @Test
    fun `heatmap is off by default and has the configured choices`() {
        val state = viewModel().uiState.value.heatmap
        assertNull(state.source)
        assertEquals(listOf("all", "hike"), state.sports)
    }

    @Test
    fun `turning the heatmap on exposes its source`() {
        val vm = viewModel()
        vm.onEvent(MapEvent.HeatmapToggled(true))
        vm.onEvent(MapEvent.HeatmapSportSelected("hike"))
        assertEquals(
            "heatmap/hike/hot",
            vm.uiState.value.heatmap.source
                ?.tilesUrl,
        )
    }

    @Test
    fun `refreshing the area marks it stale and reloads the tiles`() {
        val vm = viewModel()
        vm.onEvent(MapEvent.HeatmapToggled(true))
        vm.onEvent(MapEvent.CameraIdle(CameraSnapshot(45.85, 9.47, 13.0), area))
        vm.onEvent(MapEvent.HeatmapRefreshArea)
        assertEquals(listOf(area), heatmap.refreshed)
        assertEquals(1, vm.uiState.value.heatmap.reloadToken)
    }

    @Test
    fun `refresh does nothing while the heatmap is hidden`() {
        val vm = viewModel()
        vm.onEvent(MapEvent.CameraIdle(CameraSnapshot(45.85, 9.47, 13.0), area))
        vm.onEvent(MapEvent.HeatmapRefreshArea)
        assertTrue(heatmap.refreshed.isEmpty())
    }

    @Test
    fun `visible area age is shown`() =
        runTest(main.dispatcher) {
            heatmap.oldest = 42L
            val vm = viewModel()
            vm.onEvent(MapEvent.HeatmapToggled(true))
            vm.onEvent(MapEvent.CameraIdle(CameraSnapshot(45.85, 9.47, 13.0), area))
            assertEquals(42L, vm.uiState.value.heatmap.oldestTileMillis)
            advanceTimeBy(10_000)
        }

    private class FakeHeatmap : Heatmap {
        val state = MutableStateFlow(HeatmapSettings())
        val refreshed = mutableListOf<MapArea>()
        var oldest: Long? = null
        override val settings: Flow<HeatmapSettings> = state
        override val session: StateFlow<SessionState> = MutableStateFlow(SessionState.Active(null))
        override val config: HeatmapConfig =
            HeatmapConfig(
                tileUrl = "https://h/{sport}/{color}/{z}/{x}/{y}.png",
                tileSize = 256,
                minZoom = 3,
                maxZoom = 15,
                loginUrl = "",
                heatmapPageUrl = "",
                sessionCookieUrl = "",
                requiredCookies = emptyList(),
                policyCookie = "",
                refreshAfterDays = 30,
                sports = listOf(HeatmapConfig.Sport("all", "all"), HeatmapConfig.Sport("hike", "sport_Hike")),
                colors = listOf("hot"),
            )

        override suspend fun updateSettings(transform: (HeatmapSettings) -> HeatmapSettings) = state.update(transform)

        override fun source(settings: HeatmapSettings) = HeatmapSource("heatmap/${settings.sport}/${settings.color}", 256, 3, 15)

        override suspend fun age(
            settings: HeatmapSettings,
            area: MapArea,
        ): CacheAge? = oldest?.let { CacheAge(it, it, 1) }

        override suspend fun refresh(
            settings: HeatmapSettings,
            area: MapArea,
        ) {
            refreshed += area
        }
    }

    private class FakeRoutes : RouteRepository {
        override val summaries: Flow<List<RouteSummary>> = flowOf(emptyList())
        override val shownOnMap: StateFlow<Set<Long>> = MutableStateFlow(emptySet())
        override val shownRoutes: Flow<List<Route>> = flowOf(emptyList())

        override suspend fun get(id: Long): Route? = null

        override suspend fun save(route: Route) = 0L

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

    private class FakePreferences : MapPreferencesRepository {
        val state = MutableStateFlow(MapPreferences())
        override val preferences: Flow<MapPreferences> = state

        override suspend fun setStyle(style: MapStyle) = state.update { it.copy(style = style) }

        override suspend fun setContoursVisible(visible: Boolean) = state.update { it.copy(contoursVisible = visible) }

        override suspend fun setHillshadeVisible(visible: Boolean) = state.update { it.copy(hillshadeVisible = visible) }

        override suspend fun saveCamera(camera: CameraSnapshot) = state.update { it.copy(camera = camera) }
    }

    private class FakePermissionChecker : PermissionChecker {
        var granted = false

        override fun isGranted(permission: AppPermission) = granted
    }
}
