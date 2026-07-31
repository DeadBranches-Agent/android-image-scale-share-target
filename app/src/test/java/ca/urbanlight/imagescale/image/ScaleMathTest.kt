package ca.urbanlight.imagescale.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleMathTest {

    @Test
    fun `target dimensions round to nearest`() {
        assertEquals(2000 to 1500, ScaleMath.targetDimensions(4000, 3000, 50))
        assertEquals(1320 to 990, ScaleMath.targetDimensions(3999, 3000, 33))
        assertEquals(4000 to 3000, ScaleMath.targetDimensions(4000, 3000, 100))
    }

    @Test
    fun `target dimensions never drop below one pixel`() {
        assertEquals(1 to 1, ScaleMath.targetDimensions(3, 3, 10))
        assertEquals(1 to 1, ScaleMath.targetDimensions(1, 1, 10))
    }

    @Test
    fun `inSampleSize is the largest power of two at or above target`() {
        assertEquals(2, ScaleMath.inSampleSize(4000, 3000, 2000, 1500)) // 50% — decodes exactly at target
        assertEquals(4, ScaleMath.inSampleSize(4000, 3000, 1000, 750))  // 25%
        assertEquals(8, ScaleMath.inSampleSize(4000, 3000, 400, 300))   // 10%
        assertEquals(1, ScaleMath.inSampleSize(4000, 3000, 4000, 3000)) // 100%
        assertEquals(8, ScaleMath.inSampleSize(10, 10, 1, 1))
    }

    @Test
    fun `sampled size always stays at or above target`() {
        for (percent in intArrayOf(10, 25, 33, 50, 75, 100)) {
            val (tw, th) = ScaleMath.targetDimensions(4032, 3024, percent)
            val sample = ScaleMath.inSampleSize(4032, 3024, tw, th)
            val decodedW = 4032 / sample
            val decodedH = 3024 / sample
            assert(decodedW >= tw && decodedH >= th) {
                "at $percent%: decoded ${decodedW}x$decodedH below target ${tw}x$th"
            }
        }
    }
}
