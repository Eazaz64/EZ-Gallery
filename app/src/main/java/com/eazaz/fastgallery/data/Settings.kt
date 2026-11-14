package com.eazaz.fastgallery.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class AppSettings(
    val roots: List<String> = emptyList(),
    val depth: Int = -1, // -1 = all levels
    val folderSortKey: String = "name",
    val folderSortAsc: Boolean = true,
    val imageSortKey: String = "taken",
    val imageSortAsc: Boolean = false,
    val rememberPerFolder: Boolean = true,
    val folderCols: Int = 3,
    val imageCols: Int = 4,
    val slideSecs: Int = 5,
    val slideTransition: String = "Fade",
    val slideLoop: Boolean = false,
    val slideShuffle: Boolean = false,
    val slideKeepOn: Boolean = true,
    val cAppBg: String = "#FFFFFF",
    val cText: String = "#111827",
    val cAccent: String = "#2563EB",
    val cViewerBg: String = "#F3F4F6",
    val cBarBg: String = "#FFFFFF",
    val cBarText: String = "#111827",
    val cacheLimitMb: Int = 1500,
    val lastScan: Long = 0,
)

class SettingsStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var state by mutableStateOf(load())
        private set

    fun update(f: (AppSettings) -> AppSettings) {
        state = f(state)
        save(state)
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            roots = (sp.getString("roots", "") ?: "").split('\n').filter { it.isNotBlank() },
            depth = sp.getInt("depth", d.depth),
            folderSortKey = sp.getString("folderSortKey", d.folderSortKey) ?: d.folderSortKey,
            folderSortAsc = sp.getBoolean("folderSortAsc", d.folderSortAsc),
            imageSortKey = sp.getString("imageSortKey", d.imageSortKey) ?: d.imageSortKey,
            imageSortAsc = sp.getBoolean("imageSortAsc", d.imageSortAsc),
            rememberPerFolder = sp.getBoolean("rememberPerFolder", d.rememberPerFolder),
            folderCols = sp.getInt("folderCols", d.folderCols),
            imageCols = sp.getInt("imageCols", d.imageCols),
            slideSecs = sp.getInt("slideSecs", d.slideSecs),
            slideTransition = sp.getString("slideTransition", d.slideTransition) ?: d.slideTransition,
            slideLoop = sp.getBoolean("slideLoop", d.slideLoop),
            slideShuffle = sp.getBoolean("slideShuffle", d.slideShuffle),
            slideKeepOn = sp.getBoolean("slideKeepOn", d.slideKeepOn),
            cAppBg = sp.getString("cAppBg", d.cAppBg) ?: d.cAppBg,
            cText = sp.getString("cText", d.cText) ?: d.cText,
            cAccent = sp.getString("cAccent", d.cAccent) ?: d.cAccent,
            cViewerBg = sp.getString("cViewerBg", d.cViewerBg) ?: d.cViewerBg,
            cBarBg = sp.getString("cBarBg", d.cBarBg) ?: d.cBarBg,
            cBarText = sp.getString("cBarText", d.cBarText) ?: d.cBarText,
            cacheLimitMb = sp.getInt("cacheLimitMb", d.cacheLimitMb),
            lastScan = sp.getLong("lastScan", d.lastScan),
        )
    }

    private fun save(s: AppSettings) {
        sp.edit()
            .putString("roots", s.roots.joinToString("\n"))
            .putInt("depth", s.depth)
            .putString("folderSortKey", s.folderSortKey)
            .putBoolean("folderSortAsc", s.folderSortAsc)
            .putString("imageSortKey", s.imageSortKey)
            .putBoolean("imageSortAsc", s.imageSortAsc)
            .putBoolean("rememberPerFolder", s.rememberPerFolder)
            .putInt("folderCols", s.folderCols)
            .putInt("imageCols", s.imageCols)
            .putInt("slideSecs", s.slideSecs)
            .putString("slideTransition", s.slideTransition)
            .putBoolean("slideLoop", s.slideLoop)
            .putBoolean("slideShuffle", s.slideShuffle)
            .putBoolean("slideKeepOn", s.slideKeepOn)
            .putString("cAppBg", s.cAppBg)
            .putString("cText", s.cText)
            .putString("cAccent", s.cAccent)
            .putString("cViewerBg", s.cViewerBg)
            .putString("cBarBg", s.cBarBg)
            .putString("cBarText", s.cBarText)
            .putInt("cacheLimitMb", s.cacheLimitMb)
            .putLong("lastScan", s.lastScan)
            .apply()
    }
}
