package ca.urbanlight.imagescale.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AppConfigDefaultsTest {

    @Test
    fun `new target defaults match the spec`() {
        val target = ShareTargetConfig(id = "x")
        assertFalse("discard metadata must default to unchecked", target.discardMetadata)
        assertFalse(target.hidden)
        assertEquals(85, target.quality)
        assertEquals(50, target.scaleFactorPercent)
        assertNull(target.outputTreeUri)
    }

    @Test
    fun `clamped fixes out-of-range values and blank labels`() {
        val target = ShareTargetConfig(id = "x", label = "   ", quality = -5, scaleFactorPercent = 400)
        val clamped = target.clamped()
        assertEquals(1, clamped.quality)
        assertEquals(100, clamped.scaleFactorPercent)
        assertEquals(ShareTargetConfig.DEFAULT_LABEL, clamped.label)
    }

    @Test
    fun `visibleTargets filters hidden ones`() {
        val config = AppConfig(
            targets = listOf(
                ShareTargetConfig(id = "a"),
                ShareTargetConfig(id = "b", hidden = true),
                ShareTargetConfig(id = "c"),
            )
        )
        assertEquals(listOf("a", "c"), config.visibleTargets.map { it.id })
    }
}
