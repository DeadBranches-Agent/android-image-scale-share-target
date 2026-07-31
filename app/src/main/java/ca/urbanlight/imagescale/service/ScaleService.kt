package ca.urbanlight.imagescale.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import ca.urbanlight.imagescale.App
import ca.urbanlight.imagescale.ConfirmCancelActivity
import ca.urbanlight.imagescale.data.ShareTargetConfig
import ca.urbanlight.imagescale.image.ImageProcessor
import ca.urbanlight.imagescale.io.SafDocumentSink
import ca.urbanlight.imagescale.log.LogEvent
import ca.urbanlight.imagescale.service.JobController.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Foreground service that owns one conversion job at a time. */
class ScaleService : Service() {

    private val app get() = application as App
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var notifier: JobNotification

    private var controller: JobController? = null
    private var jobRunner: Job? = null
    private var deleteOutputsOnCancel = false
    private val createdOutputs = mutableListOf<Uri>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier = JobNotification(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startJob(intent)
            ACTION_PAUSE -> controller?.let {
                it.pause()
                logJobEvent { ts, id, p -> LogEvent.JobPaused(ts, id, p.currentIndex, p.total) }
            }
            ACTION_RESUME -> controller?.let {
                it.resume()
                logJobEvent { ts, id, p -> LogEvent.JobResumed(ts, id, p.currentIndex, p.total) }
            }
            ACTION_STOP -> if (controller != null) {
                controller?.requestCancel()
                startActivity(
                    Intent(this, ConfirmCancelActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            ACTION_CONTINUE -> controller?.continueConversion()
            ACTION_CONFIRM_CANCEL -> {
                deleteOutputsOnCancel = intent.getBooleanExtra(EXTRA_DELETE_OUTPUTS, false)
                controller?.confirmCancel()
            }
        }
        if (controller == null && jobRunner == null) stopSelf()
        return START_NOT_STICKY
    }

    private var currentTargetId: String = ""

    private fun startJob(intent: Intent) {
        if (jobRunner?.isActive == true) {
            toastAsync("An image conversion is already running")
            return
        }
        val targetId = intent.getStringExtra(EXTRA_TARGET_ID) ?: return
        val uris = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_URIS, Uri::class.java)
            ?.filterNotNull().orEmpty()
        if (uris.isEmpty()) return

        val jobController = JobController(uris.size)
        controller = jobController
        currentTargetId = targetId
        deleteOutputsOnCancel = false
        createdOutputs.clear()

        startForegroundCompat(jobController)

        // Every progress or state change refreshes the notification (conflated by StateFlow).
        scope.launch {
            combine(jobController.progress, jobController.state) { p, s -> p to s }
                .collect { (progress, state) ->
                    val active = state == State.Running || state == State.Paused ||
                        state == State.AwaitingCancelConfirm
                    if (active && canPostNotifications()) {
                        try {
                            NotificationManagerCompat.from(this@ScaleService)
                                .notify(NOTIFICATION_ID, notifier.build(progress, paused = state != State.Running))
                        } catch (_: SecurityException) {
                            // POST_NOTIFICATIONS revoked mid-job — keep processing silently.
                        }
                    }
                }
        }

        jobRunner = scope.launch(Dispatchers.IO) {
            runJob(jobController, targetId, uris)
        }
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun startForegroundCompat(controller: JobController) {
        val notification = notifier.build(controller.progress.value, paused = false)
        when {
            Build.VERSION.SDK_INT >= 35 ->
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            Build.VERSION.SDK_INT >= 29 ->
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun runJob(controller: JobController, targetId: String, uris: List<Uri>) {
        val logger = app.debugLogger
        val startedAt = System.nanoTime()
        val target = app.settingsRepository.currentConfig().target(targetId)
        val treeUri = target?.outputTreeUri
        if (target == null || treeUri == null) {
            toastAsync("Share target is gone or has no output folder")
            finishJob()
            return
        }
        val sink = SafDocumentSink(this, Uri.parse(treeUri))
        if (!sink.isUsable()) {
            logger.write(LogEvent.Error(logger.now(), "job_start", "output folder not writable: $treeUri"))
            toastAsync("Output folder for \"${target.label}\" is not writable — choose it again")
            finishJob()
            return
        }

        logger.write(LogEvent.ShareReceived(logger.now(), target.id, target.label, uris.size))
        val processor = ImageProcessor(contentResolver)
        var processed = 0
        var failed = 0

        for ((index, uri) in uris.withIndex()) {
            if (!controller.awaitRunnable()) break
            val name = processor.sourceDisplayName(uri) ?: "image ${index + 1}"
            controller.startImage(index + 1, name)
            try {
                val result = processor.process(
                    source = uri,
                    target = target,
                    sink = sink,
                    onStage = controller::fileProgress,
                    onWarning = { logger.log(LogEvent.Error(logger.now(), "exif_copy", it)) },
                )
                createdOutputs += Uri.parse(result.outputUri)
                processed++
                logger.write(
                    LogEvent.ImageProcessed(
                        timestamp = logger.now(),
                        targetId = target.id,
                        sourceName = result.sourceName,
                        outputName = result.outputName,
                        originalWidth = result.originalWidth,
                        originalHeight = result.originalHeight,
                        outputWidth = result.outputWidth,
                        outputHeight = result.outputHeight,
                        quality = target.quality,
                        scaleFactorPercent = target.scaleFactorPercent,
                        outputBytes = result.outputBytes,
                        durationMs = result.durationMs,
                    )
                )
            } catch (e: Exception) {
                failed++
                logger.write(LogEvent.Error(logger.now(), "image_processing", "$name: $e"))
            }
        }

        val durationMs = (System.nanoTime() - startedAt) / 1_000_000
        if (controller.state.value == State.Cancelled) {
            var deleted = false
            if (deleteOutputsOnCancel) {
                createdOutputs.forEach { sink.delete(it) }
                deleted = createdOutputs.isNotEmpty()
            }
            logger.write(
                LogEvent.JobCancelled(logger.now(), target.id, processed, uris.size, deleted)
            )
            toastAsync(
                if (deleted) "Conversion cancelled, ${createdOutputs.size} converted image(s) deleted"
                else "Conversion cancelled"
            )
        } else {
            controller.markDone()
            logger.write(
                LogEvent.JobCompleted(logger.now(), target.id, processed, failed, uris.size, durationMs)
            )
            val summary = buildString {
                append("$processed of ${uris.size} image(s) saved to ${sink.folderName()}")
                if (failed > 0) append(" — $failed failed")
            }
            toastAsync(summary)
        }
        finishJob()
    }

    private fun finishJob() {
        controller = null
        jobRunner = null
        createdOutputs.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun logJobEvent(build: (ts: String, targetId: String, p: JobProgress) -> LogEvent) {
        val progress = controller?.progress?.value ?: return
        val logger = app.debugLogger
        logger.log(build(logger.now(), currentTargetId, progress))
    }

    private fun toastAsync(message: String) {
        scope.launch { withContext(Dispatchers.Main) { Toast.makeText(this@ScaleService, message, Toast.LENGTH_LONG).show() } }
    }

    companion object {
        const val ACTION_START = "ca.urbanlight.imagescale.action.START"
        const val ACTION_PAUSE = "ca.urbanlight.imagescale.action.PAUSE"
        const val ACTION_RESUME = "ca.urbanlight.imagescale.action.RESUME"
        const val ACTION_STOP = "ca.urbanlight.imagescale.action.STOP"
        const val ACTION_CONTINUE = "ca.urbanlight.imagescale.action.CONTINUE"
        const val ACTION_CONFIRM_CANCEL = "ca.urbanlight.imagescale.action.CONFIRM_CANCEL"
        const val EXTRA_TARGET_ID = "target_id"
        const val EXTRA_URIS = "uris"
        const val EXTRA_DELETE_OUTPUTS = "delete_outputs"
        const val NOTIFICATION_ID = 42
    }
}
