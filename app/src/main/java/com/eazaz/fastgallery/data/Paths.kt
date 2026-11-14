package com.eazaz.fastgallery.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/** Internal storage of the CURRENT user: /storage/emulated/0 in the main space,
 *  /storage/emulated/10 (or another number) in Xiaomi Second Space / work profile. */
fun primaryStorage(): String = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')

/** Short form for display: /Pictures/My instead of /storage/emulated/0/Pictures/My */
fun shortPath(p: String): String = p.removePrefix(primaryStorage())

private fun docIdToPath(id: String): String? {
    val parts = id.split(":", limit = 2)
    if (parts.size < 2 && !id.contains(':')) return null
    val vol = parts[0]
    val rel = parts.getOrElse(1) { "" }.trim('/')
    val base = if (vol.equals("primary", ignoreCase = true)) primaryStorage() else "/storage/$vol"
    return if (rel.isEmpty()) base else "$base/$rel"
}

/** content://...tree/primary:Pictures/My  ->  <primaryStorage>/Pictures/My */
fun treeUriToPath(uri: Uri): String? {
    val id = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { return null }
    return docIdToPath(id)
}

/** Best effort: turn an image Uri sent by another app into a real file path. */
fun resolvePath(ctx: Context, uri: Uri): String? {
    if (uri.scheme == "file") return uri.path
    if (uri.scheme != "content") return null
    try {
        if (uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isDocumentUri(ctx, uri)) {
            docIdToPath(DocumentsContract.getDocumentId(uri))?.let { if (File(it).exists()) return it }
        }
    } catch (_: Exception) {
    }
    try {
        @Suppress("DEPRECATION")
        ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                @Suppress("DEPRECATION")
                val i = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (i >= 0) c.getString(i)?.let { if (File(it).exists()) return it }
            }
        }
    } catch (_: Exception) {
    }
    // Many file managers put the real path inside the Uri: content://app/root/storage/emulated/0/...
    uri.path?.let { p ->
        val i = p.indexOf("/storage/")
        if (i >= 0) {
            val cand = p.substring(i)
            if (File(cand).exists()) return cand
        }
    }
    return null
}

/** /storage/emulated/0 (or the SD card root) that this path lives on. */
fun storageRootOf(p: String): String {
    val base = primaryStorage()
    if (p == base || p.startsWith("$base/")) return base
    if (p.startsWith("/storage/")) return "/storage/" + p.removePrefix("/storage/").substringBefore('/')
    return p.substringBeforeLast('/')
}

/** True if [dir] is the storage root's "Android" folder (app data, obb, app media) — never scanned
 *  as part of a bigger main folder. You can still add e.g. Android/media/com.whatsapp on its own. */
fun isAndroidAppFolder(parent: String, name: String): Boolean =
    name == "Android" && parent == storageRootOf(parent)

/** Is [path] scanned when [root] is a main folder? (The storage root's Android folder is skipped.) */
fun isInside(path: String, root: String): Boolean {
    if (path == root) return true
    if (!path.startsWith("$root/")) return false
    val android = storageRootOf(path) + "/Android"
    val rootAboveAndroid = android.startsWith("$root/")
    val pathInAndroid = path == android || path.startsWith("$android/")
    return !(rootAboveAndroid && pathInAndroid)
}

/** Remove duplicates and folders already inside another main folder (avoids scanning twice). */
fun normalizeRoots(list: List<String>): List<String> {
    val out = ArrayList<String>()
    for (r in list.map { it.trimEnd('/') }.filter { it.isNotEmpty() }.distinct().sortedBy { it.length }) {
        if (out.none { isInside(r, it) }) out += r
    }
    // keep the user's original order
    return list.map { it.trimEnd('/') }.distinct().filter { it in out }
}

fun isCovered(path: String, roots: List<String>): Boolean = roots.any { isInside(path, it) }

data class FolderSuggestion(val path: String, val label: String, val count: Int)

/** Ask Android which folders hold images, grouped into easy top-level choices
 *  (DCIM, Pictures, Download, WhatsApp, Telegram, ...). */
fun findImageFolders(ctx: Context): List<FolderSuggestion> {
    val base = primaryStorage()
    val counts = HashMap<String, Int>()
    var total = 0
    try {
        @Suppress("DEPRECATION")
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns.DATA), null, null, null,
        )?.use { c ->
            @Suppress("DEPRECATION")
            val i = c.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (i >= 0) while (c.moveToNext()) {
                val p = c.getString(i) ?: continue
                if (p.endsWith(".gif", ignoreCase = true)) continue
                val key = suggestionRoot(p, base) ?: continue
                counts[key] = (counts[key] ?: 0) + 1
                if (isInside(p, base)) total++
            }
        }
    } catch (_: Exception) {
    }
    val list = counts.map { (k, v) -> FolderSuggestion(k, labelFor(k, base), v) }.sortedByDescending { it.count }
    return listOf(FolderSuggestion(base, "Whole internal storage (without Android app folders)", total)) + list
}

private fun suggestionRoot(p: String, base: String): String? {
    val root = when {
        p.startsWith("$base/") -> base
        p.startsWith("/storage/") && !p.startsWith("/storage/emulated/") -> "/storage/" + p.removePrefix("/storage/").substringBefore('/')
        else -> return null
    }
    val segs = p.removePrefix("$root/").split('/')
    if (segs.size < 2) return null // image directly in the storage root
    val take = if (segs[0] == "Android" && segs.size > 3 && segs[1] == "media") 3 else 1
    return root + "/" + segs.take(take).joinToString("/")
}

private fun labelFor(path: String, base: String): String {
    val sd = !path.startsWith(base)
    val rel = if (sd) path.removePrefix("/storage/").substringAfter('/') else path.removePrefix("$base/")
    val name = when {
        rel == "DCIM" -> "Camera (DCIM)"
        rel == "Pictures" -> "Pictures (screenshots, saved images)"
        rel == "Download" -> "Downloads"
        rel.endsWith("com.whatsapp") -> "WhatsApp"
        rel.endsWith("com.whatsapp.w4b") -> "WhatsApp Business"
        rel.endsWith("org.telegram.messenger") -> "Telegram"
        rel.endsWith("com.facebook.orca") -> "Messenger"
        rel.endsWith("com.instagram.android") -> "Instagram"
        rel.startsWith("Android/media/") -> rel.removePrefix("Android/media/")
        else -> rel
    }
    return if (sd) "$name (SD card)" else name
}
