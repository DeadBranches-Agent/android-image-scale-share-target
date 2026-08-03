package ca.urbanlight.imagescale.icons

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses the real generated asset with the same strict parser the app uses.
 * Guards against regenerated icon metadata that doesn't match the schema
 * (Tabler's upstream tags contain raw numbers and nulls — the generator must
 * normalize them or the app crashes on first launch).
 */
class BundledCatalogTest {

    private fun bundledAsset(): File {
        val candidates = listOf(
            File("src/main/assets/tabler_tags.json"),
            File("app/src/main/assets/tabler_tags.json"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("tabler_tags.json not found from ${File(".").absolutePath}")
    }

    @Test
    fun `the shipped tabler_tags asset parses with the app's strict parser`() {
        val catalog = IconCatalog.fromJson(bundledAsset().readText())
        assertTrue("catalog unexpectedly small: ${catalog.entries.size}", catalog.entries.size > 1000)
        assertTrue(catalog.entries.any { it.name == IconIndex.FALLBACK })
        // Numeric variant tags must have been normalized to strings, searchably so.
        assertTrue(catalog.search("arrows-join-2").isNotEmpty())
    }
}
