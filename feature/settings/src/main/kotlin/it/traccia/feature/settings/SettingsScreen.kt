package it.traccia.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.traccia.core.ui.permission.AppPermission
import it.traccia.core.ui.permission.PermissionExplanation
import it.traccia.core.ui.permission.PermissionStatus
import it.traccia.core.ui.permission.openAppSettings
import it.traccia.core.ui.permission.rememberPermissionLauncher
import it.traccia.core.ui.theme.TracciaTheme

@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onOpenHeatmap: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestPermission =
        rememberPermissionLauncher { result -> viewModel.onEvent(SettingsEvent.PermissionResultReceived(result)) }

    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(SettingsEvent.Refresh)
        onPauseOrDispose { }
    }

    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onOpenOfflineMaps = onOpenOfflineMaps,
        onOpenHeatmap = onOpenHeatmap,
        onRequestPermission = requestPermission,
        onOpenAppSettings = { context.openAppSettings() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onOpenHeatmap: () -> Unit,
    onRequestPermission: (AppPermission) -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.settings_maps_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_offline_maps)) },
                    supportingContent = { Text(stringResource(R.string.settings_offline_maps_description)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenOfflineMaps),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_heatmap)) },
                    supportingContent = { Text(stringResource(R.string.settings_heatmap_description)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenHeatmap),
                )
            }
            item {
                Text(
                    stringResource(R.string.settings_permissions_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(uiState.permissions, key = { it.permission }) { item ->
                PermissionExplanation(
                    permission = item.permission,
                    status = item.status,
                    onRequest = { onRequestPermission(item.permission) },
                    onOpenSettings = onOpenAppSettings,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    TracciaTheme {
        SettingsContent(
            uiState =
                SettingsUiState(
                    listOf(
                        PermissionItem(AppPermission.LOCATION, PermissionStatus.GRANTED),
                        PermissionItem(AppPermission.NOTIFICATIONS, PermissionStatus.NOT_REQUESTED),
                    ),
                ),
            onBack = {},
            onOpenOfflineMaps = {},
            onOpenHeatmap = {},
            onRequestPermission = {},
            onOpenAppSettings = {},
        )
    }
}
