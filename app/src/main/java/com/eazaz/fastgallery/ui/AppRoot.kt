package com.eazaz.fastgallery.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import com.eazaz.fastgallery.AppViewModel
import com.eazaz.fastgallery.Screen

@Composable
fun AppRoot(vm: AppViewModel, activity: ComponentActivity) {
    val s = vm.settings.state
    val colors = remember(s.cAppBg, s.cText, s.cAccent, s.cViewerBg, s.cBarBg, s.cBarText) {
        AppColors(
            appBg = parseHex(s.cAppBg) ?: DefaultColors.appBg,
            text = parseHex(s.cText) ?: DefaultColors.text,
            accent = parseHex(s.cAccent) ?: DefaultColors.accent,
            viewerBg = parseHex(s.cViewerBg) ?: DefaultColors.viewerBg,
            barBg = parseHex(s.cBarBg) ?: DefaultColors.barBg,
            barText = parseHex(s.cBarText) ?: DefaultColors.barText,
        )
    }
    val top = vm.stack.lastOrNull() ?: Screen.Home
    // Status/navigation bar icons: dark on light backgrounds, light on dark ones.
    val barColor = if (top is Screen.Viewer || top is Screen.Slideshow) colors.barBg else colors.appBg
    LaunchedEffect(barColor) {
        val t = android.graphics.Color.TRANSPARENT
        val style = if (barColor.luminance() < 0.5f) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    AppTheme(colors) {
        Box(Modifier.fillMaxSize().background(colors.appBg)) {
            when {
                !vm.hasPermission -> PermissionScreen()
                s.roots.isEmpty() -> RootsScreen(vm, firstRun = true)
                else -> {
                    val holder = rememberSaveableStateHolder()
                    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
                    holder.SaveableStateProvider(top.toString()) {
                        when (top) {
                            Screen.Home -> HomeScreen(vm)
                            is Screen.Folder -> FolderScreen(vm, top.path)
                            is Screen.Viewer -> ViewerScreen(vm, top.folder, top.index)
                            is Screen.Slideshow -> SlideshowScreen(vm, top.folder, top.start)
                            Screen.Settings -> SettingsScreen(vm)
                            Screen.Colors -> ColorsScreen(vm)
                            Screen.Roots -> RootsScreen(vm, firstRun = false)
                            Screen.Scan -> ScanScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
