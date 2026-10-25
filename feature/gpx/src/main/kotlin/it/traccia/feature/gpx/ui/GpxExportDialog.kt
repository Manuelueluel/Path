package it.traccia.feature.gpx.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.traccia.core.storage.route.Route
import it.traccia.feature.gpx.GpxContent
import it.traccia.feature.gpx.GpxExportEffect
import it.traccia.feature.gpx.GpxExportViewModel
import it.traccia.feature.gpx.R
import it.traccia.feature.gpx.Simplification

/** Export options and destinations: share (Suunto app, Drive, email...) or save to a file (M7.3). */
@Composable
fun GpxExportDialog(
    route: Route,
    onMessage: (Int) -> Unit,
    onDismiss: () -> Unit,
    viewModel: GpxExportViewModel = hiltViewModel(key = "gpx-export-${route.id}-${route.hashCode()}"),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.gpx_share_title)
    val saveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GPX_MIME)) { uri ->
            if (uri != null) viewModel.saveTo(uri)
        }
    LaunchedEffect(route) { viewModel.setRoute(route) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is GpxExportEffect.Share -> {
                    val send =
                        Intent(Intent.ACTION_SEND).apply {
                            type = GPX_MIME
                            putExtra(Intent.EXTRA_STREAM, effect.uri)
                            putExtra(Intent.EXTRA_SUBJECT, route.name)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(Intent.createChooser(send, shareTitle))
                    onDismiss()
                }
                GpxExportEffect.Saved -> {
                    onMessage(R.string.gpx_saved)
                    onDismiss()
                }
                GpxExportEffect.Failed -> onMessage(R.string.gpx_export_failed)
            }
        }
    }
    val options = state.options
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gpx_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.isWorking) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.gpx_content), style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    GpxContent.entries.forEachIndexed { i, content ->
                        SegmentedButton(
                            selected = options.content == content,
                            onClick = { viewModel.setOptions(options.copy(content = content)) },
                            shape = SegmentedButtonDefaults.itemShape(i, GpxContent.entries.size),
                        ) { Text(stringResource(content.label())) }
                    }
                }
                Text(stringResource(R.string.gpx_simplification), style = MaterialTheme.typography.titleSmall)
                Simplification.entries.forEach { level ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(options.simplification == level, role = Role.RadioButton) {
                                viewModel.setOptions(options.copy(simplification = level))
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = options.simplification == level, onClick = null)
                        Text(
                            stringResource(level.label(), state.pointCounts[level] ?: 0),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(options.waypoints, role = Role.Switch) { viewModel.setOptions(options.copy(waypoints = it)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.gpx_waypoints),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = options.waypoints, onCheckedChange = null)
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::share, enabled = !state.isWorking) { Text(stringResource(R.string.gpx_share)) } },
        dismissButton = {
            TextButton(
                onClick = { saveLauncher.launch(state.fileName) },
                enabled = !state.isWorking,
            ) { Text(stringResource(R.string.gpx_save_file)) }
        },
    )
}

private fun GpxContent.label() =
    when (this) {
        GpxContent.TRACK -> R.string.gpx_content_track
        GpxContent.ROUTE -> R.string.gpx_content_route
        GpxContent.BOTH -> R.string.gpx_content_both
    }

private fun Simplification.label() =
    when (this) {
        Simplification.NONE -> R.string.gpx_simplify_none
        Simplification.LIGHT -> R.string.gpx_simplify_light
        Simplification.MEDIUM -> R.string.gpx_simplify_medium
        Simplification.STRONG -> R.string.gpx_simplify_strong
    }

const val GPX_MIME = "application/gpx+xml"
