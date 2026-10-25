package it.traccia.feature.heatmap.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.traccia.feature.heatmap.R
import it.traccia.feature.heatmap.session.SessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HeatmapAccountScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    viewModel: HeatmapAccountViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.reloadSizes()
        onPauseOrDispose { }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.heatmap_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.heatmap_back))
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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.heatmap_account), style = MaterialTheme.typography.titleSmall)
                        val session = uiState.session
                        Text(
                            when (session) {
                                SessionState.LoggedOut -> stringResource(R.string.heatmap_logged_out)
                                SessionState.Expired -> stringResource(R.string.heatmap_session_expired)
                                is SessionState.Active ->
                                    session.expiresAtMillis?.let {
                                        stringResource(
                                            R.string.heatmap_logged_in_until,
                                            DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                                        )
                                    } ?: stringResource(R.string.heatmap_logged_in)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                if (session ==
                                    SessionState.Expired
                                ) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (session !is SessionState.Active) {
                                FilledTonalButton(onClick = onLogin) { Text(stringResource(R.string.heatmap_login)) }
                            } else {
                                FilledTonalButton(onClick = onLogin) { Text(stringResource(R.string.heatmap_login_again)) }
                            }
                            if (session != SessionState.LoggedOut) {
                                OutlinedButton(onClick = viewModel::logout) { Text(stringResource(R.string.heatmap_logout)) }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.heatmap_rules, uiState.refreshAfterDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.heatmap_cache_title, Formatter.formatShortFileSize(context, uiState.totalBytes)),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (uiState.caches.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearAll) { Text(stringResource(R.string.heatmap_clear_all)) }
                    }
                }
            }
            if (uiState.caches.isEmpty()) {
                item { Text(stringResource(R.string.heatmap_cache_empty), style = MaterialTheme.typography.bodyMedium) }
            }
            items(uiState.caches, key = { it.sport }) { item ->
                ListItem(
                    headlineContent = { Text(heatmapSportLabel(item.sport)?.let { stringResource(it) } ?: item.sport) },
                    supportingContent = { Text(Formatter.formatShortFileSize(context, item.bytes)) },
                    trailingContent = {
                        TextButton(
                            onClick = { viewModel.clear(item.sport) },
                        ) { Text(stringResource(R.string.heatmap_clear)) }
                    },
                )
            }
        }
    }
}
