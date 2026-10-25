package it.traccia.feature.library

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.traccia.core.common.Sport
import it.traccia.core.storage.route.RouteSummary
import it.traccia.core.ui.format.formatRouteDistance
import it.traccia.core.ui.sport.label
import it.traccia.feature.gpx.ImportError
import it.traccia.feature.gpx.ui.GpxExportDialog
import it.traccia.feature.library.ui.RouteThumbnail
import kotlin.math.roundToInt

@Composable
internal fun LibraryScreen(
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.onEvent(LibraryEvent.Import(uri))
        }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LibraryEffect.BackToMap -> onBack()
                is LibraryEffect.Imported -> snackbar.showSnackbar(context.getString(R.string.library_imported, effect.name))
                is LibraryEffect.ImportFailed ->
                    snackbar.showSnackbar(
                        context.getString(
                            when (effect.reason) {
                                ImportError.NOT_GPX -> R.string.library_import_not_gpx
                                ImportError.EMPTY -> R.string.library_import_empty
                                ImportError.UNREADABLE -> R.string.library_import_unreadable
                            },
                        ),
                    )
            }
        }
    }
    LibraryContent(
        state = state,
        snackbar = snackbar,
        onEvent = viewModel::onEvent,
        // Many apps share GPX as generic XML or binary data.
        onImport = { importLauncher.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")) },
        onBack = onBack,
    )
    state.exporting?.let { route ->
        GpxExportDialog(route, onMessage = { }, onDismiss = { viewModel.onEvent(LibraryEvent.DialogDismissed) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryContent(
    state: LibraryUiState,
    snackbar: SnackbarHostState,
    onEvent: (LibraryEvent) -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isSelecting) {
                            stringResource(R.string.library_selected, state.selection.size)
                        } else {
                            stringResource(R.string.library_title)
                        },
                    )
                },
                navigationIcon = {
                    if (state.isSelecting) {
                        IconButton(onClick = { onEvent(LibraryEvent.ClearSelection) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.library_clear_selection))
                        }
                    } else {
                        IconButton(
                            onClick = onBack,
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_back)) }
                    }
                },
                actions = {
                    if (state.isSelecting) {
                        TextButton(
                            onClick = { onEvent(LibraryEvent.ShowSelectionOnMap) },
                        ) { Text(stringResource(R.string.library_show_on_map)) }
                    } else if (state.shownOnMap.isNotEmpty()) {
                        TextButton(
                            onClick = { onEvent(LibraryEvent.HideShownRoutes) },
                        ) { Text(stringResource(R.string.library_hide_from_map)) }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isSelecting) {
                ExtendedFloatingActionButton(
                    onClick = onImport,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.library_import)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isImporting) LinearProgressIndicator(Modifier.fillMaxWidth())
            Filters(state, onEvent)
            if (state.routes.isEmpty()) {
                Text(
                    stringResource(if (state.totalCount == 0) R.string.library_empty else R.string.library_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(state.routes, key = { it.id }) { route ->
                    RouteRow(
                        route,
                        selected = route.id in state.selection,
                        selecting = state.isSelecting,
                        shown =
                            route.id in state.shownOnMap,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
    state.renaming?.let { route ->
        RenameDialog(
            route.name,
            onConfirm = { onEvent(LibraryEvent.RenameConfirmed(it)) },
            onDismiss = { onEvent(LibraryEvent.DialogDismissed) },
        )
    }
    state.deleting?.let { route ->
        AlertDialog(
            onDismissRequest = { onEvent(LibraryEvent.DialogDismissed) },
            title = { Text(stringResource(R.string.library_delete_title, route.name)) },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(LibraryEvent.DeleteConfirmed) },
                ) { Text(stringResource(R.string.library_delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(LibraryEvent.DialogDismissed) },
                ) { Text(stringResource(R.string.library_cancel)) }
            },
        )
    }
}

@Composable
private fun Filters(
    state: LibraryUiState,
    onEvent: (LibraryEvent) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onEvent(LibraryEvent.QueryChanged(it)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.library_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.sport == null, onClick = {
                onEvent(LibraryEvent.SportFilter(null))
            }, label = { Text(stringResource(R.string.library_all_sports)) })
            Sport.entries.forEach { sport ->
                FilterChip(selected = state.sport == sport, onClick = {
                    onEvent(LibraryEvent.SportFilter(sport))
                }, label = { Text(stringResource(sport.label())) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibrarySort.entries.forEach { sort ->
                FilterChip(
                    selected = state.sort == sort,
                    onClick = { onEvent(LibraryEvent.SortChanged(sort)) },
                    label = {
                        Text(
                            stringResource(
                                when (sort) {
                                    LibrarySort.RECENT -> R.string.library_sort_recent
                                    LibrarySort.NAME -> R.string.library_sort_name
                                    LibrarySort.DISTANCE -> R.string.library_sort_distance
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RouteRow(
    route: RouteSummary,
    selected: Boolean,
    selecting: Boolean,
    shown: Boolean,
    onEvent: (LibraryEvent) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val copyName = stringResource(R.string.library_copy_name, route.name)
    ListItem(
        modifier =
            Modifier.combinedClickable(
                onClick = { if (selecting) onEvent(LibraryEvent.ToggleSelection(route.id)) else onEvent(LibraryEvent.Open(route.id)) },
                onLongClick = { onEvent(LibraryEvent.ToggleSelection(route.id)) },
            ),
        leadingContent = {
            Box {
                RouteThumbnail(route.preview)
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.library_selected_route),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        headlineContent = { Text(route.name) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.library_route_details,
                    stringResource(route.sport.label()),
                    formatRouteDistance(route.distanceMeters),
                    route.ascentMeters.roundToInt(),
                    DateUtils.getRelativeTimeSpanString(route.updatedAtMillis).toString(),
                ) + if (shown) " · " + stringResource(R.string.library_on_map) else "",
            )
        },
        trailingContent = {
            if (!selecting) {
                Box {
                    IconButton(
                        onClick = { menu = true },
                    ) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.library_actions)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MenuItem(R.string.library_export) { onEvent(LibraryEvent.Export(route.id)).also { menu = false } }
                        MenuItem(R.string.library_duplicate) { onEvent(LibraryEvent.Duplicate(route, copyName)).also { menu = false } }
                        MenuItem(R.string.library_rename) { onEvent(LibraryEvent.Rename(route)).also { menu = false } }
                        MenuItem(R.string.library_delete) { onEvent(LibraryEvent.Delete(route)).also { menu = false } }
                    }
                }
            }
        },
    )
}

@Composable
private fun MenuItem(
    label: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

@Composable
private fun RenameDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_rename)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.library_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}
