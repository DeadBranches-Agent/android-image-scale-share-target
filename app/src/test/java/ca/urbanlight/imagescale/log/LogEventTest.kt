package ca.urbanlight.imagescale.log

import ca.urbanlight.imagescale.data.AppConfig
import ca.urbanlight.imagescale.data.ShareTargetConfig
import ca.urbanlight.imagescale.log.LogEvent.Companion.toJsonlLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogEventTest {

    private val ts = "2026-07-31T12:00:00Z"

    private val events: List<Pair<LogEvent, String>> = listOf(
        LogEvent.SettingsChanged(ts, AppConfig(targets = listOf(ShareTargetConfig(id = "a")))) to "settings_changed",
        LogEvent.ShareReceived(ts, "a", "Blog", 3) to "share_received",
        LogEvent.ImageProcessed(ts, "a", "in.png", "in_scaled.jpg", 4000, 3000, 2000, 1500, 85, 50, 123456, 250) to "image_processed",
        LogEvent.JobPaused(ts, "a", 1, 3) to "job_paused",
        LogEvent.JobResumed(ts, "a", 1, 3) to "job_resumed",
        LogEvent.JobCancelled(ts, "a", 2, 3, deletedOutputs = true) to "job_cancelled",
        LogEvent.JobCompleted(ts, "a", 3, 0, 3, 1200) to "job_completed",
        LogEvent.Error(ts, "exif", "boom\nwith newline") to "error",
    )

    @Test
    fun `every event is a single line ending with newline`() {
        for ((event, _) in events) {
            val line = event.toJsonlLine()
            assertTrue(line.endsWith("\n"))
            assertEquals("interior newline in: $line", -1, line.dropLast(1).indexOf('\n'))
        }
    }

    @Test
    fun `every event carries its type discriminator`() {
        for ((event, type) in events) {
            assertTrue(event.toJsonlLine().contains("\"type\":\"$type\""))
        }
    }

    @Test
    fun `timestamp is serialized`() {
        assertTrue(events.first().first.toJsonlLine().contains(ts))
    }
}
