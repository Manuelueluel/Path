package it.traccia.feature.offlinemaps

import it.traccia.core.common.AppError
import it.traccia.core.common.AppResult
import it.traccia.core.ui.permission.PermissionChecker
import it.traccia.feature.offlinemaps.catalog.Catalog
import it.traccia.feature.offlinemaps.catalog.CatalogRegion
import it.traccia.feature.offlinemaps.catalog.CatalogSource
import it.traccia.feature.offlinemaps.download.RegionDownloadController
import it.traccia.feature.offlinemaps.installed.InstalledRegion
import it.traccia.feature.offlinemaps.installed.InstalledRegions
import it.traccia.feature.offlinemaps.storage.StorageKind
import it.traccia.feature.offlinemaps.storage.StorageLocation
import it.traccia.feature.offlinemaps.storage.StorageLocations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineMapsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val catalog = FakeCatalog()
    private val installed = FakeInstalled()
    private val downloads = FakeDownloads()
    private val storage = FakeStorage()
    private var notificationsGranted = true

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        OfflineMapsViewModel(catalog, installed, downloads, storage, PermissionChecker { notificationsGranted })

    @Test
    fun `shows catalog and installed regions in their sections`() {
        catalog.next = AppResult.Success(catalogOf(catalogRegion("lombardia"), catalogRegion("veneto")))
        installed.state.value = listOf(installedRegion("veneto", size = 42))
        val state = viewModel().uiState.value
        assertEquals(listOf("veneto"), state.installed.map { it.id })
        assertEquals(listOf("lombardia"), state.available.map { it.id })
        assertEquals(42, state.usedBytes)
        assertEquals(false, state.isRefreshing)
    }

    @Test
    fun `catalog failure is reported and keeps the cached catalog`() =
        runTest(dispatcher) {
            catalog.state.value = catalogOf(catalogRegion("lombardia"))
            catalog.next = AppResult.Failure(AppError.Network())
            val vm = viewModel()
            assertTrue(vm.uiState.value.catalogUnavailable)
            assertEquals(
                listOf("lombardia"),
                vm.uiState.value.available
                    .map { it.id },
            )
            assertEquals(OfflineMapsEffect.Show(OfflineMessage.CATALOG_UNAVAILABLE), vm.effects.first())
        }

    @Test
    fun `download starts on the selected location`() {
        catalog.next = AppResult.Success(catalogOf(catalogRegion("lombardia")))
        val vm = viewModel()
        vm.onEvent(OfflineMapsEvent.Download("lombardia"))
        assertEquals(listOf("lombardia" to "internal"), downloads.started)
    }

    @Test
    fun `not enough space blocks the download`() =
        runTest(dispatcher) {
            catalog.next = AppResult.Success(catalogOf(catalogRegion("lombardia", size = 10L * 1024 * 1024 * 1024)))
            val vm = viewModel()
            vm.onEvent(OfflineMapsEvent.Download("lombardia"))
            assertTrue(downloads.started.isEmpty())
            assertEquals(OfflineMapsEffect.Show(OfflineMessage.STORAGE_FULL), vm.effects.first())
        }

    @Test
    fun `first download asks for notifications once`() =
        runTest(dispatcher) {
            notificationsGranted = false
            catalog.next = AppResult.Success(catalogOf(catalogRegion("a"), catalogRegion("b")))
            val vm = viewModel()
            vm.onEvent(OfflineMapsEvent.Download("a"))
            vm.onEvent(OfflineMapsEvent.Download("b"))
            assertEquals(OfflineMapsEffect.RequestNotificationPermission, vm.effects.first())
            assertEquals(2, downloads.started.size)
        }

    @Test
    fun `pause and cancel reach the controller`() {
        catalog.next = AppResult.Success(catalogOf(catalogRegion("a")))
        val vm = viewModel()
        vm.onEvent(OfflineMapsEvent.Pause("a"))
        vm.onEvent(OfflineMapsEvent.Cancel("a"))
        assertEquals(listOf("pause:a", "cancel:a"), downloads.actions)
    }

    @Test
    fun `delete asks for confirmation first`() {
        catalog.next = AppResult.Success(catalogOf())
        installed.state.value = listOf(installedRegion("veneto"))
        val vm = viewModel()
        vm.onEvent(OfflineMapsEvent.Delete("veneto"))
        assertEquals(
            "veneto",
            vm.uiState.value.pendingDeletion
                ?.id,
        )
        assertTrue(installed.deleted.isEmpty())
        vm.onEvent(OfflineMapsEvent.ConfirmDelete)
        assertEquals(listOf("veneto"), installed.deleted)
        assertNull(vm.uiState.value.pendingDeletion)
    }

    @Test
    fun `dismissing the confirmation keeps the region`() {
        catalog.next = AppResult.Success(catalogOf())
        installed.state.value = listOf(installedRegion("veneto"))
        val vm = viewModel()
        vm.onEvent(OfflineMapsEvent.Delete("veneto"))
        vm.onEvent(OfflineMapsEvent.DismissDelete)
        assertTrue(installed.deleted.isEmpty())
    }

    @Test
    fun `storage location can be changed`() {
        catalog.next = AppResult.Success(catalogOf())
        val vm = viewModel()
        vm.onEvent(OfflineMapsEvent.SelectLocation("sd-1"))
        assertEquals("sd-1", vm.uiState.value.selectedLocationId)
    }

    private class FakeCatalog : CatalogSource {
        val state = MutableStateFlow<Catalog?>(null)
        var next: AppResult<Catalog> = AppResult.Failure(AppError.Network())
        override val catalog: StateFlow<Catalog?> = state

        override suspend fun refresh(): AppResult<Catalog> = next.also { if (it is AppResult.Success) state.value = it.value }
    }

    private class FakeInstalled : InstalledRegions {
        val state = MutableStateFlow<List<InstalledRegion>>(emptyList())
        val deleted = mutableListOf<String>()
        override val regions: Flow<List<InstalledRegion>> = state

        override suspend fun install(
            region: CatalogRegion,
            files: Map<String, File>,
        ) = Unit

        override suspend fun delete(id: String) {
            deleted += id
            state.update { list -> list.filterNot { it.id == id } }
        }
    }

    private class FakeDownloads : RegionDownloadController {
        val started = mutableListOf<Pair<String, String>>()
        val actions = mutableListOf<String>()
        override val states: Flow<Map<String, DownloadState>> = MutableStateFlow(emptyMap())

        override suspend fun partialBytes(): Map<String, Long> = emptyMap()

        override suspend fun start(
            region: CatalogRegion,
            location: StorageLocation,
        ) {
            started += region.id to location.id
        }

        override fun pause(regionId: String) {
            actions += "pause:$regionId"
        }

        override suspend fun cancel(regionId: String) {
            actions += "cancel:$regionId"
        }
    }

    private class FakeStorage : StorageLocations {
        private val selected = MutableStateFlow("internal")
        private val all =
            listOf(
                StorageLocation("internal", StorageKind.INTERNAL, File("/internal"), freeBytes = 1L * 1024 * 1024 * 1024),
                StorageLocation("sd-1", StorageKind.REMOVABLE, File("/sd"), freeBytes = 5L * 1024 * 1024 * 1024),
            )

        override fun available() = all

        override val selectedId: Flow<String> = selected

        override suspend fun select(id: String) {
            selected.value = id
        }

        override suspend fun selected() = all.first { it.id == selected.value }

        override fun stagingLocation(regionId: String): StorageLocation? = null
    }
}
