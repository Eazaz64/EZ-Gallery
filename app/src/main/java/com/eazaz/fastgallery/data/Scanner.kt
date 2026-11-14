package com.eazaz.fastgallery.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

val IMAGE_EXT = setOf("webp", "jpg", "jpeg", "png", "heic", "heif", "bmp", "avif")

/**
 * Scans only the main folders the user picked.
 * Speed tricks:
 *  - folders are processed level by level, 6 in parallel
 *  - a folder whose modified time is unchanged is NOT re-read; only its
 *    sub-folders are looked for (names already known as images are skipped)
 *  - one stat per image (size + time together)
 */
class Scanner(private val dao: GalleryDao) {

    data class Progress(
        val running: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val images: Int = 0,
        val current: String = "",
        val finishedAt: Long = 0,
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress
    private val mutex = Mutex()

    suspend fun scan(roots: List<String>, maxDepth: Int) = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext
        try {
            _progress.value = Progress(running = true)
            val existing = dao.allFolders().associateBy { it.path }
            val seen = ConcurrentHashMap.newKeySet<String>()
            val done = AtomicInteger()
            val total = AtomicInteger()
            val images = AtomicInteger()
            val par = Dispatchers.IO.limitedParallelism(6)

            val cleanRoots = roots.map { it.trimEnd('/') }.distinct().filter { File(it).isDirectory }
            var level: List<Triple<String, String, Int>> = cleanRoots.map { Triple(it, it, 0) }
            total.addAndGet(level.size)

            while (level.isNotEmpty()) {
                val current = level
                level = coroutineScope {
                    current.map { (dir, root, depth) ->
                        async(par) {
                            val subs = try {
                                processDir(dir, root, existing, seen, images)
                            } catch (e: Exception) {
                                emptyList()
                            }
                            done.incrementAndGet()
                            val nextDepth = depth + 1
                            val keep = if (maxDepth >= 0 && nextDepth > maxDepth) emptyList() else subs
                            total.addAndGet(keep.size)
                            _progress.value = Progress(true, done.get(), total.get(), images.get(), dir)
                            keep.map { Triple(it, root, nextDepth) }
                        }
                    }.awaitAll().flatten()
                }
            }

            for (path in existing.keys) if (path !in seen) dao.removeFolder(path)
            _progress.value = Progress(false, done.get(), total.get(), images.get(), "", System.currentTimeMillis())
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun processDir(
        dir: String,
        root: String,
        existing: Map<String, FolderEntity>,
        seen: MutableSet<String>,
        images: AtomicInteger,
    ): List<String> {
        val f = File(dir)
        val mtime = f.lastModified()
        val names = f.list() ?: return emptyList()
        val old = existing[dir]
        val subdirs = ArrayList<String>()

        if (old != null && old.dirMtime == mtime && old.dirMtime != 0L) {
            // Unchanged folder: images in DB are still correct; just find sub-folders.
            for (n in names) {
                if (n.startsWith(".")) continue
                if (n.substringAfterLast('.', "").lowercase() in IMAGE_EXT) continue
                val c = File(f, n)
                if (c.isDirectory) subdirs += c.path
            }
            seen += dir
            images.addAndGet(old.imageCount)
            return subdirs
        }

        val imgs = ArrayList<ImageEntity>()
        for (n in names) {
            if (n.startsWith(".")) continue
            val c = File(f, n)
            val ext = n.substringAfterLast('.', "").lowercase()
            if (ext in IMAGE_EXT) {
                try {
                    val a = Files.readAttributes(c.toPath(), BasicFileAttributes::class.java)
                    if (a.isRegularFile) {
                        val m = a.lastModifiedTime().toMillis()
                        imgs += ImageEntity(folder = dir, name = n, size = a.size(), modified = m, taken = DateParse.taken(n, m))
                    } else if (a.isDirectory) subdirs += c.path
                } catch (_: Exception) {
                }
            } else if (c.isDirectory) {
                subdirs += c.path
            }
        }

        if (imgs.isNotEmpty()) {
            // Keep rows (and IDs) of files that are unchanged; insert only new/changed ones.
            val oldImgs = if (old != null) dao.images(dir).associateBy { it.name } else emptyMap()
            val finalList = ArrayList<ImageEntity>(imgs.size)
            val inserts = ArrayList<ImageEntity>()
            val keep = HashSet<Long>()
            for (n in imgs) {
                val o = oldImgs[n.name]
                if (o != null && o.size == n.size && o.modified == n.modified) {
                    keep += o.id; finalList += o
                } else {
                    inserts += n; finalList += n
                }
            }
            val removeIds = oldImgs.values.filter { it.id !in keep }.map { it.id }
            val newest = finalList.maxBy { it.taken }
            val base = old ?: FolderEntity(
                path = dir, name = f.name, root = root, imageCount = 0, totalSize = 0,
                coverPath = null, coverMtime = 0, dirMtime = 0, newestTaken = 0, newestModified = 0,
            )
            val entity = base.copy(
                name = f.name,
                root = root,
                imageCount = finalList.size,
                totalSize = finalList.sumOf { it.size },
                coverPath = newest.path,
                coverMtime = newest.modified,
                dirMtime = mtime,
                newestTaken = newest.taken,
                newestModified = finalList.maxOf { it.modified },
            )
            dao.applyFolderDiff(entity, removeIds, inserts)
            seen += dir
            images.addAndGet(imgs.size)
        }
        return subdirs
    }
}

/** "Date taken" from the file name (IMG_20240312_161800, Screenshot_2024-03-12-16-18-00, ...).
 *  Falls back to the file's modified time. Reading EXIF for 400k files would make scanning very slow. */
object DateParse {
    private val re = Regex("(19\\d{2}|20\\d{2})[-_.]?(\\d{2})[-_.]?(\\d{2})(?:[-_ .T]?(\\d{2})[-_.:]?(\\d{2})[-_.:]?(\\d{2}))?")
    private val maxYear = LocalDateTime.now().year + 1

    fun taken(name: String, fallback: Long): Long {
        val m = re.find(name) ?: return fallback
        val g = m.groupValues
        val y = g[1].toInt(); val mo = g[2].toInt(); val d = g[3].toInt()
        if (y > maxYear || mo !in 1..12 || d !in 1..31) return fallback
        var h = g.getOrNull(4)?.toIntOrNull() ?: 0
        var mi = g.getOrNull(5)?.toIntOrNull() ?: 0
        var s = g.getOrNull(6)?.toIntOrNull() ?: 0
        if (h > 23 || mi > 59 || s > 59) { h = 0; mi = 0; s = 0 }
        return try {
            LocalDateTime.of(y, mo, d, h, mi, s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            fallback
        }
    }
}

object Sorts {
    fun images(list: List<ImageEntity>, key: String, asc: Boolean): List<ImageEntity> {
        val cmp: Comparator<ImageEntity> = when (key) {
            "name" -> Comparator { a, b -> naturalCompare(a.name, b.name) }
            "modified" -> compareBy<ImageEntity> { it.modified }.thenComparator { a, b -> naturalCompare(a.name, b.name) }
            "size" -> compareBy<ImageEntity> { it.size }.thenComparator { a, b -> naturalCompare(a.name, b.name) }
            else -> compareBy<ImageEntity> { it.taken }.thenComparator { a, b -> naturalCompare(a.name, b.name) }
        }
        val s = list.sortedWith(cmp)
        return if (asc) s else s.asReversed()
    }

    fun folders(list: List<FolderEntity>, key: String, asc: Boolean): List<FolderEntity> {
        val cmp: Comparator<FolderEntity> = when (key) {
            "name" -> Comparator { a, b -> naturalCompare(a.name, b.name) }
            "modified" -> compareBy<FolderEntity> { it.newestModified }.thenComparator { a, b -> naturalCompare(a.name, b.name) }
            "size" -> compareBy<FolderEntity> { it.totalSize }.thenComparator { a, b -> naturalCompare(a.name, b.name) }
            else -> compareBy<FolderEntity> { it.newestTaken }.thenComparator { a, b -> naturalCompare(a.name, b.name) }
        }
        val s = list.sortedWith(cmp)
        return if (asc) s else s.asReversed()
    }
}

/** Natural order: img2 before img10, case-insensitive. */
fun naturalCompare(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            val si = i
            while (i < a.length && a[i].isDigit()) i++
            val sj = j
            while (j < b.length && b[j].isDigit()) j++
            val na = a.substring(si, i).trimStart('0')
            val nb = b.substring(sj, j).trimStart('0')
            if (na.length != nb.length) return na.length - nb.length
            val c = na.compareTo(nb)
            if (c != 0) return c
        } else {
            val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
            if (c != 0) return c
            i++; j++
        }
    }
    return (a.length - i) - (b.length - j)
}
