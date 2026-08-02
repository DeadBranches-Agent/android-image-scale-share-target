package ca.urbanlight.imagescale.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconCatalogTest {

    private val catalog = IconCatalog(
        listOf(
            IconEntry("photo", "tab_photo", "Media", listOf("picture", "camera")),
            IconEntry("folder", "tab_folder", "Document", listOf("directory", "files")),
            IconEntry("arrow-up", "tab_arrow_up", "Arrows", listOf("direction", "north")),
        )
    )

    @Test
    fun `blank query returns everything`() {
        assertEquals(3, catalog.search("").size)
        assertEquals(3, catalog.search("   ").size)
    }

    @Test
    fun `matches by name substring`() {
        assertEquals(listOf("photo"), catalog.search("pho").map { it.name })
    }

    @Test
    fun `matches by tag and category case-insensitively`() {
        assertEquals(listOf("photo"), catalog.search("CAMERA").map { it.name })
        assertEquals(listOf("folder"), catalog.search("document").map { it.name })
    }

    @Test
    fun `all tokens must match`() {
        assertEquals(listOf("arrow-up"), catalog.search("arrow north").map { it.name })
        assertTrue(catalog.search("arrow camera").isEmpty())
    }

    @Test
    fun `parses the generated json format`() {
        val json = """[{"name":"photo","res":"tab_photo","category":"Media","tags":["picture"]}]"""
        val parsed = IconCatalog.fromJson(json)
        assertEquals("tab_photo", parsed.entries.single().res)
    }
}
