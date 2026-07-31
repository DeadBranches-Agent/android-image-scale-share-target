package ca.urbanlight.imagescale.log

import ca.urbanlight.imagescale.data.AppConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed class LogEvent {
    abstract val timestamp: String

    @Serializable
    @SerialName("settings_changed")
    data class SettingsChanged(
        override val timestamp: String,
        val config: AppConfig,
    ) : LogEvent()

    @Serializable
    @SerialName("share_received")
    data class ShareReceived(
        override val timestamp: String,
        val targetId: String,
        val targetLabel: String,
        val imageCount: Int,
    ) : LogEvent()

    @Serializable
    @SerialName("image_processed")
    data class ImageProcessed(
        override val timestamp: String,
        val targetId: String,
        val sourceName: String,
        val outputName: String,
        val originalWidth: Int,
        val originalHeight: Int,
        val outputWidth: Int,
        val outputHeight: Int,
        val quality: Int,
        val scaleFactorPercent: Int,
        val outputBytes: Long,
        val durationMs: Long,
    ) : LogEvent()

    @Serializable
    @SerialName("job_paused")
    data class JobPaused(
        override val timestamp: String,
        val targetId: String,
        val processed: Int,
        val total: Int,
    ) : LogEvent()

    @Serializable
    @SerialName("job_resumed")
    data class JobResumed(
        override val timestamp: String,
        val targetId: String,
        val processed: Int,
        val total: Int,
    ) : LogEvent()

    @Serializable
    @SerialName("job_cancelled")
    data class JobCancelled(
        override val timestamp: String,
        val targetId: String,
        val processed: Int,
        val total: Int,
        val deletedOutputs: Boolean,
    ) : LogEvent()

    @Serializable
    @SerialName("job_completed")
    data class JobCompleted(
        override val timestamp: String,
        val targetId: String,
        val processed: Int,
        val failed: Int,
        val total: Int,
        val durationMs: Long,
    ) : LogEvent()

    @Serializable
    @SerialName("error")
    data class Error(
        override val timestamp: String,
        val context: String,
        val message: String,
    ) : LogEvent()

    companion object {
        private val json = Json {
            encodeDefaults = true
            classDiscriminator = "type"
        }

        fun LogEvent.toJsonlLine(): String = json.encodeToString(serializer(), this) + "\n"
    }
}
