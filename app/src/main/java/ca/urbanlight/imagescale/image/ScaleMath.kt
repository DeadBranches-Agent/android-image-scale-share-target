package ca.urbanlight.imagescale.image

import kotlin.math.max
import kotlin.math.roundToInt

object ScaleMath {

    /** Output dimensions for a scale percentage, never below 1px. */
    fun targetDimensions(origWidth: Int, origHeight: Int, scalePercent: Int): Pair<Int, Int> {
        val w = max(1, (origWidth * scalePercent / 100.0).roundToInt())
        val h = max(1, (origHeight * scalePercent / 100.0).roundToInt())
        return w to h
    }

    /**
     * Largest power-of-two sample size that still decodes at or above the target size,
     * so the exact scale never upsamples.
     */
    fun inSampleSize(origWidth: Int, origHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (origWidth / (sample * 2) >= targetWidth && origHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }
}
