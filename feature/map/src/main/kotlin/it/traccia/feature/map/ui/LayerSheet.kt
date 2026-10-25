package it.traccia.feature.map.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import it.traccia.feature.map.HeatmapUi
import it.traccia.feature.map.MapEvent
import it.traccia.feature.map.R
import it.traccia.feature.map.style.MapStyle

/** Map style and layer selection (outside of route drawing). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LayerSheet(
    style: MapStyle,
    contoursVisible: Boolean,
    hillshadeVisible: Boolean,
    onStyleSelected: (MapStyle) -> Unit,
    onContoursToggled: (Boolean) -> Unit,
    onHillshadeToggled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    heatmap: HeatmapUi,
    onHeatmapEvent: (MapEvent) -> Unit,
    onOpenStravaLogin: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(stringResource(R.string.map_layers_style))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MapStyle.entries.forEach { option ->
                    StyleOption(option, selected = option == style, onClick = { onStyleSelected(option) }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.map_layers_overlays))
            LayerSwitch(stringResource(R.string.map_layer_contours), contoursVisible, onContoursToggled)
            LayerSwitch(stringResource(R.string.map_layer_hillshade), hillshadeVisible, onHillshadeToggled)
            HeatmapSection(heatmap, onHeatmapEvent, onOpenStravaLogin)
            TextButton(onClick = onOpenOfflineMaps, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.map_manage_offline_maps))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun StyleOption(
    style: MapStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, description) =
        when (style) {
            MapStyle.TOPO -> R.string.map_style_topo to R.string.map_style_topo_description
            MapStyle.MINIMAL -> R.string.map_style_minimal to R.string.map_style_minimal_description
        }
    OutlinedCard(
        modifier = modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        border =
            BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
            Column(Modifier.padding(top = 6.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun LayerSwitch(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onToggle, role = Role.Switch)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
