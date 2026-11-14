package com.eazaz.fastgallery.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.ImageDecoder
import android.media.MediaScannerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Delete / move / rename / wallpaper. Works directly on files (All files access). */
class FileOps(
    private val ctx: Context,
    private val dao: GalleryDao,
    private val roots: () -> List<String>,
) {

    /** Recount a folder after files changed. Removes the folder row if it has no images left. */
    suspend fun refreshFolder(path: String) {
        val st = dao.stats(path)
        if (st.c == 0) {
            dao.removeFolder(path)
            return
        }
        val newest = dao.newestImage(path)
        val old = dao.folder(path)
        val dir = File(path)
        val root = roots().filter { path == it || path.startsWith("$it/") }.maxByOrNull { it.length }
            ?: old?.root ?: (dir.parent ?: path)
        val base = old ?: FolderEntity(
            path = path, name = dir.name, root = root, imageCount = 0, totalSize = 0,
            coverPath = null, coverMtime = 0, dirMtime = 0, newestTaken = 0, newestModified = 0,
        )
        dao.upsertFolder(
            base.copy(
                imageCount = st.c,
                totalSize = st.s,
                coverPath = newest?.path,
                coverMtime = newest?.modified ?: 0,
                newestTaken = st.t ?: 0,
                newestModified = st.m ?: 0,
            )
        )
    }

    /** Permanently deletes files. Returns how many failed. */
    suspend fun delete(list: List<ImageEntity>): Int = withContext(Dispatchers.IO) {
        val ok = ArrayList<Long>()
        for (img in list) {
            val f = File(img.path)
            if (f.delete() || !f.exists()) ok += img.id
        }
        ok.chunked(500).forEach { dao.deleteImageIds(it) }
        list.map { it.folder }.distinct().forEach { refreshFolder(it) }
        mediaScan(list.map { it.path })
        list.size - ok.size
    }

    /** Moves files into target folder; a name already there gets " (1)", " (2)"... Returns failures. */
    suspend fun move(list: List<ImageEntity>, target: String): Int = withContext(Dispatchers.IO) {
        val dest = File(target)
        dest.mkdirs()
        var failed = 0
        val changed = ArrayList<String>()
        for (img in list) {
            if (img.folder == target) continue
            val src = File(img.path)
            val newName = uniqueName(dest, img.name)
            val out = File(dest, newName)
            val moved = src.renameTo(out) || try {
                src.copyTo(out, overwrite = false)
                out.setLastModified(src.lastModified())
                src.delete()
                true
            } catch (_: Exception) {
                out.delete(); false
            }
            if (moved) {
                dao.relocateImage(img.id, target, newName)
                changed += img.path; changed += out.path
            } else failed++
        }
        (list.map { it.folder } + target).distinct().forEach { refreshFolder(it) }
        mediaScan(changed)
        failed
    }

    fun uniqueName(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (File(dir, "$base ($i)$ext").exists()) i++
        return "$base ($i)$ext"
    }

    /** Returns an error message, or null on success. */
    suspend fun renameImage(img: ImageEntity, newBase: String): String? = withContext(Dispatchers.IO) {
        val clean = newBase.trim()
        if (clean.isEmpty() || clean.contains('/')) return@withContext "Invalid name"
        val ext = img.name.substringAfterLast('.', "")
        val newName = if (ext.isEmpty()) clean else "$clean.$ext"
        if (newName == img.name) return@withContext null
        val src = File(img.path)
        val out = File(img.folder, newName)
        if (out.exists()) return@withContext "A file with that name already exists"
        if (!src.renameTo(out)) return@withContext "Could not rename"
        dao.relocateImage(img.id, img.folder, newName)
        refreshFolder(img.folder)
        mediaScan(listOf(src.path, out.path))
        null
    }

    suspend fun renameFolder(folder: FolderEntity, newName: String): String? = withContext(Dispatchers.IO) {
        val clean = newName.trim()
        if (clean.isEmpty() || clean.contains('/')) return@withContext "Invalid name"
        val src = File(folder.path)
        val parent = src.parentFile ?: return@withContext "Could not rename"
        val out = File(parent, clean)
        if (out.path == src.path) return@withContext null
        if (out.exists()) return@withContext "A folder with that name already exists"
        if (!src.renameTo(out)) return@withContext "Could not rename"
        dao.renameFolderTree(src.path, out.path, clean)
        mediaScan(listOf(out.path))
        null
    }

    suspend fun createFolder(parent: String, name: String): String? = withContext(Dispatchers.IO) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/')) return@withContext null
        val f = File(parent, clean)
        if (f.exists() || f.mkdirs()) f.path else null
    }

    /** which = WallpaperManager.FLAG_SYSTEM, FLAG_LOCK or both. */
    suspend fun setWallpaper(path: String, which: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val dm = ctx.resources.displayMetrics
            val maxSide = max(dm.widthPixels, dm.heightPixels) * 2
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { d, info, _ ->
                val big = max(info.size.width, info.size.height)
                if (big > maxSide) {
                    val s = maxSide.toFloat() / big
                    d.setTargetSize(max(1, (info.size.width * s).toInt()), max(1, (info.size.height * s).toInt()))
                }
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            WallpaperManager.getInstance(ctx).setBitmap(bmp, null, true, which)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun mediaScan(paths: List<String>) {
        if (paths.isEmpty()) return
        try {
            MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null, null)
        } catch (_: Exception) {
        }
    }
}
