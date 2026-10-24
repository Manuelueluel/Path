package it.traccia.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import it.traccia.core.ui.theme.TracciaTheme

/** Home screen. The MapLibre map arrives with milestone M1; for now it is an empty canvas. */
@Composable
internal fun MapScreen(onOpenSettings: () -> Unit) {
    Scaffold { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = stringResource(R.string.map_placeholder),
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.map_open_settings))
            }
        }
    }
}

@Preview
@Composable
private fun MapScreenPreview() {
    TracciaTheme { MapScreen(onOpenSettings = {}) }
}
