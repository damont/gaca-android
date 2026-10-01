package dev.monts.gaca.data.repo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Loads chat photos from the API. They're private (same bearer token as every other route), so
 * a plain image URL won't do — this goes through the app's own authenticated [OkHttpClient].
 * Decoded bitmaps are cached in memory by URL: photos are immutable once sent, so a hit is
 * always right.
 */
class ImageRepository(private val client: OkHttpClient, private val baseUrl: String) {

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** [relativeUrl] is what the API puts on an attachment (relative to the API base). */
    suspend fun load(relativeUrl: String, maxEdge: Int = 1024): Bitmap? {
        cache.get(relativeUrl)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val bytes = client.newCall(Request.Builder().url(baseUrl + relativeUrl).build())
                    .execute()
                    .use { response ->
                        if (!response.isSuccessful) throw IOException("photo ${response.code}")
                        response.body?.bytes()
                    } ?: return@withContext null
                decodeDownsampled(bytes, maxEdge)?.also { cache.put(relativeUrl, it) }
            } catch (_: IOException) {
                null
            }
        }
    }

    private fun decodeDownsampled(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
