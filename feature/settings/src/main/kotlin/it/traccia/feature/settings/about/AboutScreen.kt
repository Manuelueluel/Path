package it.traccia.feature.settings.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.traccia.core.ui.theme.TracciaTheme
import it.traccia.feature.settings.R

/** App version, licenses of data and libraries, and the note on the Strava heatmap (M8.7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(
    versionName: String,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.about_app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.about_version, versionName), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_source_code)) },
                    supportingContent = { Text(SOURCE_URL) },
                    modifier = Modifier.clickable(role = Role.Button) { uriHandler.openUri(SOURCE_URL) },
                )
            }
            item { HeatmapNote() }
            item { SectionTitle(stringResource(R.string.about_data_section)) }
            items(dataCredits, key = { it.name }) { CreditItem(it) { uriHandler.openUri(it.url) } }
            item { SectionTitle(stringResource(R.string.about_software_section)) }
            items(softwareCredits, key = { it.name }) { CreditItem(it) { uriHandler.openUri(it.url) } }
        }
    }
}

@Composable
private fun HeatmapNote() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.about_heatmap_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.about_heatmap_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CreditItem(
    credit: Credit,
    onOpen: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(credit.name) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(credit.role))
                Text(credit.notice, style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.about_license, credit.license),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),
    )
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

private const val SOURCE_URL = "https://github.com/Manuelueluel/Path"

@Preview
@Composable
private fun AboutScreenPreview() {
    TracciaTheme { AboutScreen(versionName = "0.1.0", onBack = {}) }
}
