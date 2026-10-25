package it.traccia.feature.offlinemaps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.traccia.core.ui.permission.AppPermission
import it.traccia.core.ui.permission.rememberPermissionLauncher
import it.traccia.feature.offlinemaps.ui.RegionRow
import it.traccia.feature.offlinemaps.ui.StorageCard
import it.traccia.feature.offlinemaps.ui.formatBytes

@Composable
internal fun OfflineMapsScreen(
    onBack: () -> Unit,
    viewModel: OfflineMapsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val requestPermission =
        rememberPermissionLauncher { viewModel.onEvent(OfflineMapsEvent.NotificationPermissionResult(it)) }
    val catalogUnavailable = stringResource(R.string.offline_catalog_unavailable)
    val storageFull = stringResource(R.string.offline_storage_full)

    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(OfflineMapsEvent.Resumed)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OfflineMapsEffect.RequestNotificationPermission -> requestPermission(AppPermission.NOTIFICATIONS)
                is OfflineMapsEffect.Show ->
                    snackbarHostState.showSnackbar(
                        when (effect.message) {
                            OfflineMessage.CATALOG_UNAVAILABLE -> catalogUnavailable
                            OfflineMessage.STORAGE_FULL -> storageFull
                        },
                    )
            }
        }
    }

    OfflineMapsContent(uiState, snackbarHostState, viewModel::onEvent, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineMapsContent(
    uiState: OfflineMapsUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (OfflineMapsEvent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.offline_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.offline_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(OfflineMapsEvent.Refresh) }, enabled = !uiState.isRefreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.offline_refresh))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.isRefreshing) item { LinearProgressIndicator(Modifier.padding(bottom = 8.dp)) }
            item {
                StorageCard(
                    locations = uiState.locations,
                    selectedId = uiState.selectedLocationId,
                    usedBytes = uiState.usedBytes,
                    onSelect = { onEvent(OfflineMapsEvent.SelectLocation(it)) },
                )
            }
            if (uiState.installed.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.offline_section_installed)) }
                items(uiState.installed, key = { it.id }) { RegionRow(it, onEvent) }
            }
            item { SectionTitle(stringResource(R.string.offline_section_available)) }
            if (uiState.available.isEmpty() && !uiState.isRefreshing) {
                item {
                    Text(
                        stringResource(
                            if (uiState.catalogUnavailable) R.string.offline_catalog_empty_offline else R.string.offline_catalog_empty,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(uiState.available, key = { it.id }) { RegionRow(it, onEvent) }
        }
    }

    uiState.pendingDeletion?.let { item ->
        AlertDialog(
            onDismissRequest = { onEvent(OfflineMapsEvent.DismissDelete) },
            title = { Text(stringResource(R.string.offline_delete_title, item.name)) },
            text = { Text(stringResource(R.string.offline_delete_message, formatBytes(LocalContext.current, item.sizeBytes))) },
            confirmButton = {
                TextButton(onClick = { onEvent(OfflineMapsEvent.ConfirmDelete) }) { Text(stringResource(R.string.offline_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(OfflineMapsEvent.DismissDelete) }) { Text(stringResource(R.string.offline_action_keep)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}
