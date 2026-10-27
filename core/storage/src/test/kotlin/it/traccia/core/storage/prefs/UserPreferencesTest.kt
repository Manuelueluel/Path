package it.traccia.core.storage.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import it.traccia.core.common.SpeedReference
import it.traccia.core.common.Sport
import it.traccia.core.common.UnitSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesTest {
    @TempDir
    lateinit var dir: File

    private fun repository(scope: CoroutineScope) =
        DataStoreUserPreferences(
            PreferenceDataStoreFactory.create(scope = scope) { File(dir, "user.preferences_pb") },
            scope,
        )

    @Test
    fun `defaults when nothing is stored`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val prefs = repository(scope).load()
            assertEquals(UserPreferences(), prefs)
            assertFalse(prefs.onboardingDone)
            assertEquals(SpeedReference.defaultFor(Sport.MTB), prefs.speedFor(Sport.MTB))
            scope.cancel()
        }

    @Test
    fun `stored values survive a new instance and can be reset`() =
        runTest {
            val custom = SpeedReference(5.0, 400.0, 600.0)
            val first = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            repository(first).update {
                it.copy(
                    units = UnitSystem.IMPERIAL,
                    defaultSport = Sport.GRAVEL,
                    heatmapRefreshDays = 7,
                    speeds = mapOf(Sport.HIKING to custom),
                    onboardingDone = true,
                )
            }
            first.cancel()

            val second = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val repo = repository(second)
            val loaded = repo.load()
            assertEquals(UnitSystem.IMPERIAL, loaded.units)
            assertEquals(Sport.GRAVEL, loaded.defaultSport)
            assertEquals(7, loaded.heatmapRefreshDays)
            assertEquals(custom, loaded.speedFor(Sport.HIKING))
            assertEquals(true, loaded.onboardingDone)

            repo.update { it.copy(heatmapRefreshDays = null, speeds = emptyMap()) }
            assertEquals(null, repo.load().heatmapRefreshDays)
            assertEquals(emptyMap<Sport, SpeedReference>(), repo.load().speeds)
            second.cancel()
        }
}
