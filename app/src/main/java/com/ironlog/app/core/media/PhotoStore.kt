package com.ironlog.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where progress photos live and how they get there.
 *
 * Photos are stored in the app's internal files directory, not in the shared
 * gallery. That is a deliberate privacy decision: progress photos are usually
 * taken in underwear, and writing them to shared media storage would expose them
 * to every other app holding a media-read permission, and to whoever picks up the
 * phone and opens the gallery. Internal storage is sandboxed to this app's UID
 * and is excluded from cloud backup in the manifest.
 *
 * The camera app cannot write into that private directory directly, so a
 * [FileProvider] grants it a scoped, one-shot write URI for exactly one file.
 */
@Singleton
class PhotoStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val photosDir: File
        get() = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }

    /** Creates the destination file and the URI the camera app will write to. */
    fun newCaptureTarget(): CaptureTarget {
        val file = File(photosDir, "photo_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return CaptureTarget(file = file, uri = uri)
    }

    /**
     * Post-processing after a capture: corrects orientation from EXIF, downscales
     * and re-encodes. A modern phone camera produces 4-12 MB per shot; at one
     * photo a week for a year that is a gigabyte of internal storage for images
     * only ever shown at screen size.
     */
    suspend fun process(file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(file.exists() && file.length() > 0) { "Capture produced no file" }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
                ?: error("Could not decode the captured image")

            val rotated = applyExifRotation(file, decoded)
            FileOutputStream(file).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (rotated != decoded) decoded.recycle()
            rotated.recycle()

            file.absolutePath
        }
    }

    /** Copies a gallery pick into private storage so the app owns a stable path. */
    suspend fun importFromUri(source: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val destination = File(photosDir, "import_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            } ?: error("Could not read the selected image")
            process(destination).getOrThrow()
        }
    }

    fun delete(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

    fun exists(path: String): Boolean = File(path).exists()

    /** A temporary, shareable copy — private files cannot be shared by raw path. */
    fun shareableUri(path: String): Uri? = runCatching {
        val source = File(path)
        val cacheDir = File(context.cacheDir, "shared").apply { if (!exists()) mkdirs() }
        val copy = File(cacheDir, source.name)
        source.copyTo(copy, overwrite = true)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy)
    }.getOrNull()

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= MAX_DIMENSION && h / 2 >= MAX_DIMENSION) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val MAX_DIMENSION = 1440
        const val JPEG_QUALITY = 85
    }
}

data class CaptureTarget(val file: File, val uri: Uri)
