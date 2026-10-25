package it.traccia.feature.offlinemaps.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import it.traccia.feature.offlinemaps.R
import java.util.UUID

/** Progress notification of a region download, with a "Pausa" action. */
internal class DownloadNotifications(
    private val context: Context,
) {
    fun foregroundInfo(
        workId: UUID,
        regionId: String,
        regionName: String,
        bytes: Long,
        totalBytes: Long,
    ): ForegroundInfo {
        ensureChannel()
        val percent = if (totalBytes > 0) (bytes * PERCENT / totalBytes).toInt() else 0
        val notification: Notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getString(R.string.offline_notification_title, regionName))
                .setContentText(context.getString(R.string.offline_notification_progress, percent))
                .setProgress(PERCENT, percent, totalBytes <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .addAction(
                    0,
                    context.getString(R.string.offline_action_pause),
                    // Cancelling the work keeps the partial files: it is a pause.
                    WorkManager.getInstance(context).createCancelPendingIntent(workId),
                ).build()
        val id = NOTIFICATION_BASE + regionId.hashCode() % NOTIFICATION_RANGE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.offline_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "offline_downloads"
        const val PERCENT = 100
        const val NOTIFICATION_BASE = 1000
        const val NOTIFICATION_RANGE = 1000
    }
}
