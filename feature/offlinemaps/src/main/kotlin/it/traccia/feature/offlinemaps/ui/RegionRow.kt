package it.traccia.feature.offlinemaps.ui

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.traccia.feature.offlinemaps.DownloadError
import it.traccia.feature.offlinemaps.OfflineMapsEvent
import it.traccia.feature.offlinemaps.R
import it.traccia.feature.offlinemaps.RegionItem
import it.traccia.feature.offlinemaps.RegionStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/** One region: name, size and data date, status and the actions allowed in that status. */
@Composable
internal fun RegionRow(
    item: RegionItem,
    onEvent: (OfflineMapsEvent) -> Unit,
) {
    val context = LocalContext.current
    val status = item.status
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (status is RegionStatus.Installed && status.updateAvailable) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            ),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle(context, item),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.isInstalled && status !is RegionStatus.Downloading && status !is RegionStatus.Queued) {
                    IconButton(onClick = { onEvent(OfflineMapsEvent.Delete(item.id)) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.offline_action_delete))
                    }
                }
            }
            StatusLine(item, status)
            Actions(item, status, onEvent)
        }
    }
}

@Composable
private fun StatusLine(
    item: RegionItem,
    status: RegionStatus,
) {
    val context = LocalContext.current
    when (status) {
        is RegionStatus.Downloading, is RegionStatus.Paused -> {
            val (bytes, total) =
                if (status is RegionStatus.Downloading) {
                    status.bytes to status.totalBytes
                } else {
                    (status as RegionStatus.Paused).bytes to
                        status.totalBytes
                }
            val fraction = if (total > 0) bytes.toFloat() / total else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp))
            Text(
                stringResource(
                    if (status is RegionStatus.Downloading) R.string.offline_status_downloading else R.string.offline_status_paused,
                    (fraction * PERCENT).toInt(),
                    formatBytes(context, bytes),
                    formatBytes(context, total),
                ),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        RegionStatus.Queued -> {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp))
            Text(stringResource(R.string.offline_status_queued), style = MaterialTheme.typography.labelMedium)
        }
        is RegionStatus.Failed ->
            Text(
                stringResource(
                    when (status.error) {
                        DownloadError.NETWORK -> R.string.offline_error_network
                        DownloadError.CHECKSUM -> R.string.offline_error_checksum
                        DownloadError.UNAVAILABLE -> R.string.offline_error_unavailable
                        DownloadError.STORAGE -> R.string.offline_error_storage
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        is RegionStatus.Installed ->
            if (status.updateAvailable) {
                Text(
                    stringResource(R.string.offline_update_available, formatVersion(item.catalogVersion.orEmpty())),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        RegionStatus.NotInstalled -> Unit
    }
}

@Composable
private fun Actions(
    item: RegionItem,
    status: RegionStatus,
    onEvent: (OfflineMapsEvent) -> Unit,
) {
    val downloadable = item.catalogRegion != null
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        when (status) {
            RegionStatus.NotInstalled ->
                if (downloadable) Primary(R.string.offline_action_download) { onEvent(OfflineMapsEvent.Download(item.id)) }
            is RegionStatus.Downloading, RegionStatus.Queued -> {
                Secondary(R.string.offline_action_cancel) { onEvent(OfflineMapsEvent.Cancel(item.id)) }
                Secondary(R.string.offline_action_pause) { onEvent(OfflineMapsEvent.Pause(item.id)) }
            }
            is RegionStatus.Paused -> {
                Secondary(R.string.offline_action_cancel) { onEvent(OfflineMapsEvent.Cancel(item.id)) }
                Primary(R.string.offline_action_resume) { onEvent(OfflineMapsEvent.Download(item.id)) }
            }
            is RegionStatus.Failed -> {
                Secondary(R.string.offline_action_cancel) { onEvent(OfflineMapsEvent.Cancel(item.id)) }
                if (downloadable) Primary(R.string.offline_action_retry) { onEvent(OfflineMapsEvent.Download(item.id)) }
            }
            is RegionStatus.Installed ->
                if (status.updateAvailable &&
                    downloadable
                ) {
                    Primary(R.string.offline_action_update) { onEvent(OfflineMapsEvent.Download(item.id)) }
                }
        }
    }
}

@Composable
private fun Primary(
    label: Int,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(label)) }
}

@Composable
private fun Secondary(
    label: Int,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) { Text(stringResource(label)) }
}

private fun subtitle(
    context: Context,
    item: RegionItem,
): String {
    val version = item.installedVersion ?: item.catalogVersion
    val size = formatBytes(context, item.sizeBytes)
    return if (version != null) context.getString(R.string.offline_size_and_date, size, formatVersion(version)) else size
}

internal fun formatBytes(
    context: Context,
    bytes: Long,
): String = Formatter.formatShortFileSize(context, bytes)

/** Catalog versions are ISO dates: show them in the user's locale. */
private fun formatVersion(version: String): String =
    try {
        LocalDate.parse(version).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
    } catch (_: DateTimeParseException) {
        version
    }

private const val PERCENT = 100
