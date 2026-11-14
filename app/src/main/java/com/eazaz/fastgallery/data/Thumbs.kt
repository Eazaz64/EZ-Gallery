package com.eazaz.fastgallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A small grid thumbnail of one image file. mtime is part of the key so edited files get a new thumb. */
data class Thumb(val path: String, val mtime: Long)

/**
 * Thumbnails are made once (scaled decode, no full-size bitmap), saved as small WebP files
 * in the app cache, and read back instantly afterwards.
 */
class ThumbStore(context: Context) {
    val dir = File(context.cacheDir, "thumbs")

    private fun fileFor(t: Thumb): File {
        val md = MessageDigest.getInstance("SHA-1")
        val hex = md.digest("${t.path}|${t.mtime}".toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(dir, hex.substring(0, 2)), hex.substring(2) + ".webp")
    }

    fun get(t: Thumb): Bitmap {
        val f = fileFor(t)
        if (f.exists()) {
            BitmapFactory.decodeFile(f.path)?.let { return it }
        }
        val src = ImageDecoder.createSource(File(t.path))
        val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val short = min(w, h)
            if (short > SIZE) {
                val s = SIZE.toFloat() / short
                decoder.setTargetSize(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 72, it) }
            tmp.renameTo(f)
        } catch (_: Exception) {
        }
        return bmp
    }

    fun sizeBytes(): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clear() {
        dir.deleteRecursively()
    }

    /** Keep the cache under the limit: delete the oldest thumbnails first. */
    fun trim(limitBytes: Long) {
        val files = dir.walkTopDown().filter { it.isFile }.toMutableList()
        var total = files.sumOf { it.length() }
        if (total <= limitBytes) return
        val target = (limitBytes * 0.85).toLong()
        files.sortBy { it.lastModified() }
        for (f in files) {
            if (total <= target) break
            total -= f.length()
            f.delete()
        }
    }

    companion object {
        const val SIZE = 320
    }
}

class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options): String = "t:${data.path}:${data.mtime}"
}

class ThumbFetcher(private val data: Thumb, private val store: ThumbStore) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bmp = store.get(data)
        return ImageFetchResult(image = bmp.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val store: ThumbStore) : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(data, store)
    }
}
