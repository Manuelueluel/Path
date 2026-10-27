package it.traccia.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import it.traccia.app.navigation.TracciaNavHost
import it.traccia.core.storage.prefs.UserPreferencesRepository
import it.traccia.core.ui.format.LocalUnitSystem
import it.traccia.core.ui.theme.TracciaTheme
import it.traccia.feature.gpx.GpxImporter
import it.traccia.feature.gpx.ImportError
import it.traccia.feature.gpx.ImportResult
import it.traccia.feature.planner.PlannerCommands
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import it.traccia.feature.gpx.R as GpxR

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var gpxImporter: GpxImporter

    @Inject
    lateinit var plannerCommands: PlannerCommands

    @Inject
    lateinit var userPreferences: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val units by remember { userPreferences.preferences.map { it.units } }
                .collectAsStateWithLifecycle(userPreferences.preferences.value.units)
            // Decided once per task: the stored flag must be read before choosing the first screen.
            var showOnboarding by rememberSaveable { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(Unit) {
                if (showOnboarding == null) showOnboarding = !userPreferences.load().onboardingDone
            }
            TracciaTheme {
                CompositionLocalProvider(LocalUnitSystem provides units) {
                    showOnboarding?.let { TracciaNavHost(startWithOnboarding = it) }
                }
            }
        }
        // Only on a fresh start: after a configuration change the intent was already handled.
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A GPX opened or shared from another app is imported and opened in the planner (M7.4, M7.5). */
    private fun handleIntent(intent: Intent?) {
        val uri = gpxUri(intent) ?: return
        lifecycleScope.launch {
            val message =
                when (val result = gpxImporter.import(uri)) {
                    is ImportResult.Imported -> {
                        plannerCommands.open(result.routeId)
                        getString(GpxR.string.gpx_imported, result.name)
                    }
                    is ImportResult.Failed ->
                        getString(
                            when (result.reason) {
                                ImportError.NOT_GPX -> GpxR.string.gpx_import_not_gpx
                                ImportError.EMPTY -> GpxR.string.gpx_import_empty
                                ImportError.UNREADABLE -> GpxR.string.gpx_import_unreadable
                            },
                        )
                }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun gpxUri(intent: Intent?): Uri? =
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            else -> null
        }
}
