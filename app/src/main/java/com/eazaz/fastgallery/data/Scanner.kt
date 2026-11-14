package com.eazaz.fastgallery.data

import android.content.Context
import android.provider.MediaStore
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
/** Folders found through Android's media index get this as their "root". */
const val MEDIA_ROOT = "@media"

class Scanner(private val dao: GalleryDao, private val ctx: Context) {

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
    private val sp = ctx.getSharedPreferences("mediastore", Context.MODE_PRIVATE)

    /**
     * autoFind: use Android's media index (MediaStore) to find every folder with images, like other galleries.
     *   The full index is read only the first time; after that only what changed (generation counter).
     * roots: extra folders walked with all their subfolders (also finds folders Android skips, e.g. with .nomedia).
     * excludes: folders (and their subfolders) never shown.
     * Image lists always come from the folders on disk; unchanged folders (same modified time) are not re-read.
     */
    suspend fun scan(
        autoFind: Boolean,
        roots: List<String>,
        maxDepth: Int,
        excludes: List<String>,
        forceFull: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        mutex.lock() // a second request waits for the running scan, then runs once more
        try {
            _progress.value = Progress(running = true)
            val existing = dao.allFolders().associateBy { it.path }
            val seen = ConcurrentHashMap.newKeySet<String>()
            val done = AtomicInteger()
            val total = AtomicInteger()
            val images = AtomicInteger()
            val par = Dispatchers.IO.limitedParallelism(6)
            fun excluded(p: String) = excludes.any { p == it || p.startsWith("$it/") }

            val cleanRoots = roots.map { it.trimEnd('/') }.distinct().filter { File(it).isDirectory && !excluded(it) }

            // 1) Folders Android knows have images (+ the ones we already show), read one by one (no subfolders).
            if (autoFind) {
                val (dirs, full) = mediaDirs(forceFull)
                val flat = LinkedHashSet<String>()
                if (!full) flat += existing.values.filter { it.root == MEDIA_ROOT }.map { it.path }
                flat += dirs
                // folders inside an extra folder are handled by step 2 (avoid reading them twice)
                val list = flat.filter { !excluded(it) && cleanRoots.none { r -> isInside(it, r) } }
                total.addAndGet(list.size)
                coroutineScope {
                    list.map { dir ->
                        async(par) {
                            try { processDir(dir, MEDIA_ROOT, existing, seen, images) } catch (_: Exception) {}
                            done.incrementAndGet()
                            _progress.value = Progress(true, done.get(), total.get(), images.get(), dir)
                        }
                    }.awaitAll()
                }
            }

            // 2) Extra folders: walk with subfolders, level by level.
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
                            val keep = if (maxDepth >= 0 && nextDepth > maxDepth) emptyList() else subs.filter { !excluded(it) }
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

    /** Folders with images from MediaStore. Returns (folders, wasFullRead).
     *  Normally only images added/changed since the last call are read, which takes milliseconds. */
    private fun mediaDirs(forceFull: Boolean): Pair<Set<String>, Boolean> {
        val out = HashSet<String>()
        val vols = try { MediaStore.getExternalVolumeNames(ctx).toList() } catch (_: Exception) { listOf(MediaStore.VOLUME_EXTERNAL_PRIMARY) }
        val state = vols.map { v ->
            val gen = try { MediaStore.getGeneration(ctx, v) } catch (_: Exception) { -1L }
            val ver = try { MediaStore.getVersion(ctx, v) } catch (_: Exception) { "" }
            Triple(v, gen, ver)
        }
        val full = forceFull || state.any { (v, _, ver) ->
            sp.getLong("gen_$v", -1L) < 0 || sp.getString("ver_$v", null) != ver
        }
        val ed = sp.edit()
        for ((v, gen, ver) in state) {
            val lastGen = sp.getLong("gen_$v", -1L)
            if (!full && gen >= 0 && gen == lastGen) continue
            val uri = MediaStore.Images.Media.getContentUri(v)
            val sel = if (full) null else "${MediaStore.MediaColumns.GENERATION_MODIFIED} > ?"
            val args = if (full) null else arrayOf(lastGen.toString())
            try {
                @Suppress("DEPRECATION")
                ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), sel, args, null)?.use { c ->
                    @Suppress("DEPRECATION")
                    val i = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (i >= 0) while (c.moveToNext()) {
                        val p = c.getString(i) ?: continue
                        val ext = p.substringAfterLast('.', "").lowercase()
                        if (ext !in IMAGE_EXT) continue
                        val parent = p.substringBeforeLast('/', "")
                        if (parent.isNotEmpty()) out += parent
                    }
                }
                ed.putLong("gen_$v", gen).putString("ver_$v", ver)
            } catch (_: Exception) {
            }
        }
        ed.apply()
        return out to full
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
                if (n.startsWith(".") || isAndroidAppFolder(dir, n)) continue
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
            if (n.startsWith(".") || isAndroidAppFolder(dir, n)) continue
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
