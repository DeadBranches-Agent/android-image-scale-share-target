package ca.urbanlight.imagescale.log

import android.content.Context
import android.net.Uri
import android.widget.Toast
import ca.urbanlight.imagescale.data.SettingsRepository
import ca.urbanlight.imagescale.log.LogEvent.Companion.toJsonlLine
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Appends one JSONL line per event to the user-chosen log document.
 * The stream is opened in append mode and closed per write, so logging keeps
 * working across process restarts and from any component.
 */
class DebugLogger(
    private val context: Context,
    private val repository: SettingsRepository,
    private val scope: CoroutineScope,
) {

    fun now(): String = Instant.now().toString()

    /** Fire-and-forget; a no-op unless logging is active. */
    fun log(event: LogEvent) {
        scope.launch(Dispatchers.IO) { write(event) }
    }

    suspend fun write(event: LogEvent) {
        val uriString = repository.currentLogUri() ?: return
        try {
            val stream = context.contentResolver.openOutputStream(Uri.parse(uriString), "wa")
                ?: throw IllegalStateException("provider returned no stream")
            stream.use { it.write(event.toJsonlLine().toByteArray(Charsets.UTF_8)) }
        } catch (e: Exception) {
            // Log file unusable (revoked permission, provider without append, …):
            // disable logging so we don't fail on every event.
            repository.setActiveLogUri(null)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Debug logging stopped: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
