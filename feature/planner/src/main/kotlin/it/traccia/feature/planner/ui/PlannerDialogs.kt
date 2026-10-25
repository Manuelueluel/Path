package it.traccia.feature.planner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.traccia.core.common.Sport
import it.traccia.core.ui.sport.label
import it.traccia.feature.planner.PlannerEvent
import it.traccia.feature.planner.PlannerUiState
import it.traccia.feature.planner.R

/** The single option of a tapped point (M5.3). */
@Composable
fun DeletePointButton(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, tonalElevation = 4.dp, shadowElevation = 4.dp) {
        TextButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Text(stringResource(R.string.planner_delete_point), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** Route settings, outside the drawing mode (M5.9): sport, routing and options, save and export. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSheet(
    state: PlannerUiState,
    onEvent: (PlannerEvent) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = {
        onEvent(PlannerEvent.CloseSheet)
    }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(state.routeName ?: stringResource(R.string.planner_new_route), style = MaterialTheme.typography.titleLarge)
            Title(stringResource(R.string.planner_sport))
            SportChips(state.sport) { onEvent(PlannerEvent.SportSelected(it)) }
            Title(stringResource(R.string.planner_routing))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !state.routing.straightLine,
                    onClick = { onEvent(PlannerEvent.StraightLineSelected(false)) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.planner_routing_follow)) }
                SegmentedButton(
                    selected = state.routing.straightLine,
                    onClick = { onEvent(PlannerEvent.StraightLineSelected(true)) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.planner_routing_straight)) }
            }
            if (!state.routing.straightLine && state.availableOptions.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.availableOptions.forEach { option ->
                        FilterChip(
                            selected = option in state.routing.options,
                            onClick = { onEvent(PlannerEvent.OptionToggled(option)) },
                            label = { Text(optionLabel(option)?.let { stringResource(it) } ?: option) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilledTonalButton(
                    onClick = { onEvent(PlannerEvent.SaveClicked) },
                    enabled = state.canSave,
                ) { Text(stringResource(R.string.planner_save)) }
                OutlinedButton(
                    onClick = { onEvent(PlannerEvent.ExportClicked) },
                    enabled = state.canSave,
                ) { Text(stringResource(R.string.planner_export)) }
            }
            TextButton(onClick = { onEvent(PlannerEvent.NewRoute) }) { Text(stringResource(R.string.planner_new_route_action)) }
        }
    }
}

/** Save with name and sport: the only question, asked once the drawing is done (M5.10). */
@Composable
fun SaveRouteDialog(
    initialName: String,
    initialSport: Sport,
    onSave: (String, Sport) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var sport by rememberSaveable { mutableStateOf(initialSport) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.planner_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.planner_save_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SportChips(sport) { sport = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, sport) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.planner_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.planner_cancel)) } },
    )
}

@Composable
private fun SportChips(
    selected: Sport,
    onSelect: (Sport) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sport.entries.forEach { sport ->
            FilterChip(selected = sport == selected, onClick = { onSelect(sport) }, label = { Text(stringResource(sport.label())) })
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

private fun optionLabel(option: String): Int? =
    when (option) {
        "avoid_steps" -> R.string.planner_option_avoid_steps
        "avoid_climbs" -> R.string.planner_option_avoid_climbs
        "avoid_traffic" -> R.string.planner_option_avoid_traffic
        "prefer_unpaved" -> R.string.planner_option_prefer_unpaved
        else -> null
    }
