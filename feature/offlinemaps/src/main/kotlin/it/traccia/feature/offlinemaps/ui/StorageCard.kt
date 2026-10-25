package it.traccia.feature.offlinemaps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import it.traccia.feature.offlinemaps.R
import it.traccia.feature.offlinemaps.storage.StorageKind
import it.traccia.feature.offlinemaps.storage.StorageLocation

/** Space used by the maps and choice of the volume for new downloads (M2.5, M2.8). */
@Composable
internal fun StorageCard(
    locations: List<StorageLocation>,
    selectedId: String?,
    usedBytes: Long,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.offline_storage_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.offline_storage_used, formatBytes(context, usedBytes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            locations.forEach { location ->
                val label =
                    stringResource(
                        if (location.kind == StorageKind.INTERNAL) R.string.offline_storage_internal else R.string.offline_storage_sd,
                    )
                val free = stringResource(R.string.offline_storage_free, formatBytes(context, location.freeBytes))
                if (locations.size > 1) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(location.id == selectedId, role = Role.RadioButton) { onSelect(location.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = location.id == selectedId, onClick = null)
                        Text("$label · $free", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                } else {
                    Text("$label · $free", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
