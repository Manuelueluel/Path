package it.traccia.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.traccia.core.common.SpeedReference
import it.traccia.core.common.Sport
import it.traccia.core.common.UnitSystem
import it.traccia.core.ui.permission.AppPermission
import it.traccia.core.ui.permission.PermissionExplanation
import it.traccia.core.ui.permission.PermissionStatus
import it.traccia.core.ui.permission.openAppSettings
import it.traccia.core.ui.permission.rememberPermissionLauncher
import it.traccia.core.ui.sport.label
import it.traccia.core.ui.theme.TracciaTheme
import it.traccia.feature.map.style.MapStyle
import it.traccia.feature.map.R as MapR

@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onOpenHeatmap: () -> Unit,
    onOpenAbout: () -> Unit,
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
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenOfflineMaps = onOpenOfflineMaps,
        onOpenHeatmap = onOpenHeatmap,
        onOpenAbout = onOpenAbout,
        onRequestPermission = requestPermission,
        onOpenAppSettings = { context.openAppSettings() },
    )
    uiState.speedEditor?.let { SpeedDialog(it, uiState.units, viewModel::onEvent) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onOpenHeatmap: () -> Unit,
    onOpenAbout: () -> Unit,
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
            generalSection(uiState, onEvent)
            speedsSection(uiState, onEvent)
            mapsSection(uiState, onEvent, onOpenOfflineMaps, onOpenHeatmap)
            item { SectionTitle(stringResource(R.string.settings_permissions_section)) }
            items(uiState.permissions, key = { it.permission }) { item ->
                PermissionExplanation(
                    permission = item.permission,
                    status = item.status,
                    onRequest = { onRequestPermission(item.permission) },
                    onOpenSettings = onOpenAppSettings,
                )
            }
            item { SectionTitle(stringResource(R.string.settings_about_section)) }
            item {
                NavigationItem(stringResource(R.string.settings_about), stringResource(R.string.settings_about_description), onOpenAbout)
            }
        }
    }
}

private fun LazyListScope.generalSection(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    item { SectionTitle(stringResource(R.string.settings_general_section)) }
    item {
        Setting(stringResource(R.string.settings_units)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                UnitSystem.entries.forEachIndexed { i, units ->
                    SegmentedButton(
                        selected = uiState.units == units,
                        onClick = { onEvent(SettingsEvent.UnitsSelected(units)) },
                        shape = SegmentedButtonDefaults.itemShape(i, UnitSystem.entries.size),
                    ) {
                        Text(
                            stringResource(
                                when (units) {
                                    UnitSystem.METRIC -> R.string.settings_units_metric
                                    UnitSystem.IMPERIAL -> R.string.settings_units_imperial
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
    item {
        Setting(stringResource(R.string.settings_default_sport), stringResource(R.string.settings_default_sport_description)) {
            ChipRow(Sport.entries, uiState.defaultSport, { stringResource(it.label()) }) {
                onEvent(SettingsEvent.DefaultSportSelected(it))
            }
        }
    }
    item {
        Setting(stringResource(R.string.settings_map_style)) {
            ChipRow(MapStyle.entries, uiState.mapStyle, { stringResource(it.label()) }) { onEvent(SettingsEvent.MapStyleSelected(it)) }
        }
    }
}

private fun LazyListScope.speedsSection(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    item { SectionTitle(stringResource(R.string.settings_speeds_section)) }
    items(uiState.speeds, key = { it.sport }) { speed ->
        val summary = speedSummary(speed.display, uiState.units)
        ListItem(
            headlineContent = { Text(stringResource(speed.sport.label())) },
            supportingContent = {
                Text(if (speed.isCustom) summary + " · " + stringResource(R.string.settings_speed_custom) else summary)
            },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            modifier = Modifier.clickable { onEvent(SettingsEvent.SpeedEditOpened(speed.sport)) },
        )
    }
}

private fun LazyListScope.mapsSection(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onOpenHeatmap: () -> Unit,
) {
    item { SectionTitle(stringResource(R.string.settings_maps_section)) }
    item {
        NavigationItem(
            stringResource(R.string.settings_offline_maps),
            stringResource(R.string.settings_offline_maps_description),
            onOpenOfflineMaps,
        )
    }
    item {
        NavigationItem(stringResource(R.string.settings_heatmap), stringResource(R.string.settings_heatmap_description), onOpenHeatmap)
    }
    item {
        Setting(stringResource(R.string.settings_heatmap_refresh), stringResource(R.string.settings_heatmap_refresh_description)) {
            ChipRow(uiState.heatmapRefreshChoices, uiState.heatmapRefreshDays, { pluralStringResource(R.plurals.settings_days, it, it) }) {
                onEvent(SettingsEvent.HeatmapRefreshSelected(it))
            }
        }
    }
}

@Composable
internal fun speedSummary(
    display: SpeedInput,
    units: UnitSystem,
): String {
    val (horizontal, vertical) = speedUnits(units)
    val descent = display.descent
    return if (descent == null) {
        stringResource(R.string.settings_speed_summary, display.flat, horizontal, display.ascent, vertical)
    } else {
        stringResource(R.string.settings_speed_summary_descent, display.flat, horizontal, display.ascent, vertical, descent)
    }
}

/** Labels of the horizontal and vertical speed units. */
@Composable
internal fun speedUnits(units: UnitSystem): Pair<String, String> =
    when (units) {
        UnitSystem.METRIC -> stringResource(R.string.unit_kmh) to stringResource(R.string.unit_mh)
        UnitSystem.IMPERIAL -> stringResource(R.string.unit_mph) to stringResource(R.string.unit_fth)
    }

private fun MapStyle.label(): Int =
    when (this) {
        MapStyle.TOPO -> MapR.string.map_style_topo
        MapStyle.MINIMAL -> MapR.string.map_style_minimal
    }

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun Setting(
    title: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

@Composable
private fun <T> ChipRow(
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label(value)) })
        }
    }
}

@Composable
private fun NavigationItem(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    TracciaTheme {
        SettingsContent(
            uiState =
                SettingsUiState(
                    permissions =
                        listOf(
                            PermissionItem(AppPermission.LOCATION, PermissionStatus.GRANTED),
                            PermissionItem(AppPermission.NOTIFICATIONS, PermissionStatus.NOT_REQUESTED),
                        ),
                    speeds =
                        listOf(Sport.HIKING, Sport.MTB).map {
                            val reference = SpeedReference.defaultFor(it)
                            val onFoot = it == Sport.HIKING
                            SportSpeed(it, reference, onFoot, false, SpeedInput.from(reference, UnitSystem.METRIC, onFoot))
                        },
                ),
            onEvent = {},
            onBack = {},
            onOpenOfflineMaps = {},
            onOpenHeatmap = {},
            onOpenAbout = {},
            onRequestPermission = {},
            onOpenAppSettings = {},
        )
    }
}
