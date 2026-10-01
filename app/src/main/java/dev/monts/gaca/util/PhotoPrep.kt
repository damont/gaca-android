package dev.monts.gaca.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/** A picked photo, shrunk and re-encoded as JPEG, ready to upload. [thumbnail] is only for the
 * preview chip and the optimistic bubble. */
class PreparedPhoto(val id: String, val jpeg: ByteArray, val thumbnail: Bitmap)

/**
 * Phone photos are 3–10 MB; the API caps uploads at 2 MB (the nginx layers in front of it do too),
 * and the model doesn't need more than ~1600px anyway. So every picked photo is decoded
 * downsampled, rotated upright per its EXIF, and re-encoded as JPEG under [MAX_BYTES] — which also
 * normalizes whatever the picker hands back (HEIC, PNG…) into something the API accepts.
 */
object PhotoPrep {
    const val MAX_PHOTOS = 4
    private const val MAX_EDGE = 1600
    private const val MAX_BYTES = 1_500_000
    private const val THUMB_EDGE = 256

    /** `null` if the file can't be read or decoded. */
    suspend fun prepare(resolver: ContentResolver, uri: Uri): PreparedPhoto? = withContext(Dispatchers.IO) {
        try {
            var bitmap = decode(resolver, uri, MAX_EDGE) ?: return@withContext null
            bitmap = upright(bitmap, orientation(resolver, uri))

            var edge = MAX_EDGE
            var quality = 85
            var bytes = encode(bitmap, quality)
            while (bytes.size > MAX_BYTES && (quality > 55 || edge > 800)) {
                if (quality > 55) {
                    quality -= 10
                } else {
                    edge = (edge * 0.8f).toInt()
                    bitmap = scaleTo(bitmap, edge)
                }
                bytes = encode(bitmap, quality)
            }
            if (bytes.size > MAX_BYTES) return@withContext null
            PreparedPhoto(UUID.randomUUID().toString(), bytes, scaleTo(bitmap, THUMB_EDGE))
        } catch (_: Exception) {
            null
        }
    }

    private fun decode(resolver: ContentResolver, uri: Uri, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    private fun orientation(resolver: ContentResolver, uri: Uri): Int =
        try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun upright(bitmap: Bitmap, exif: Int): Bitmap {
        val matrix = Matrix()
        when (exif) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleTo(bitmap: Bitmap, edge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= edge) return bitmap
        val scale = edge.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}
