package ca.urbanlight.imagescale

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import ca.urbanlight.imagescale.data.SettingsRepository
import ca.urbanlight.imagescale.log.DebugLogger
import ca.urbanlight.imagescale.share.ShortcutPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsRepository by lazy { SettingsRepository(this) }
    val debugLogger by lazy { DebugLogger(this, settingsRepository, appScope) }
    val shortcutPublisher by lazy { ShortcutPublisher(this) }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                PROGRESS_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    companion object {
        const val PROGRESS_CHANNEL_ID = "conversion_progress"
    }
}
