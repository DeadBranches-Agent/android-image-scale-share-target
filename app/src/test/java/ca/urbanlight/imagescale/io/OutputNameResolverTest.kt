package ca.urbanlight.imagescale.io

import org.junit.Assert.assertEquals
import org.junit.Test

class OutputNameResolverTest {

    private val nothingExists = { _: String -> false }

    @Test
    fun `extension is replaced with jpg`() {
        assertEquals("IMG_1234_scaled.jpg", OutputNameResolver.resolve("IMG_1234.png", nothingExists))
        assertEquals("photo_scaled.jpg", OutputNameResolver.resolve("photo.jpeg", nothingExists))
    }

    @Test
    fun `name without extension gets suffix`() {
        assertEquals("holiday_scaled.jpg", OutputNameResolver.resolve("holiday", nothingExists))
    }

    @Test
    fun `null or blank names fall back to image`() {
        assertEquals("image_scaled.jpg", OutputNameResolver.resolve(null, nothingExists))
        assertEquals("image_scaled.jpg", OutputNameResolver.resolve("  ", nothingExists))
        assertEquals("image_scaled.jpg", OutputNameResolver.resolve(".hidden", nothingExists))
    }

    @Test
    fun `illegal characters are replaced`() {
        assertEquals("a_b_c_scaled.jpg", OutputNameResolver.resolve("a:b*c.png", nothingExists))
        assertEquals("my pic_scaled.jpg", OutputNameResolver.resolve("my pic.png", nothingExists))
    }

    @Test
    fun `path segments are stripped`() {
        assertEquals("pic_scaled.jpg", OutputNameResolver.resolve("some/dir/pic.png", nothingExists))
    }

    @Test
    fun `collisions get numeric suffixes`() {
        val taken = mutableSetOf("pic_scaled.jpg", "pic_scaled_1.jpg")
        assertEquals("pic_scaled_2.jpg", OutputNameResolver.resolve("pic.png") { it in taken })
        taken += "pic_scaled_2.jpg"
        assertEquals("pic_scaled_3.jpg", OutputNameResolver.resolve("pic.png") { it in taken })
    }
}
