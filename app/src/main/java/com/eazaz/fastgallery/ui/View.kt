package com.eazaz.fastgallery.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.eazaz.fastgallery.AppViewModel
import com.eazaz.fastgallery.Screen
import com.eazaz.fastgallery.data.ImageEntity
import com.eazaz.fastgallery.data.path
import kotlinx.coroutines.delay
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import java.io.File

@Composable
private fun SystemBars(visible: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(visible) {
        val ctl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (visible) {
            ctl.show(WindowInsetsCompat.Type.systemBars())
        } else {
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctl.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

// =================== Viewer ===================

@Composable
fun ViewerScreen(vm: AppViewModel, folderPath: String, startIndex: Int, targetPath: String? = null) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val (folder, images) = rememberFolderImages(vm, folderPath)
    val list = images
    if (list == null) {
        Box(Modifier.fillMaxSize().background(c.viewerBg)); return
    }
    if (list.isEmpty()) {
        LaunchedEffect(Unit) { vm.back() }
        Box(Modifier.fillMaxSize().background(c.viewerBg)); return
    }

    // Find the image by its path (positions can shift when files are added, e.g. an edited copy).
    val first = targetPath?.let { p -> list.indexOfFirst { it.path == p }.takeIf { it >= 0 } } ?: startIndex
    val pager = rememberPagerState(initialPage = first.coerceIn(0, list.size - 1)) { list.size }
    var targetDone by rememberSaveable(targetPath) { mutableStateOf(false) }
    LaunchedEffect(list, targetPath) {
        if (targetPath != null && !targetDone) {
            val i = list.indexOfFirst { it.path == targetPath }
            if (i >= 0) {
                if (pager.currentPage != i) pager.scrollToPage(i)
                targetDone = true
            }
        }
    }
    var bars by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    val page = pager.currentPage.coerceIn(0, list.size - 1)
    val current: ImageEntity = list[page]

    LaunchedEffect(page) { vm.setLastViewed(folderPath, page) }
    SystemBars(bars)

    Box(Modifier.fillMaxSize().background(c.viewerBg)) {
        HorizontalPager(
            state = pager,
            key = { list.getOrNull(it)?.id ?: it.toLong() },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { p ->
            val img = list.getOrNull(p) ?: return@HorizontalPager
            ZoomableAsyncImage(
                model = ImageRequest.Builder(ctx).data(File(img.path)).build(),
                contentDescription = img.name,
                modifier = Modifier.fillMaxSize(),
                onClick = { bars = !bars },
            )
        }

        AnimatedVisibility(
            visible = bars,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(Modifier.fillMaxWidth().background(c.barBg).statusBarsPadding()) {
                TopBar(
                    title = current.name,
                    subtitle = "${fmtNum(page + 1)} of ${fmtNum(list.size)}",
                    onBack = { vm.back() },
                    bg = c.barBg, fg = c.barText,
                ) {
                    Box {
                        IconBtn(AppIcons.More, "More options", c.barText) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.appBg) {
                            MenuItem(AppIcons.Info, "Info") { menu = false; dialog = "info" }
                            MenuItem(AppIcons.Edit, "Rename") { menu = false; dialog = "rename" }
                            MenuItem(AppIcons.Wallpaper, "Set as wallpaper") { menu = false; dialog = "wallpaper" }
                            MenuItem(AppIcons.Crop, "Edit") { menu = false; vm.push(Screen.Editor(current.path)) }
                            MenuItem(AppIcons.Copy, "Copy to…") { menu = false; dialog = "copy" }
                            MenuItem(AppIcons.Move, "Move to…") { menu = false; dialog = "move" }
                            MenuItem(AppIcons.Play, "Slideshow from here") { menu = false; dialog = "slideshow" }
                            MenuItem(AppIcons.Trash, "Delete", c.danger) { menu = false; dialog = "delete" }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = bars,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier.fillMaxWidth().background(c.barBg).navigationBarsPadding().height(76.dp),
            ) {
                BottomAction(AppIcons.Play, "Slideshow", c.accent, Modifier.weight(1f)) { dialog = "slideshow" }
                BottomAction(AppIcons.Share, "Share", c.barText, Modifier.weight(1f)) { shareImages(ctx, listOf(current)) }
                BottomAction(AppIcons.Crop, "Edit", c.barText, Modifier.weight(1f)) { vm.push(Screen.Editor(current.path)) }
                BottomAction(AppIcons.Move, "Move", c.barText, Modifier.weight(1f)) { dialog = "move" }
                BottomAction(AppIcons.Trash, "Delete", c.danger, Modifier.weight(1f)) { dialog = "delete" }
            }
        }
    }

    val close = { dialog = "" }
    when (dialog) {
        "info" -> ImageInfoSheet(current, close)
        "rename" -> RenameDialog("Rename image", current.name.substringBeforeLast('.'), current.name.substringAfterLast('.', ""), close) {
            vm.renameImage(current, it); close()
        }
        "wallpaper" -> WallpaperDialog(close) { which -> close(); vm.setWallpaper(current.path, which) }
        "move" -> MoveDialog(vm, 1, folder, close) { target -> close(); vm.move(listOf(current), target) }
        "copy" -> MoveDialog(vm, 1, folder, close, action = "Copy") { target -> close(); vm.copy(listOf(current), target) }
        "delete" -> DeleteDialog(1, close) { close(); vm.delete(listOf(current)) }
        "slideshow" -> SlideshowSheet(vm, canStartHere = true, onDismiss = close) { fromHere ->
            close(); vm.push(Screen.Slideshow(folderPath, if (fromHere) page else 0))
        }
    }
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color = LocalAppColors.current.text, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = color, fontSize = 15.sp) },
        leadingIcon = { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

// =================== Slideshow ===================

@Composable
fun SlideshowScreen(vm: AppViewModel, folderPath: String, start: Int) {
    val c = LocalAppColors.current
    val ctx = LocalContext.current
    val s = vm.settings.state
    val (_, images) = rememberFolderImages(vm, folderPath)
    val list = images
    if (list == null) { Box(Modifier.fillMaxSize().background(c.viewerBg)); return }
    if (list.isEmpty()) { LaunchedEffect(Unit) { vm.back() }; return }

    val order = remember(list.size) {
        val n = list.size
        val st = start.coerceIn(0, n - 1)
        // From this image to the end, then wrap to the start: whole folder exactly once.
        val seq = (st until n).toList() + (0 until st).toList()
        if (s.slideShuffle) seq.shuffled() else seq
    }
    var pos by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var secs by remember { mutableIntStateOf(s.slideSecs) }
    val dm = ctx.resources.displayMetrics
    val w = dm.widthPixels
    val h = dm.heightPixels

    fun request(img: ImageEntity) = ImageRequest.Builder(ctx).data(File(img.path)).size(w, h).build()

    SystemBars(false)
    val view = LocalView.current
    DisposableEffect(s.slideKeepOn) {
        view.keepScreenOn = s.slideKeepOn
        onDispose { view.keepScreenOn = false }
    }

    // Advance
    LaunchedEffect(pos, paused, secs) {
        if (paused) return@LaunchedEffect
        delay(secs * 1000L)
        if (pos + 1 < order.size) pos++
        else if (s.slideLoop) pos = 0
        else vm.back()
    }
    // Preload the next image so the change is instant
    LaunchedEffect(pos) {
        val next = order.getOrNull(pos + 1) ?: if (s.slideLoop) order.firstOrNull() else null
        val img = next?.let { list.getOrNull(it) } ?: return@LaunchedEffect
        SingletonImageLoader.get(ctx).enqueue(request(img))
    }
    // Hide controls after 3 s
    LaunchedEffect(controls, paused) {
        if (controls && !paused) { delay(3000); controls = false }
    }

    Box(
        Modifier.fillMaxSize().background(c.viewerBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls },
    ) {
        AnimatedContent(
            targetState = pos,
            transitionSpec = {
                when (s.slideTransition) {
                    "Slide" -> slideInHorizontally(tween(450)) { it } togetherWith slideOutHorizontally(tween(450)) { -it }
                    "None" -> EnterTransition.None togetherWith ExitTransition.None
                    else -> fadeIn(tween(600)) togetherWith fadeOut(tween(600))
                }
            },
            label = "slide",
            modifier = Modifier.fillMaxSize(),
        ) { p ->
            val img = list.getOrNull(order.getOrElse(p) { 0 })
            if (img != null) {
                AsyncImage(
                    model = request(img), contentDescription = img.name,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                )
            }
        }

        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(tween(200)), exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize(),
        ) {
          Box(Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                LinearProgressIndicator(
                    progress = { (pos + 1).toFloat() / order.size },
                    color = c.accent, trackColor = c.barText.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        "${fmtNum(pos + 1)} of ${fmtNum(order.size)}" + if (s.slideLoop) " · loop" else " · plays folder once",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.barText,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.barBg.copy(alpha = 0.8f)).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp)
                    .shadow(8.dp, RoundedCornerShape(32.dp)).clip(RoundedCornerShape(32.dp)).background(c.barBg).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(AppIcons.Back, "Previous", c.barText) { if (pos > 0) pos-- ; controls = true }
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(c.accent).clickable { paused = !paused; controls = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (paused) AppIcons.PlayFilled else AppIcons.Pause, if (paused) "Play" else "Pause", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                IconBtn(AppIcons.Next, "Next", c.barText) { if (pos + 1 < order.size) pos++ else if (s.slideLoop) pos = 0; controls = true }
                Box(Modifier.width(1.dp).height(28.dp).background(c.divider))
                Text(
                    "$secs s", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.barText,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                        val steps = listOf(1, 2, 3, 5, 8, 10, 15, 20, 30, 45, 60, 90, 120)
                        secs = steps.firstOrNull { it > secs } ?: steps.first()
                        controls = true
                    }.padding(horizontal = 12.dp, vertical = 12.dp),
                )
                IconBtn(AppIcons.Close, "Stop slideshow", c.barText) { vm.back() }
            }
          }
        }
    }
}


// =================== Image opened from another app (outside the main folders) ===================

@Composable
fun ExternalViewer(vm: AppViewModel, uri: android.net.Uri, onClose: () -> Unit) {
    val c = LocalAppColors.current
    var bars by remember { mutableStateOf(true) }
    androidx.activity.compose.BackHandler { onClose() }
    SystemBars(bars)
    Box(Modifier.fillMaxSize().background(c.viewerBg)) {
        ZoomableAsyncImage(
            model = uri, contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            onClick = { bars = !bars },
        )
        AnimatedVisibility(
            visible = bars, enter = fadeIn(tween(180)), exit = fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(Modifier.fillMaxWidth().background(c.barBg).statusBarsPadding()) {
                TopBar(
                    title = uri.lastPathSegment?.substringAfterLast('/') ?: "Image",
                    subtitle = "Not in your main folders",
                    onBack = onClose, bg = c.barBg, fg = c.barText,
                )
            }
        }
    }
}
