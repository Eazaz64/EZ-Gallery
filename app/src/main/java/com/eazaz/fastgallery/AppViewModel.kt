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

    private var autoScanned = false
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
        if (granted && settings.state.roots.isNotEmpty() && !autoScanned) {
            autoScanned = true
            startScan()
        }
        if (!trimmed) {
            trimmed = true
            viewModelScope.launch(Dispatchers.IO) {
                try { thumbs.trim(settings.state.cacheLimitMb * 1024L * 1024L) } catch (_: Exception) {}
            }
        }
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
