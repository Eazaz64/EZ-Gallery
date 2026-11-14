package com.eazaz.fastgallery

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eazaz.fastgallery.data.AppDb
import com.eazaz.fastgallery.data.FileOps
import com.eazaz.fastgallery.data.FolderEntity
import com.eazaz.fastgallery.data.ImageEntity
import com.eazaz.fastgallery.data.Scanner
import com.eazaz.fastgallery.data.SettingsStore
import com.eazaz.fastgallery.data.DateParse
import com.eazaz.fastgallery.data.Sorts
import com.eazaz.fastgallery.data.path
import com.eazaz.fastgallery.data.resolvePath
import com.eazaz.fastgallery.ui.EditRender
import com.eazaz.fastgallery.ui.EditState
import android.net.Uri
import android.os.Environment
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data class Folder(val path: String) : Screen
    data class Viewer(val folder: String, val index: Int) : Screen
    data class Slideshow(val folder: String, val start: Int) : Screen
    data object Settings : Screen
    data object Colors : Screen
    data object Roots : Screen
    data object Scan : Screen
    data class Editor(val path: String) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val dao = AppDb.get(app).dao()
    val settings = SettingsStore(app)
    val scanner = Scanner(dao)
    val ops = FileOps(app, dao) { settings.state.roots }
    val thumbs get() = App.instance.thumbs

    val stack = mutableStateListOf<Screen>(Screen.Home)
    var hasPermission by mutableStateOf(false)
        private set

    /** Folder list kept in memory so the home screen shows instantly (no flash when going back).
     *  While scanning it updates at most ~3x per second. */
    val folders: StateFlow<List<FolderEntity>?> = dao.foldersFlow()
        .conflate()
        .transform { emit(it); delay(300) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Last sorted image list per folder, so going back to a folder is instant. */
    val sortedCache = HashMap<String, List<ImageEntity>>()

    /** An image opened from another app that is not inside the main folders. */
    var externalUri by mutableStateOf<Uri?>(null)
        private set

    private var lastAutoScan = 0L
    private var trimmed = false
    private val lastViewed = HashMap<String, Int>()

    fun push(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun resetTo(vararg s: Screen) { stack.clear(); stack.addAll(s) }

    fun setLastViewed(folder: String, index: Int) { lastViewed[folder] = index }
    fun takeLastViewed(folder: String): Int? = lastViewed.remove(folder)

    init {
        // Main folders saved with the wrong user's storage path (e.g. /storage/emulated/0
        // while running in Second Space at /storage/emulated/10): fix them automatically.
        val primary = android.os.Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        val fixed = settings.state.roots.map { r ->
            val m = Regex("^/storage/emulated/\\d+").find(r)
            if (m != null && m.value != primary && !java.io.File(r).isDirectory) primary + r.substring(m.value.length) else r
        }.distinct()
        if (fixed != settings.state.roots) settings.update { it.copy(roots = fixed) }
    }

    fun onResume(granted: Boolean) {
        hasPermission = granted
        // Pick up new / deleted images every time the app comes back (fast: only changed folders are read).
        val now = System.currentTimeMillis()
        if (granted && settings.state.roots.isNotEmpty() && now - lastAutoScan > 15_000) {
            lastAutoScan = now
            startScan()
        }
        if (!trimmed) {
            trimmed = true
            viewModelScope.launch(Dispatchers.IO) {
                try { thumbs.trim(settings.state.cacheLimitMb * 1024L * 1024L) } catch (_: Exception) {}
            }
        }
    }

    /** Image opened from a file manager / chat app. If it lives in a scanned folder, open the full viewer there. */
    fun openExternal(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        val path = withContext(Dispatchers.IO) { resolvePath(app, uri) }
        if (path != null && Environment.isExternalStorageManager() && File(path).canRead()) {
            val folderPath = File(path).parent
            val f = folderPath?.let { dao.folder(it) }
            if (f != null) {
                val list = withContext(Dispatchers.Default) { sortedImages(f) }
                val idx = list.indexOfFirst { it.path == path }
                if (idx >= 0) {
                    sortedCache[f.path] = list
                    externalUri = null
                    resetTo(Screen.Home, Screen.Folder(f.path), Screen.Viewer(f.path, idx))
                    return@launch
                }
            }
        }
        externalUri = uri
    }

    fun closeExternal() { externalUri = null }

    suspend fun sortedImages(f: FolderEntity): List<ImageEntity> {
        val s = settings.state
        val key = (if (s.rememberPerFolder) f.sortKey else null) ?: s.imageSortKey
        val asc = (if (s.rememberPerFolder) f.sortAsc else null) ?: s.imageSortAsc
        return Sorts.images(dao.images(f.path), key, asc)
    }

    fun startScan() {
        viewModelScope.launch {
            val s = settings.state
            scanner.scan(s.roots, s.depth)
            settings.update { it.copy(lastScan = System.currentTimeMillis()) }
        }
    }

    fun toast(msg: String) = Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()

    fun togglePin(f: FolderEntity) = viewModelScope.launch {
        if (f.pinned) dao.setPinned(f.path, false, 0) else dao.setPinned(f.path, true, System.currentTimeMillis())
    }

    fun setFolderSort(path: String, key: String?, asc: Boolean?) = viewModelScope.launch {
        dao.setFolderSort(path, key, asc)
    }

    fun clearFolderSorts() = viewModelScope.launch { dao.clearFolderSorts() }

    fun delete(list: List<ImageEntity>, done: () -> Unit = {}) = viewModelScope.launch {
        val failed = ops.delete(list)
        toast(if (failed == 0) "Deleted ${list.size}" else "Deleted ${list.size - failed}, $failed failed")
        done()
    }

    fun move(list: List<ImageEntity>, target: String, done: () -> Unit = {}) = viewModelScope.launch {
        val failed = ops.move(list, target)
        toast(if (failed == 0) "Moved ${list.size}" else "Moved ${list.size - failed}, $failed failed")
        done()
    }

    fun copy(list: List<ImageEntity>, target: String, done: () -> Unit = {}) = viewModelScope.launch {
        val failed = ops.copy(list, target)
        toast(if (failed == 0) "Copied ${list.size}" else "Copied ${list.size - failed}, $failed failed")
        done()
    }

    /** Saves the edited image as a new file next to the original. */
    fun saveEdit(path: String, st: EditState, done: (Boolean) -> Unit) = viewModelScope.launch {
        val out = withContext(Dispatchers.Default) {
            try { EditRender.saveCopy(path, st) } catch (_: Throwable) { null }
        }
        if (out != null) {
            val orig = File(path)
            ops.addFile(out, DateParse.taken(orig.name, orig.lastModified()))
            toast("Saved as copy: ${out.name}")
        } else {
            toast("Could not save the edited image")
        }
        done(out != null)
    }

    fun renameImage(img: ImageEntity, newBase: String) = viewModelScope.launch {
        ops.renameImage(img, newBase)?.let { toast(it) }
    }

    fun renameFolder(f: FolderEntity, newName: String) = viewModelScope.launch {
        val err = ops.renameFolder(f, newName)
        if (err != null) {
            toast(err)
        } else {
            // keep the open screens pointing at the new path
            val newPath = f.path.substringBeforeLast('/') + "/" + newName.trim()
            for (i in stack.indices) {
                when (val s = stack[i]) {
                    is Screen.Folder -> if (s.path == f.path) stack[i] = Screen.Folder(newPath)
                    is Screen.Viewer -> if (s.folder == f.path) stack[i] = s.copy(folder = newPath)
                    else -> {}
                }
            }
        }
    }

    fun createFolder(parent: String, name: String, done: (String?) -> Unit) = viewModelScope.launch {
        val p = ops.createFolder(parent, name)
        if (p == null) toast("Could not create folder")
        done(p)
    }

    fun setWallpaper(path: String, which: Int) = viewModelScope.launch {
        toast("Setting wallpaper…")
        val ok = ops.setWallpaper(path, which)
        toast(if (ok) "Wallpaper set" else "Could not set wallpaper")
    }

    fun clearThumbCache(done: () -> Unit) = viewModelScope.launch {
        withContext(Dispatchers.IO) { thumbs.clear() }
        done()
    }
}
