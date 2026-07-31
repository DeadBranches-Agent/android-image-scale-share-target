package ca.urbanlight.imagescale.image

data class ProcessResult(
    val sourceName: String,
    val outputName: String,
    val originalWidth: Int,
    val originalHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val outputBytes: Long,
    val durationMs: Long,
)
