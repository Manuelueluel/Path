package it.traccia.feature.gpx

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.traccia.core.storage.route.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class GpxExportUiState(
    val route: Route? = null,
    val options: ExportOptions = ExportOptions(),
    val pointCounts: Map<Simplification, Int> = emptyMap(),
    val isWorking: Boolean = false,
) {
    val fileName: String get() = GpxConversion.fileName(route?.name.orEmpty())
}

sealed interface GpxExportEffect {
    data class Share(
        val uri: Uri,
    ) : GpxExportEffect

    data object Saved : GpxExportEffect

    data object Failed : GpxExportEffect
}

/** Export dialog (M7.2, M7.3). */
@HiltViewModel
class GpxExportViewModel
    @Inject
    constructor(
        private val exporter: GpxExporter,
        private val files: GpxFiles,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(GpxExportUiState())
        val uiState: StateFlow<GpxExportUiState> = _uiState.asStateFlow()
        private val _effects = Channel<GpxExportEffect>(Channel.BUFFERED)
        val effects: Flow<GpxExportEffect> = _effects.receiveAsFlow()

        fun setRoute(route: Route) {
            if (_uiState.value.route == route) return
            _uiState.update { it.copy(route = route, pointCounts = exporter.pointCounts(route)) }
        }

        fun setOptions(options: ExportOptions) = _uiState.update { it.copy(options = options) }

        fun share() = export { gpx -> _effects.send(GpxExportEffect.Share(files.shareable(_uiState.value.fileName, gpx))) }

        fun saveTo(uri: Uri) =
            export { gpx ->
                files.write(uri, gpx)
                _effects.send(GpxExportEffect.Saved)
            }

        private fun export(deliver: suspend (String) -> Unit) {
            val route = _uiState.value.route ?: return
            _uiState.update { it.copy(isWorking = true) }
            viewModelScope.launch {
                try {
                    deliver(exporter.gpx(route, _uiState.value.options))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Log.w(TAG, "GPX export failed", e)
                    _effects.send(GpxExportEffect.Failed)
                } finally {
                    _uiState.update { it.copy(isWorking = false) }
                }
            }
        }

        private companion object {
            const val TAG = "GpxExport"
        }
    }
