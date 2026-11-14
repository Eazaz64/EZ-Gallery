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
