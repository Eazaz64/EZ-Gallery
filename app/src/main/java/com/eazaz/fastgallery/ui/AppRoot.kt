package com.eazaz.fastgallery.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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

/** The screen on top plus how deep the stack is, so we know if we went forward or back. */
private data class Entry(val screen: Screen, val depth: Int)

private val ease = FastOutSlowInEasing
private const val DUR = 300

private fun AnimatedContentTransitionScope<Entry>.screenTransition(from: Entry, to: Entry): ContentTransform {
    val forward = to.depth >= from.depth
    val media = to.screen is Screen.Viewer || to.screen is Screen.Slideshow || to.screen is Screen.Editor
    val leavingMedia = from.screen is Screen.Viewer || from.screen is Screen.Slideshow || from.screen is Screen.Editor
    return when {
        // Opening an image or a slideshow: zoom + fade in, like expanding from the grid.
        forward && media ->
            (fadeIn(tween(220, easing = ease)) + scaleIn(tween(DUR, easing = ease), initialScale = 0.92f)) togetherWith
                fadeOut(tween(220, delayMillis = 80))
        // Closing an image or a slideshow: shrink + fade out back to the grid.
        !forward && leavingMedia ->
            fadeIn(tween(180)) togetherWith
                (fadeOut(tween(200, easing = ease)) + scaleOut(tween(250, easing = ease), targetScale = 0.92f))
        // Normal screens: slide in from the right; going back slides the other way.
        forward ->
            (slideInHorizontally(tween(DUR, easing = ease)) { it / 3 } + fadeIn(tween(DUR, easing = ease))) togetherWith
                (slideOutHorizontally(tween(DUR, easing = ease)) { -it / 6 } + fadeOut(tween(200, easing = ease)))
        else ->
            (slideInHorizontally(tween(DUR, easing = ease)) { -it / 6 } + fadeIn(tween(DUR, easing = ease))) togetherWith
                (slideOutHorizontally(tween(DUR, easing = ease)) { it / 3 } + fadeOut(tween(200, easing = ease)))
    }.apply {
        // the screen we go to is drawn on top
        targetContentZIndex = if (forward) to.depth.toFloat() else -1f
    }.using(SizeTransform(clip = false))
}

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
    val barColor = if (top is Screen.Viewer || top is Screen.Slideshow || top is Screen.Editor || vm.externalUri != null) colors.barBg else colors.appBg
    LaunchedEffect(barColor) {
        val t = android.graphics.Color.TRANSPARENT
        val style = if (barColor.luminance() < 0.5f) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    AppTheme(colors) {
        Box(Modifier.fillMaxSize().background(colors.appBg)) {
            val ext = vm.externalUri
            when {
                ext != null -> ExternalViewer(vm, ext) {
                    vm.closeExternal()
                    if (vm.stack.size <= 1) activity.finish()
                }
                !vm.hasPermission -> PermissionScreen()
                s.roots.isEmpty() -> RootsScreen(vm, firstRun = true)
                else -> {
                    val holder = rememberSaveableStateHolder()
                    BackHandler(enabled = vm.stack.size > 1) { vm.back() }
                    AnimatedContent(
                        targetState = Entry(top, vm.stack.size),
                        contentKey = { it.screen.toString() },
                        transitionSpec = { screenTransition(initialState, targetState) },
                        label = "screens",
                        modifier = Modifier.fillMaxSize(),
                    ) { e ->
                        val scr = e.screen
                        Box(Modifier.fillMaxSize().background(if (scr is Screen.Viewer || scr is Screen.Slideshow || scr is Screen.Editor) colors.viewerBg else colors.appBg)) {
                            holder.SaveableStateProvider(scr.toString()) {
                                when (scr) {
                                    Screen.Home -> HomeScreen(vm)
                                    is Screen.Folder -> FolderScreen(vm, scr.path)
                                    is Screen.Viewer -> ViewerScreen(vm, scr.folder, scr.index)
                                    is Screen.Slideshow -> SlideshowScreen(vm, scr.folder, scr.start)
                                    Screen.Settings -> SettingsScreen(vm)
                                    Screen.Colors -> ColorsScreen(vm)
                                    Screen.Roots -> RootsScreen(vm, firstRun = false)
                                    Screen.Scan -> ScanScreen(vm)
                                    is Screen.Editor -> EditorScreen(vm, scr.path)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
