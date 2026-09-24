package ca.urbanlight.imagescale.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import ca.urbanlight.imagescale.data.ShareTargetConfig
import ca.urbanlight.imagescale.io.OutputNameResolver
import ca.urbanlight.imagescale.io.SafDocumentSink
import java.io.InputStream

class ImageProcessor(private val resolver: ContentResolver) {

    class ProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun sourceDisplayName(uri: Uri): String? =
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
            }
        } catch (_: Exception) {
            null
        } ?: uri.lastPathSegment?.substringAfterLast('/')

    /**
     * Scales one image into the sink per the target's settings.
     * SAF streams aren't reliably resettable, so each pass opens a fresh stream.
     *
     * @param onStage coarse per-file progress, 0..100
     * @param onWarning non-fatal problems (e.g. EXIF could not be written)
     */
    fun process(
        source: Uri,
        target: ShareTargetConfig,
        sink: SafDocumentSink,
        onStage: (Int) -> Unit = {},
        onWarning: (String) -> Unit = {},
    ): ProcessResult {
        val started = System.nanoTime()
        val openSource: () -> InputStream? = { resolver.openInputStream(source) }
        val sourceName = sourceDisplayName(source) ?: "image"

        onStage(5)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns null, so only the stream is null-checked.
        val boundsStream = openSource() ?: throw ProcessingException("cannot open $sourceName")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val origW = bounds.outWidth
        val origH = bounds.outHeight
        if (origW <= 0 || origH <= 0) throw ProcessingException("$sourceName is not a decodable image")

        val (targetW, targetH) = ScaleMath.targetDimensions(origW, origH, target.scaleFactorPercent)
        val options = BitmapFactory.Options().apply {
            inSampleSize = ScaleMath.inSampleSize(origW, origH, targetW, targetH)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        onStage(15)
        val decoded = openSource()?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw ProcessingException("cannot decode $sourceName")

        onStage(45)
        var bitmap = if (decoded.width != targetW || decoded.height != targetH) {
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else decoded

        // Discarding metadata drops the orientation tag, so bake the rotation
        // into the pixels; otherwise the tag is copied and pixels stay as-is.
        if (target.discardMetadata) {
            val orientation = ExifCopier.readOrientation(openSource)
            orientationMatrix(orientation)?.let { matrix ->
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }
        }
        val outW = bitmap.width
        val outH = bitmap.height

        onStage(65)
        val outputName = OutputNameResolver.resolve(sourceName, sink::exists)
        val document = sink.create(outputName)
        try {
            val stream = resolver.openOutputStream(document.uri)
                ?: throw ProcessingException("cannot write $outputName")
            stream.use { bitmap.compress(Bitmap.CompressFormat.JPEG, target.quality, it) }
        } catch (e: Exception) {
            document.delete()
            throw if (e is ProcessingException) e else ProcessingException("writing $outputName failed", e)
        } finally {
            bitmap.recycle()
        }

        onStage(85)
        if (!target.discardMetadata) {
            val copied = ExifCopier.copy(openSource, resolver, document.uri, outW, outH)
            if (!copied) onWarning("EXIF metadata could not be written for $outputName")
        }

        onStage(100)
        return ProcessResult(
            sourceName = sourceName,
            outputName = document.name ?: outputName,
            outputUri = document.uri.toString(),
            originalWidth = origW,
            originalHeight = origH,
            outputWidth = outW,
            outputHeight = outH,
            outputBytes = document.length(),
            durationMs = (System.nanoTime() - started) / 1_000_000,
        )
    }

    private fun orientationMatrix(orientation: Int): Matrix? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return null
        }
        return matrix
    }
}
