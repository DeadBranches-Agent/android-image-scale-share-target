package ca.urbanlight.imagescale

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ca.urbanlight.imagescale.data.AppConfig
import ca.urbanlight.imagescale.data.SettingsCodec
import ca.urbanlight.imagescale.data.ShareTargetConfig
import ca.urbanlight.imagescale.icons.IconCatalog
import ca.urbanlight.imagescale.log.LogEvent
import ca.urbanlight.imagescale.ui.IconPickerDialog
import ca.urbanlight.imagescale.ui.TargetListScreen
import ca.urbanlight.imagescale.ui.theme.ImageScaleTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val app get() = application as App

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Keep the share sheet in sync with every config change, whatever triggered it.
        lifecycleScope.launch {
            app.settingsRepository.config.collect { config ->
                withContext(Dispatchers.Default) { app.shortcutPublisher.publish(config) }
            }
        }

        setContent {
            ImageScaleTheme {
                MainScreen()
            }
        }
    }

    @Composable
    private fun MainScreen() {
        val repository = app.settingsRepository
        val scope = rememberCoroutineScope()
        val config by repository.config.collectAsStateWithLifecycle(AppConfig())
        val logUri by repository.activeLogUri.collectAsStateWithLifecycle(null)

        val catalog by produceState<IconCatalog?>(null) {
            value = withContext(Dispatchers.IO) {
                IconCatalog.fromJson(assets.open("tabler_tags.json").bufferedReader().readText())
            }
        }

        var iconPickerTarget by remember { mutableStateOf<ShareTargetConfig?>(null) }
        var folderPickerTargetId by remember { mutableStateOf<String?>(null) }

        val folderLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            val targetId = folderPickerTargetId
            folderPickerTargetId = null
            if (uri != null && targetId != null) {
                scope.launch {
                    repository.setOutputTree(targetId, uri)
                    logSettingsChanged()
                }
            }
        }

        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri -> if (uri != null) scope.launch { exportSettings(uri) } }

        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> if (uri != null) scope.launch { importSettings(uri) } }

        val logFileLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/x-ndjson")
        ) { uri -> if (uri != null) scope.launch { startLogging(uri) } }

        TargetListScreen(
            config = config,
            loggingActive = logUri != null,
            outputFolderName = { target -> target.outputTreeUri?.let(::folderDisplayName) },
            onAddTarget = {
                scope.launch {
                    repository.addTarget()
                    logSettingsChanged()
                }
            },
            onUpdate = { target ->
                scope.launch {
                    repository.updateTarget(target)
                    logSettingsChanged()
                }
            },
            onPickIcon = { iconPickerTarget = it },
            onPickOutputFolder = { target ->
                folderPickerTargetId = target.id
                folderLauncher.launch(null)
            },
            onToggleHidden = { target ->
                scope.launch {
                    repository.setHidden(target.id, !target.hidden)
                    logSettingsChanged()
                }
            },
            onDelete = { target ->
                scope.launch {
                    repository.deleteTarget(target.id)
                    logSettingsChanged()
                }
            },
            onExport = { exportLauncher.launch("image-scale-settings.json") },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            onToggleLogging = {
                if (logUri != null) {
                    scope.launch {
                        repository.setActiveLogUri(null)
                        toast("Debug logging stopped")
                    }
                } else {
                    logFileLauncher.launch("image-scale-log.jsonl")
                }
            },
        )

        val pickerTarget = iconPickerTarget
        val loadedCatalog = catalog
        if (pickerTarget != null && loadedCatalog != null) {
            IconPickerDialog(
                catalog = loadedCatalog,
                onSelect = { iconName ->
                    iconPickerTarget = null
                    scope.launch {
                        repository.updateTarget(pickerTarget.copy(iconName = iconName))
                        logSettingsChanged()
                    }
                },
                onDismiss = { iconPickerTarget = null },
            )
        }
    }

    private suspend fun exportSettings(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val json = SettingsCodec.encode(app.settingsRepository.currentConfig())
            val stream = contentResolver.openOutputStream(uri, "wt")
                ?: contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("no stream for $uri")
            stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            toast("Settings exported")
        } catch (e: Exception) {
            app.debugLogger.log(LogEvent.Error(app.debugLogger.now(), "export_settings", e.toString()))
            toast("Export failed: ${e.message}")
        }
    }

    private suspend fun importSettings(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("no stream for $uri")
            when (val result = SettingsCodec.decode(text)) {
                is SettingsCodec.DecodeResult.Failure -> toast(result.message)
                is SettingsCodec.DecodeResult.Success -> {
                    val applied = app.settingsRepository.replaceConfig(result.config)
                    logSettingsChanged()
                    val cleared = result.config.targets.count { it.outputTreeUri != null } -
                        applied.targets.count { it.outputTreeUri != null }
                    val note = buildString {
                        append("Imported ${applied.targets.size} share target(s)")
                        if (result.migratedFromV1) append(" (migrated from v1)")
                        if (cleared > 0) append("; $cleared output folder(s) need re-selection")
                    }
                    toast(note)
                }
            }
        } catch (e: Exception) {
            app.debugLogger.log(LogEvent.Error(app.debugLogger.now(), "import_settings", e.toString()))
            toast("Import failed: ${e.message}")
        }
    }

    private suspend fun startLogging(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Provider didn't offer a persistable grant; logging still works this session.
        }
        app.settingsRepository.setActiveLogUri(uri.toString())
        app.debugLogger.log(
            LogEvent.SettingsChanged(app.debugLogger.now(), app.settingsRepository.currentConfig())
        )
        toast("Debug logging started")
    }

    private fun logSettingsChanged() {
        val logger = app.debugLogger
        app.appScope.launch {
            logger.write(
                LogEvent.SettingsChanged(logger.now(), app.settingsRepository.currentConfig())
            )
        }
    }

    private fun folderDisplayName(uriString: String): String {
        val decoded = Uri.parse(uriString).lastPathSegment ?: return uriString
        return decoded.substringAfterLast(':').ifBlank { decoded }
    }

    private suspend fun toast(message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
    }
}
