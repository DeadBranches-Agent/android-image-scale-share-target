package ca.urbanlight.imagescale.data

import kotlinx.serialization.Serializable

@Serializable
data class ShareTargetConfig(
    val id: String,
    val label: String = "Scaled JPG",
    val iconName: String = "photo",
    val quality: Int = 85,
    val scaleFactorPercent: Int = 50,
    val discardMetadata: Boolean = false,
    val outputTreeUri: String? = null,
    val hidden: Boolean = false,
) {
    fun clamped(): ShareTargetConfig = copy(
        quality = quality.coerceIn(QUALITY_RANGE),
        scaleFactorPercent = scaleFactorPercent.coerceIn(SCALE_RANGE),
        label = label.take(MAX_LABEL_LENGTH).ifBlank { DEFAULT_LABEL },
    )

    companion object {
        val QUALITY_RANGE = 1..100
        val SCALE_RANGE = 10..100
        const val MAX_LABEL_LENGTH = 40
        const val DEFAULT_LABEL = "Scaled JPG"
    }
}

@Serializable
data class AppConfig(
    val version: Int = CURRENT_VERSION,
    val targets: List<ShareTargetConfig> = emptyList(),
) {
    val visibleTargets: List<ShareTargetConfig> get() = targets.filter { !it.hidden }

    fun target(id: String?): ShareTargetConfig? = targets.find { it.id == id }

    companion object {
        const val CURRENT_VERSION = 2
    }
}
