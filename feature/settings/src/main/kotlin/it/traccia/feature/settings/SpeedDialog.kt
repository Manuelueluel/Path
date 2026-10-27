package it.traccia.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.traccia.core.common.UnitSystem
import it.traccia.core.ui.sport.label

/** Reference speeds of one sport: shown from the settings, never while drawing (M8.2). */
@Composable
internal fun SpeedDialog(
    editor: SpeedEditor,
    units: UnitSystem,
    onEvent: (SettingsEvent) -> Unit,
) {
    val (horizontal, vertical) = speedUnits(units)
    AlertDialog(
        onDismissRequest = { onEvent(SettingsEvent.SpeedEditDismissed) },
        title = { Text(stringResource(R.string.settings_speed_edit, stringResource(editor.sport.label()))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SpeedField(stringResource(R.string.settings_speed_flat), horizontal, editor.input.flat, editor.isInvalid) {
                    onEvent(SettingsEvent.SpeedFieldChanged(SpeedField.FLAT, it))
                }
                SpeedField(stringResource(R.string.settings_speed_ascent), vertical, editor.input.ascent, editor.isInvalid) {
                    onEvent(SettingsEvent.SpeedFieldChanged(SpeedField.ASCENT, it))
                }
                editor.input.descent?.let { descent ->
                    SpeedField(stringResource(R.string.settings_speed_descent), vertical, descent, editor.isInvalid) {
                        onEvent(SettingsEvent.SpeedFieldChanged(SpeedField.DESCENT, it))
                    }
                }
                if (editor.isInvalid) {
                    Text(
                        stringResource(R.string.settings_speed_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(SettingsEvent.SpeedEditConfirmed) }) { Text(stringResource(R.string.settings_speed_save)) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SettingsEvent.SpeedEditReset) }) { Text(stringResource(R.string.settings_speed_reset)) }
        },
    )
}

@Composable
private fun SpeedField(
    label: String,
    unit: String,
    value: String,
    isError: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = { Text(unit) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
