package com.eazaz.fastgallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.eazaz.fastgallery.AppViewModel
import com.eazaz.fastgallery.Screen
import com.eazaz.fastgallery.data.FolderEntity
import com.eazaz.fastgallery.data.ImageEntity
import com.eazaz.fastgallery.data.Sorts
import com.eazaz.fastgallery.data.Thumb
import com.eazaz.fastgallery.data.path
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.File
import kotlin.math.max
import kotlin.math.min

// =================== Home: folders grid ===================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val s = vm.settings.state
    // Kept in memory by the view model, so it is ready instantly when you come back.
    val folders by vm.folders.collectAsState()
    val progress by vm.scanner.progress.collectAsState()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    BackHandler(searching) { searching = false; query = "" }
    val sorted = remember(folders, s.folderSortKey, s.folderSortAsc, query) {
        val q = query.trim().lowercase()
        val all = folders ?: emptyList()
        val filtered = if (q.isEmpty()) all else all.filter { it.name.lowercase().contains(q) }
        Sorts.folders(filtered, s.folderSortKey, s.folderSortAsc)
    }
    // Only show the scan bar if a scan takes longer than a second (quick refreshes stay invisible).
    var showBar by remember { mutableStateOf(false) }
    LaunchedEffect(progress.running) {
        if (progress.running) { delay(1000); showBar = true } else showBar = false
    }
    val pinned = remember(sorted) { sorted.filter { it.pinned }.sortedBy { it.pinOrder } }
    val others = remember(sorted) { sorted.filter { !it.pinned } }
    val totalImages = remember(folders) { folders?.sumOf { it.imageCount } ?: 0 }
    var menuFor by remember { mutableStateOf<FolderEntity?>(null) }
    var showSort by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()

    // Flat index list for the fast scroller label
    val entries: List<FolderEntity?> = remember(pinned, others) {
        buildList<FolderEntity?> {
            if (pinned.isNotEmpty()) { add(null); addAll(pinned) }
            add(null); addAll(others)
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        if (searching) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Row(Modifier.fillMaxWidth().height(60.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(AppIcons.Back, "Close search") { searching = false; query = "" }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search folders") },
                    leadingIcon = { Icon(AppIcons.Search, null, tint = c.subtext) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
        } else {
            TopBar("Gallery", large = true) {
                IconBtn(AppIcons.Search, "Search folders") { searching = true }
                IconBtn(AppIcons.Sort, "Sort") { showSort = true }
                IconBtn(AppIcons.More, "Settings") { vm.push(Screen.Settings) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${fmtNum(folders?.size ?: 0)} folders · ${fmtNum(totalImages)} images",
                fontSize = 13.sp, color = c.subtext, modifier = Modifier.weight(1f),
            )
            Pill(sortLabel(s.folderSortKey, s.folderSortAsc)) { showSort = true }
        }
        if (progress.running && showBar) {
            LinearProgressIndicator(
                progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                color = c.accent, trackColor = c.divider, modifier = Modifier.fillMaxWidth().height(3.dp),
            )
        }
        Box(Modifier.weight(1f)) {
            if (searching && query.isNotBlank() && sorted.isEmpty()) {
                Text("No folder matches \"$query\"", color = c.subtext, modifier = Modifier.align(Alignment.Center))
            } else if (folders != null && folders!!.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (progress.running) "Scanning your main folders…" else "No images found in your main folders",
                        fontSize = 16.sp, textAlign = TextAlign.Center,
                    )
                    if (!progress.running) {
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.push(Screen.Roots) }) { Text("Choose main folders") }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(s.folderCols),
                    state = grid,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (pinned.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h_pinned") { SectionLabel("Pinned", icon = AppIcons.Pin) }
                        items(pinned, key = { "p:" + it.path }) { f ->
                            FolderTile(f, onClick = { vm.push(Screen.Folder(f.path)) }, onLongClick = { menuFor = f })
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }, key = "h_all") { SectionLabel("All folders") }
                    items(others, key = { it.path }) { f ->
                        FolderTile(f, onClick = { vm.push(Screen.Folder(f.path)) }, onLongClick = { menuFor = f })
                    }
                }
                FastScroller(grid, entries.size) { idx ->
                    val f = entries.getOrNull(idx) ?: entries.getOrNull(idx + 1)
                    if (f == null) "" else when (s.folderSortKey) {
                        "name" -> f.name.take(1).uppercase()
                        "size" -> fmtSize(f.totalSize)
                        "modified" -> fmtMonth(f.newestModified)
                        else -> fmtMonth(f.newestTaken)
                    }
                }
            }
        }
    }

    menuFor?.let { f -> FolderMenuHost(vm, f) { menuFor = null } }
    if (showSort) SortSheet(vm, null, "folders") { showSort = false }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderTile(f: FolderEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(c.placeholder)) {
            if (f.coverPath != null) {
                AsyncImage(
                    model = Thumb(f.coverPath, f.coverMtime), contentDescription = f.name,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
            if (f.pinned) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.PinFilled, "Pinned", tint = c.accent, modifier = Modifier.size(13.dp)) }
            }
        }
        Text(
            f.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(fmtNum(f.imageCount), fontSize = 12.sp, color = c.subtext)
    }
}

