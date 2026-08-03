package ca.urbanlight.imagescale.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Runs the real decode path against a real JPEG (Robolectric native graphics).
 * Regression guard for the launch-day field bug: the bounds pass uses
 * inJustDecodeBounds, where decodeStream returns null BY CONTRACT — the old
 * code treated that null as "cannot open" and failed every shared image.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ImageProcessorDecodeTest {

    private fun jpegBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF3366AA.toInt())
        val out = ByteArrayOutputStream()
        assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out))
        bitmap.recycle()
        return out.toByteArray()
    }

    @Test
    fun `decodes and scales a real jpeg instead of reporting cannot-open`() {
        val bytes = jpegBytes(100, 80)
        val decoded = ImageProcessor.decodeScaled(
            openSource = { ByteArrayInputStream(bytes) },
            scalePercent = 50,
            bakeOrientation = false,
            sourceName = "test.jpg",
        )
        assertEquals(100, decoded.originalWidth)
        assertEquals(80, decoded.originalHeight)
        assertEquals(50, decoded.bitmap.width)
        assertEquals(40, decoded.bitmap.height)
    }

    @Test
    fun `scale 100 keeps original dimensions`() {
        val bytes = jpegBytes(64, 48)
        val decoded = ImageProcessor.decodeScaled(
            openSource = { ByteArrayInputStream(bytes) },
            scalePercent = 100,
            bakeOrientation = true, // no EXIF in generated jpeg → no rotation applied
            sourceName = "test.jpg",
        )
        assertEquals(64, decoded.bitmap.width)
        assertEquals(48, decoded.bitmap.height)
    }

    @Test
    fun `null stream reports cannot open`() {
        try {
            ImageProcessor.decodeScaled({ null }, 50, false, "gone.jpg")
            throw AssertionError("expected ProcessingException")
        } catch (e: ImageProcessor.ProcessingException) {
            assertTrue(e.message!!.contains("cannot open"))
        }
    }

    @Test
    fun `undecodable bytes report not decodable, not cannot-open`() {
        val garbage = ByteArray(256) { it.toByte() }
        try {
            ImageProcessor.decodeScaled({ ByteArrayInputStream(garbage) }, 50, false, "junk.bin")
            throw AssertionError("expected ProcessingException")
        } catch (e: ImageProcessor.ProcessingException) {
            assertTrue("got: ${e.message}", e.message!!.contains("not a decodable"))
        }
    }

    @Test
    fun `sanity - BitmapFactory bounds pass returns null by contract`() {
        val bytes = jpegBytes(10, 10)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val result = ByteArrayInputStream(bytes).use { BitmapFactory.decodeStream(it, null, bounds) }
        assertEquals(null, result)
        assertEquals(10, bounds.outWidth)
    }
}
