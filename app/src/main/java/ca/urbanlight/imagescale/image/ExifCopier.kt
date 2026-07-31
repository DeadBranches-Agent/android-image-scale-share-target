package ca.urbanlight.imagescale.image

import android.content.ContentResolver
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream

/**
 * Copies a curated EXIF tag set from the source image onto the written JPEG.
 * The pixel data is not rotated when metadata is preserved, so TAG_ORIENTATION
 * is copied too — viewers keep rendering the image upright.
 */
object ExifCopier {

    private val TAGS = arrayOf(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_SENSITIVITY_TYPE,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_BRIGHTNESS_VALUE,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_MAX_APERTURE_VALUE,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_LIGHT_SOURCE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LENS_SPECIFICATION,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_CONTRAST,
        ExifInterface.TAG_SATURATION,
        ExifInterface.TAG_SHARPNESS,
    )

    /**
     * @return true when the tags were written; false when the output provider
     * cannot hand out the seekable "rw" descriptor ExifInterface needs.
     */
    fun copy(
        openSource: () -> InputStream?,
        resolver: ContentResolver,
        output: Uri,
        outputWidth: Int,
        outputHeight: Int,
    ): Boolean {
        val source = openSource()?.use { ExifInterface(it) } ?: return false
        return try {
            resolver.openFileDescriptor(output, "rw")?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                for (tag in TAGS) {
                    source.getAttribute(tag)?.let { exif.setAttribute(tag, it) }
                }
                exif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, outputWidth.toString())
                exif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, outputHeight.toString())
                exif.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, outputWidth.toString())
                exif.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, outputHeight.toString())
                exif.saveAttributes()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /** Source orientation for baking rotation into pixels when metadata is discarded. */
    fun readOrientation(openSource: () -> InputStream?): Int =
        openSource()?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
}
