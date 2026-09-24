package ca.urbanlight.imagescale.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import ca.urbanlight.imagescale.App
import ca.urbanlight.imagescale.R

/** Builds the conversion-progress notification: filename, one batch progress bar, iC/nT, Pause/Stop. */
class JobNotification(private val context: Context) {

    fun build(progress: JobProgress, paused: Boolean): Notification {
        val content = RemoteViews(context.packageName, R.layout.notification_progress).apply {
            setTextViewText(R.id.current_file_name, progress.currentFileName.ifBlank { "Preparing…" })
            setProgressBar(R.id.overall_progress, 100, progress.overallPercent, false)
            setTextViewText(R.id.overall_count, "${progress.currentIndex}/${progress.total}")
        }

        val pauseAction = if (paused) {
            NotificationCompat.Action(0, context.getString(R.string.resume), service(ScaleService.ACTION_RESUME))
        } else {
            NotificationCompat.Action(0, context.getString(R.string.pause), service(ScaleService.ACTION_PAUSE))
        }

        return NotificationCompat.Builder(context, App.PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(content)
            .setCustomBigContentView(content)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(pauseAction)
            .addAction(
                NotificationCompat.Action(0, context.getString(R.string.stop), service(ScaleService.ACTION_STOP))
            )
            // Swiping the notification away (possible for FGS notifications on
            // Android 14+) asks for the same confirmation as Stop.
            .setDeleteIntent(service(ScaleService.ACTION_STOP))
            .build()
    }

    private fun service(action: String): PendingIntent =
        PendingIntent.getService(
            context,
            action.hashCode(),
            Intent(context, ScaleService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
