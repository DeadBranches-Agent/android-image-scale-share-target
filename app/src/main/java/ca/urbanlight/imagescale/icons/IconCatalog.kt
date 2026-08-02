package ca.urbanlight.imagescale.icons

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class IconEntry(
    val name: String,
    val res: String,
    val category: String = "",
    val tags: List<String> = emptyList(),
)

/** Searchable index over the bundled Tabler icons (loaded from assets/tabler_tags.json). */
class IconCatalog(val entries: List<IconEntry>) {

    /**
     * Case-insensitive search over name, category, and tags. Every whitespace-separated
     * token must match somewhere. A blank query returns everything.
     */
    fun search(query: String): List<IconEntry> {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return entries
        return entries.filter { entry ->
            tokens.all { token ->
                entry.name.contains(token) ||
                    entry.category.lowercase().contains(token) ||
                    entry.tags.any { it.lowercase().contains(token) }
            }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): IconCatalog =
            IconCatalog(json.decodeFromString<List<IconEntry>>(text))
    }
}
