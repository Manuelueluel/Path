package it.traccia.core.ui.permission

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.traccia.core.ui.R
import it.traccia.core.ui.theme.TracciaTheme

/** Card explaining why a permission is needed, with the action matching its [status]. */
@Composable
fun PermissionExplanation(
    permission: AppPermission,
    status: PermissionStatus,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val texts = permission.texts()
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(texts.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(texts.title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(texts.explanation), style = MaterialTheme.typography.bodyMedium)
            when (status) {
                PermissionStatus.GRANTED ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.permission_granted), style = MaterialTheme.typography.labelLarge)
                    }
                PermissionStatus.NOT_REQUESTED, PermissionStatus.DENIED ->
                    FilledTonalButton(onClick = onRequest) { Text(stringResource(R.string.permission_allow)) }
                PermissionStatus.PERMANENTLY_DENIED -> {
                    Text(
                        stringResource(R.string.permission_permanently_denied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.permission_open_settings)) }
                }
            }
        }
    }
}

private class PermissionTexts(
    val icon: ImageVector,
    @param:StringRes val title: Int,
    @param:StringRes val explanation: Int,
)

private fun AppPermission.texts(): PermissionTexts =
    when (this) {
        AppPermission.LOCATION ->
            PermissionTexts(Icons.Filled.LocationOn, R.string.permission_location_title, R.string.permission_location_explanation)
        AppPermission.NOTIFICATIONS ->
            PermissionTexts(
                Icons.Filled.Notifications,
                R.string.permission_notifications_title,
                R.string.permission_notifications_explanation,
            )
    }

@Preview
@Composable
private fun PermissionExplanationPreview() {
    TracciaTheme {
        PermissionExplanation(AppPermission.LOCATION, PermissionStatus.PERMANENTLY_DENIED, onRequest = {}, onOpenSettings = {})
    }
}
