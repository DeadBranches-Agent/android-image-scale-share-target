package ca.urbanlight.imagescale.io

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** Write access to the target's output tree, with a cached name index for fast dedup. */
class SafDocumentSink(private val context: Context, treeUri: Uri) {

    private val tree: DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)
    private val names: MutableSet<String> by lazy {
        tree?.listFiles()?.mapNotNull { it.name }?.toMutableSet() ?: mutableSetOf()
    }

    fun isUsable(): Boolean = tree != null && tree.canWrite()

    fun exists(name: String): Boolean = name in names

    /** Creates the output document; SAF may still rename on races, so read back the real name. */
    fun create(name: String): DocumentFile {
        val doc = tree?.createFile("image/jpeg", name)
            ?: throw IllegalStateException("could not create $name in output folder")
        names += doc.name ?: name
        return doc
    }

    fun delete(uri: Uri): Boolean =
        DocumentFile.fromSingleUri(context, uri)?.delete() ?: false

    fun folderName(): String = tree?.name ?: "output folder"
}
