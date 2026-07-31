package ca.urbanlight.imagescale.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val configKey = stringPreferencesKey("app_config_json")
    private val logUriKey = stringPreferencesKey("active_log_uri")

    val config: Flow<AppConfig> = context.dataStore.data
        .map { prefs -> decode(prefs[configKey]) }
        .distinctUntilChanged()

    val activeLogUri: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[logUriKey] }
        .distinctUntilChanged()

    suspend fun currentConfig(): AppConfig = config.first()

    suspend fun currentLogUri(): String? = activeLogUri.first()

    private fun decode(text: String?): AppConfig {
        if (text == null) return AppConfig()
        return when (val result = SettingsCodec.decode(text)) {
            is SettingsCodec.DecodeResult.Success -> result.config
            is SettingsCodec.DecodeResult.Failure -> AppConfig()
        }
    }

    suspend fun update(transform: (AppConfig) -> AppConfig): AppConfig {
        var updated = AppConfig()
        context.dataStore.edit { prefs ->
            updated = transform(decode(prefs[configKey]))
            prefs[configKey] = SettingsCodec.encode(updated)
        }
        return updated
    }

    suspend fun addTarget(): ShareTargetConfig {
        var created = ShareTargetConfig(id = UUID.randomUUID().toString())
        update { config ->
            created = created.copy(label = "Share target ${config.targets.size + 1}")
            config.copy(targets = config.targets + created)
        }
        return created
    }

    suspend fun updateTarget(target: ShareTargetConfig): AppConfig = update { config ->
        config.copy(targets = config.targets.map { if (it.id == target.id) target.clamped() else it })
    }

    suspend fun deleteTarget(id: String): AppConfig {
        val previous = currentConfig().target(id)
        val updated = update { config -> config.copy(targets = config.targets.filterNot { it.id == id }) }
        previous?.outputTreeUri?.let { releaseTreeIfUnused(it, updated) }
        return updated
    }

    suspend fun setHidden(id: String, hidden: Boolean): AppConfig = update { config ->
        config.copy(targets = config.targets.map { if (it.id == id) it.copy(hidden = hidden) else it })
    }

    suspend fun setOutputTree(id: String, uri: Uri): AppConfig {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        val previous = currentConfig().target(id)?.outputTreeUri
        val updated = update { config ->
            config.copy(targets = config.targets.map {
                if (it.id == id) it.copy(outputTreeUri = uri.toString()) else it
            })
        }
        if (previous != null && previous != uri.toString()) releaseTreeIfUnused(previous, updated)
        return updated
    }

    /** Import replaces everything; tree URIs whose permission is gone are nulled out. */
    suspend fun replaceConfig(imported: AppConfig): AppConfig {
        val old = currentConfig()
        val validated = imported.copy(targets = imported.targets.map { target ->
            if (target.outputTreeUri != null && !hasWritePermission(target.outputTreeUri)) {
                target.copy(outputTreeUri = null)
            } else target
        })
        val updated = update { validated }
        old.targets.mapNotNull { it.outputTreeUri }.distinct().forEach { releaseTreeIfUnused(it, updated) }
        return updated
    }

    fun hasWritePermission(uriString: String): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == uriString && it.isWritePermission
        }

    private fun releaseTreeIfUnused(uriString: String, config: AppConfig) {
        if (config.targets.any { it.outputTreeUri == uriString }) return
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uriString), flags)
        } catch (_: SecurityException) {
            // Permission already gone — nothing to release.
        }
    }

    suspend fun setActiveLogUri(uri: String?) {
        val previous = currentLogUri()
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(logUriKey) else prefs[logUriKey] = uri
        }
        if (uri == null && previous != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(previous),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
            }
        }
    }
}
