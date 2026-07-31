package ca.urbanlight.imagescale

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import ca.urbanlight.imagescale.data.ShareTargetConfig
import ca.urbanlight.imagescale.service.ScaleService
import ca.urbanlight.imagescale.ui.TargetPickerDialog
import ca.urbanlight.imagescale.ui.theme.ImageScaleTheme
import kotlinx.coroutines.launch

/**
 * Share-sheet entry point. Resolves which target applies (shortcut id, single
 * visible target, or a mini picker), validates it, hands the job to
 * [ScaleService], and finishes.
 */
class ShareActivity : ComponentActivity() {

    private val app get() = application as App
    private var onPermissionSettled: (() -> Unit)? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Granted or not, the job proceeds; without the permission there is
            // simply no progress notification.
            onPermissionSettled?.invoke()
            onPermissionSettled = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = extractImageUris()
        if (uris.isEmpty()) {
            toastAndFinish("Nothing shareable was received")
            return
        }

        lifecycleScope.launch {
            val config = app.settingsRepository.currentConfig()
            val shortcutId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)
            val fromShortcut = config.target(shortcutId)?.takeIf { !it.hidden }
            val visible = config.visibleTargets

            when {
                fromShortcut != null -> proceed(fromShortcut, uris)
                visible.size == 1 -> proceed(visible.single(), uris)
                visible.isEmpty() -> {
                    toast("Add a share target first")
                    startActivity(Intent(this@ShareActivity, MainActivity::class.java))
                    finish()
                }
                else -> setContent {
                    ImageScaleTheme {
                        TargetPickerDialog(
                            targets = visible,
                            onSelect = { proceed(it, uris) },
                            onDismiss = { finish() },
                        )
                    }
                }
            }
        }
    }

    private fun extractImageUris(): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.filterNotNull().orEmpty()
        else -> emptyList()
    }

    private fun proceed(target: ShareTargetConfig, uris: List<Uri>) {
        val outputTree = target.outputTreeUri
        if (outputTree == null || !app.settingsRepository.hasWritePermission(outputTree)) {
            toast("Choose an output folder for \"${target.label}\" first")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val start = { startJob(target, uris) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onPermissionSettled = start
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            start()
        }
    }

    private fun startJob(target: ShareTargetConfig, uris: List<Uri>) {
        val serviceIntent = Intent(this, ScaleService::class.java)
            .setAction(ScaleService.ACTION_START)
            .putExtra(ScaleService.EXTRA_TARGET_ID, target.id)
            .putParcelableArrayListExtra(ScaleService.EXTRA_URIS, ArrayList(uris))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // Extend the share grant on the source images to the service via ClipData.
        val clip = ClipData.newUri(contentResolver, "images", uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        serviceIntent.clipData = clip

        startForegroundService(serviceIntent)
        finish()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun toastAndFinish(message: String) {
        toast(message)
        finish()
    }
}
