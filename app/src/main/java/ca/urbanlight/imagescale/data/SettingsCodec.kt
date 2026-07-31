package ca.urbanlight.imagescale.data

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Pure JSON codec for [AppConfig] export/import, including v1 (single-target) migration. */
object SettingsCodec {

    sealed interface DecodeResult {
        data class Success(val config: AppConfig, val migratedFromV1: Boolean = false) : DecodeResult
        data class Failure(val message: String) : DecodeResult
    }

    /** Schema of the original single-target settings export. */
    @Serializable
    private data class V1Settings(
        val version: Int = 1,
        val quality: Int = 85,
        val scaleFactorPercent: Int = 50,
        val discardMetadata: Boolean = false,
        val outputTreeUri: String? = null,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(config: AppConfig): String =
        json.encodeToString(AppConfig.serializer(), config.copy(version = AppConfig.CURRENT_VERSION))

    fun decode(text: String, newId: () -> String = { UUID.randomUUID().toString() }): DecodeResult {
        val version = try {
            json.parseToJsonElement(text).jsonObject["version"]?.jsonPrimitive?.int ?: 1
        } catch (e: Exception) {
            return DecodeResult.Failure("Not a valid settings file: ${e.message}")
        }
        if (version > AppConfig.CURRENT_VERSION) {
            return DecodeResult.Failure(
                "Settings file version $version is newer than this app supports (${AppConfig.CURRENT_VERSION})"
            )
        }
        return try {
            if (version == 1) {
                val v1 = json.decodeFromString(V1Settings.serializer(), text)
                val target = ShareTargetConfig(
                    id = newId(),
                    quality = v1.quality,
                    scaleFactorPercent = v1.scaleFactorPercent,
                    discardMetadata = v1.discardMetadata,
                    outputTreeUri = v1.outputTreeUri,
                ).clamped()
                DecodeResult.Success(AppConfig(targets = listOf(target)), migratedFromV1 = true)
            } else {
                val config = json.decodeFromString(AppConfig.serializer(), text)
                val sanitized = config.targets
                    .distinctBy { it.id }
                    .map { (if (it.id.isBlank()) it.copy(id = newId()) else it).clamped() }
                DecodeResult.Success(AppConfig(targets = sanitized))
            }
        } catch (e: Exception) {
            DecodeResult.Failure("Could not read settings: ${e.message}")
        }
    }
}
