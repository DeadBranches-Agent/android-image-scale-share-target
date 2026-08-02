package ca.urbanlight.imagescale.io

object OutputNameResolver {

    private const val SUFFIX = "_scaled"
    private const val EXTENSION = ".jpg"
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|]|\\p{Cntrl}")

    /**
     * Builds `<base>_scaled.jpg` from a source display name, appending `_1`, `_2`, …
     * until [exists] reports the name free.
     */
    fun resolve(displayName: String?, exists: (String) -> Boolean): String {
        val withoutExtension = displayName.orEmpty()
            .substringAfterLast('/')
            .let { if (it.contains('.')) it.substringBeforeLast('.') else it }
        val base = ILLEGAL.replace(withoutExtension, "_").trim().ifBlank { "image" }
        val first = "$base$SUFFIX$EXTENSION"
        if (!exists(first)) return first
        var n = 1
        while (true) {
            val candidate = "$base${SUFFIX}_$n$EXTENSION"
            if (!exists(candidate)) return candidate
            n++
        }
    }
}