// =================== Inside a folder ===================

@Composable
fun FolderScreen(vm: AppViewModel, path: String) {
    val c = LocalAppColors.current
    val s = vm.settings.state
    val (folder, images) = rememberFolderImages(vm, path)
    val list = images ?: emptyList()
    val grid = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current

    var selected by remember { mutableStateOf(setOf<Long>()) }
    val selecting = selected.isNotEmpty()
    var showSort by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showSlideshow by remember { mutableStateOf(false) }
    var showCopy by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val selectedImages = { list.filter { it.id in selected } }

    BackHandler(selecting) { selected = emptySet() }

    // Coming back from the viewer: make sure the last viewed image is on screen.
    LaunchedEffect(path, images != null) {
        if (images == null) return@LaunchedEffect
        val idx = vm.takeLastViewed(path) ?: return@LaunchedEffect
        snapshotFlow { grid.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
        val vis = grid.layoutInfo.visibleItemsInfo
        val fully = vis.drop(s.imageCols).dropLast(s.imageCols)
        if (fully.none { it.index == idx }) grid.scrollToItem(max(0, idx - s.imageCols))
    }

    val sortKey = (if (s.rememberPerFolder) folder?.sortKey else null) ?: s.imageSortKey
    val sortAsc = (if (s.rememberPerFolder) folder?.sortAsc else null) ?: s.imageSortAsc

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Crossfade(targetState = selecting, animationSpec = tween(180), label = "bar") { selMode ->
        if (selMode) {
          Column(Modifier.fillMaxWidth().background(c.chipBg)) {
            Row(
                Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBtn(AppIcons.Close, "Cancel selection") { selected = emptySet() }
                Text("${fmtNum(selected.size)} selected", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                        selected = if (selected.size == list.size) emptySet() else list.map { it.id }.toSet()
                    }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.SelectAll, null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(if (selected.size == list.size) "Select none" else "Select all", color = c.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
            Text(
                "Tip: long-press, then drag to select a range", fontSize = 12.sp, color = c.subtext,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
            )
          }
        } else {
          Column(Modifier.fillMaxWidth()) {
            TopBar(
                title = folder?.name ?: File(path).name,
                subtitle = "${fmtNum(list.size)} images",
                onBack = { vm.back() },
            ) {
                IconBtn(AppIcons.Play, "Slideshow", c.accent) { if (list.isNotEmpty()) showSlideshow = true }
                IconBtn(AppIcons.Sort, "Sort") { showSort = true }
                IconBtn(AppIcons.More, "More options") { if (folder != null) showMenu = true }
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(sortLabel(sortKey, sortAsc)) { showSort = true }
                Spacer(Modifier.size(10.dp))
                Text("Pinch to change columns", fontSize = 12.sp, color = c.subtext)
            }
          }
        }
        }

        Box(Modifier.weight(1f)) {
            val currentList by rememberUpdatedState(list)
            var longPressed by remember { mutableStateOf(false) }
            var dragged by remember { mutableStateOf(false) }
            var anchor by remember { mutableIntStateOf(-1) }
            var base by remember { mutableStateOf(setOf<Long>()) }
            var autoScroll by remember { mutableFloatStateOf(0f) }

            LaunchedEffect(autoScroll) {
                if (autoScroll != 0f) while (true) {
                    grid.scrollBy(autoScroll)
                    delay(16)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(s.imageCols),
                state = grid,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
                    .pinchToChangeColumns({ vm.settings.state.imageCols }, 2..8) { n ->
                        vm.settings.update { it.copy(imageCols = n) }
                    }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { off ->
                                grid.indexAt(off)?.let { i ->
                                    val l = currentList
                                    if (i < l.size) {
                                        longPressed = true
                                        dragged = false
                                        anchor = i
                                        base = selected
                                        selected = base + l[i].id
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (dragged) longPressed = false
                                anchor = -1; autoScroll = 0f
                            },
                            onDragCancel = {
                                if (dragged) longPressed = false
                                anchor = -1; autoScroll = 0f
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                dragged = true
                                val l = currentList
                                if (anchor >= 0) {
                                    grid.indexAt(change.position)?.let { i ->
                                        if (i < l.size) {
                                            val from = min(anchor, i)
                                            val to = max(anchor, i)
                                            selected = base + (from..to).map { l[it].id }
                                        }
                                    }
                                }
                                val h = size.height.toFloat()
                                val y = change.position.y
                                val edge = 120f
                                autoScroll = when {
                                    y < edge -> -((edge - y) / edge) * 30f
                                    y > h - edge -> ((y - (h - edge)) / edge) * 30f
                                    else -> 0f
                                }
                            },
                        )
                    },
            ) {
                itemsIndexed(list, key = { _, it -> it.id }) { i, img ->
                    ImageTile(img, img.id in selected, selecting) {
                        when {
                            longPressed -> longPressed = false
                            selecting -> selected = if (img.id in selected) selected - img.id else selected + img.id
                            else -> vm.push(Screen.Viewer(path, i))
                        }
                    }
                }
            }
            FastScroller(grid, list.size) { idx ->
                val img = list.getOrNull(idx)
                if (img == null) "" else when (sortKey) {
                    "name" -> img.name.take(1).uppercase()
                    "size" -> fmtSize(img.size)
                    "modified" -> fmtMonth(img.modified)
                    else -> fmtMonth(img.taken)
                }
            }
        }

        AnimatedVisibility(
            visible = selecting,
            enter = expandVertically(tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
        ) {
            Row(Modifier.fillMaxWidth().height(72.dp).background(c.appBg)) {
                BottomAction(AppIcons.Share, "Share", c.text, Modifier.weight(1f)) { shareImages(ctx, selectedImages()) }
                BottomAction(AppIcons.Copy, "Copy to", c.text, Modifier.weight(1f)) { showCopy = true }
                BottomAction(AppIcons.Move, "Move to", c.text, Modifier.weight(1f)) { showMove = true }
                BottomAction(AppIcons.Trash, "Delete", c.danger, Modifier.weight(1f)) { showDelete = true }
            }
        }
    }

    if (showSort) SortSheet(vm, folder, "images") { showSort = false }
    if (showCopy) MoveDialog(vm, selected.size, folder, { showCopy = false }, action = "Copy") { target ->
        showCopy = false
        val l = selectedImages()
        vm.copy(l, target) { selected = emptySet() }
    }
    if (showMenu && folder != null) FolderMenuHost(vm, folder) { showMenu = false }
    if (showSlideshow) SlideshowSheet(vm, canStartHere = false, onDismiss = { showSlideshow = false }) { _ ->
        showSlideshow = false
        vm.push(Screen.Slideshow(path, 0))
    }
    if (showDelete) DeleteDialog(selected.size, { showDelete = false }) {
        showDelete = false
        val l = selectedImages()
        vm.delete(l) { selected = emptySet() }
    }
    if (showMove) MoveDialog(vm, selected.size, folder, { showMove = false }) { target ->
        showMove = false
        val l = selectedImages()
        vm.move(l, target) { selected = emptySet() }
    }
}

@Composable
fun ImageTile(img: ImageEntity, isSelected: Boolean, selecting: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val pad by animateDpAsState(if (isSelected) 8.dp else 0.dp, label = "pad")
    Box(Modifier.aspectRatio(1f).background(if (isSelected) c.chipBg else c.placeholder).clickable(onClick = onClick)) {
        AsyncImage(
            model = Thumb(img.path, img.modified), contentDescription = img.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(pad).clip(if (isSelected) RoundedCornerShape(8.dp) else RectangleShape),
        )
        if (selecting) {
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape)
                    .background(if (isSelected) c.accent else Color.Black.copy(alpha = 0.15f))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(AppIcons.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
fun BottomAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxSize().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.padding(top = 4.dp))
    }
}
