package com.eazaz.fastgallery.ui

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.eazaz.fastgallery.AppViewModel
import com.eazaz.fastgallery.Screen
import com.eazaz.fastgallery.data.FolderEntity
import com.eazaz.fastgallery.data.ImageEntity
import com.eazaz.fastgallery.data.Sorts
import com.eazaz.fastgallery.data.Thumb
import com.eazaz.fastgallery.data.ext
import com.eazaz.fastgallery.data.naturalCompare
import com.eazaz.fastgallery.data.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Folder row + its images, sorted the way the user chose (per folder or global). */
@Composable
fun rememberFolderImages(vm: AppViewModel, path: String): Pair<FolderEntity?, List<ImageEntity>?> {
    val folder by remember(path) { vm.dao.folderFlow(path).distinctUntilChanged() }.collectAsState(initial = null)
    val raw by remember(path) { vm.dao.imagesFlow(path).distinctUntilChanged() }.collectAsState(initial = null)
    val s = vm.settings.state
    val key = (if (s.rememberPerFolder) folder?.sortKey else null) ?: s.imageSortKey
    val asc = (if (s.rememberPerFolder) folder?.sortAsc else null) ?: s.imageSortAsc
    // Start from the last list we showed for this folder, so the screen is never blank.
    val sorted by produceState<List<ImageEntity>?>(vm.sortedCache[path], raw, key, asc) {
        val r = raw ?: return@produceState
        val result = withContext(Dispatchers.Default) { Sorts.images(r, key, asc) }
        vm.sortedCache[path] = result
        value = result
    }
    return folder to sorted
}

// ---------------- Sort ----------------

@Composable
fun SortSheet(vm: AppViewModel, folder: FolderEntity?, initialScope: String, onDismiss: () -> Unit) {
    val s = vm.settings.state
    var scope by remember { mutableStateOf(initialScope) }
    val folderKey0 = s.folderSortKey
    val imageKey0 = (if (s.rememberPerFolder) folder?.sortKey else null) ?: s.imageSortKey
    val imageAsc0 = (if (s.rememberPerFolder) folder?.sortAsc else null) ?: s.imageSortAsc
    var fKey by remember { mutableStateOf(folderKey0) }
    var fAsc by remember { mutableStateOf(s.folderSortAsc) }
    var iKey by remember { mutableStateOf(imageKey0) }
    var iAsc by remember { mutableStateOf(imageAsc0) }
    var apply by remember { mutableStateOf(if (folder?.sortKey != null) "this" else "all") }
    val c = LocalAppColors.current

    Sheet(onDismiss) {
        Text("Sort", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Segmented(listOf("folders" to "Folders", "images" to "Images"), scope) { scope = it }
        Spacer(Modifier.height(8.dp))
        val key = if (scope == "folders") fKey else iKey
        val asc = if (scope == "folders") fAsc else iAsc
        SectionLabel("Sort by")
        listOf(
            "name" to "Name",
            "modified" to "Date modified",
            "taken" to (if (scope == "folders") "Date taken (newest image)" else "Date taken"),
            "size" to (if (scope == "folders") "Size (total)" else "Size"),
        ).forEach { (id, label) ->
            Row(
                Modifier.fillMaxWidth().height(48.dp).clickable { if (scope == "folders") fKey = id else iKey = id },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(20.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (key == id) c.accent else c.subtext.copy(alpha = 0.5f))
                        .padding(if (key == id) 6.dp else 2.dp)
                        .clip(RoundedCornerShape(10.dp)).background(c.appBg),
                )
                Spacer(Modifier.width(14.dp))
                Text(label, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        val ascLabel = if (key == "name") "Ascending (A→Z)" else "Ascending (oldest / smallest)"
        val descLabel = if (key == "name") "Descending (Z→A)" else "Descending (newest / largest)"
        ChoiceRow(listOf("asc" to ascLabel, "desc" to descLabel), if (asc) "asc" else "desc") {
            if (scope == "folders") fAsc = it == "asc" else iAsc = it == "asc"
        }
        if (scope == "images" && folder != null && s.rememberPerFolder) {
            SectionLabel("Apply to")
            ChoiceRow(listOf("this" to "This folder only", "all" to "All folders (global)"), apply) { apply = it }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (scope == "folders") {
                    vm.settings.update { it.copy(folderSortKey = fKey, folderSortAsc = fAsc) }
                } else if (folder != null && s.rememberPerFolder && apply == "this") {
                    vm.setFolderSort(folder.path, iKey, iAsc)
                } else {
                    vm.settings.update { it.copy(imageSortKey = iKey, imageSortAsc = iAsc) }
                    vm.clearFolderSorts()
                }
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Apply", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

// ---------------- Folder long-press menu ----------------

@Composable
fun FolderMenuHost(vm: AppViewModel, folder: FolderEntity, onDismiss: () -> Unit) {
    var mode by remember(folder.path) { mutableStateOf("menu") }
    val c = LocalAppColors.current
    when (mode) {
        "menu" -> Sheet(onDismiss) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(c.placeholder)) {
                    if (folder.coverPath != null) AsyncImage(
                        Thumb(folder.coverPath, folder.coverMtime), null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(folder.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${fmtNum(folder.imageCount)} images · ${fmtSize(folder.totalSize)}", fontSize = 13.sp, color = c.subtext)
                }
            }
            SheetAction(if (folder.pinned) AppIcons.PinFilled else AppIcons.Pin, if (folder.pinned) "Unpin" else "Pin to top", c.accent) {
                vm.togglePin(folder); onDismiss()
            }
            SheetAction(AppIcons.Edit, "Rename folder") { mode = "rename" }
            SheetAction(AppIcons.Play, "Slideshow this folder") { mode = "slideshow" }
            SheetAction(AppIcons.Sort, "Sort this folder…") { mode = "sort" }
            SheetAction(AppIcons.Info, "Folder info") { mode = "info" }
            SheetAction(AppIcons.Close, "Exclude from gallery", c.subtext) { vm.excludeFolder(folder.path); onDismiss() }
        }
        "rename" -> RenameDialog("Rename folder", folder.name, "", onDismiss) { vm.renameFolder(folder, it); onDismiss() }
        "slideshow" -> SlideshowSheet(vm, canStartHere = false, onDismiss = onDismiss) { _ ->
            onDismiss(); vm.push(Screen.Slideshow(folder.path, 0))
        }
        "sort" -> SortSheet(vm, folder, "images", onDismiss)
        "info" -> InfoSheet(
            "Folder info",
            listOf(
                "Name" to folder.name,
                "Images" to fmtNum(folder.imageCount),
                "Total size" to fmtSize(folder.totalSize),
                "Newest image" to fmtDate(folder.newestTaken),
                "Path" to folder.path,
            ),
            onDismiss,
        )
    }
}

// ---------------- Info ----------------

@Composable
fun InfoSheet(title: String, rows: List<Pair<String, String>>, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    Sheet(onDismiss) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        rows.forEach { (k, v) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(k, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.subtext)
                Text(v, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun ImageInfoSheet(img: ImageEntity, onDismiss: () -> Unit) {
    val res by produceState("…", img.path) {
        value = withContext(Dispatchers.IO) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(img.path, o)
            if (o.outWidth > 0) "${o.outWidth} × ${o.outHeight}" else "—"
        }
    }
    InfoSheet(
        "Info",
        listOf(
            "Name" to img.name,
            "Size" to fmtSize(img.size),
            "Resolution" to res,
            "Date taken" to fmtDate(img.taken),
            "Date modified" to fmtDate(img.modified),
            "Format" to img.ext.uppercase(),
            "Path" to img.path,
        ),
        onDismiss,
    )
}

// ---------------- Rename / delete / wallpaper ----------------

@Composable
fun RenameDialog(title: String, initial: String, ext: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val c = LocalAppColors.current
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val suffixFn: (@Composable () -> Unit)? = if (ext.isNotEmpty()) {
        { Text(".$ext", color = c.subtext) }
    } else null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.appBg,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = value, onValueChange = { value = it }, singleLine = true,
                    label = { Text("New name") },
                    suffix = suffixFn,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, focusedLabelColor = c.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (ext.isNotEmpty()) Text("The extension is kept automatically.", fontSize = 12.sp, color = c.subtext, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(value.text) }, enabled = value.text.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.text) } },
    )
}

@Composable
fun DeleteDialog(count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.appBg,
        icon = { Icon(AppIcons.Trash, null, tint = c.danger) },
        title = { Text(if (count == 1) "Delete this image permanently?" else "Delete $count images permanently?", fontWeight = FontWeight.Bold) },
        text = { Text("They will be removed from your phone. This can't be undone.") },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = c.danger)) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.text) } },
    )
}

@Composable
fun WallpaperDialog(onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val c = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.appBg,
        title = { Text("Set as wallpaper", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                SheetAction(AppIcons.Image, "Home screen") { onPick(WallpaperManager.FLAG_SYSTEM) }
                SheetAction(AppIcons.Image, "Lock screen") { onPick(WallpaperManager.FLAG_LOCK) }
                SheetAction(AppIcons.Image, "Both") { onPick(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.text) } },
    )
}

// ---------------- Move to folder ----------------

@Composable
fun MoveDialog(vm: AppViewModel, count: Int, current: FolderEntity?, onDismiss: () -> Unit, action: String = "Move", onPick: (String) -> Unit) {
    val c = LocalAppColors.current
    val all by remember { vm.dao.foldersFlow() }.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var newFolder by remember { mutableStateOf(false) }
    val list = remember(all, query) {
        val q = query.trim().lowercase()
        all.filter { it.path != current?.path && (q.isEmpty() || it.name.lowercase().contains(q)) }
            .sortedWith { a, b -> if (a.pinned != b.pinned) (if (a.pinned) -1 else 1) else naturalCompare(a.name, b.name) }
    }
    val newParent = current?.root ?: vm.settings.state.roots.firstOrNull() ?: ""

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.appBg).systemBarsPadding()) {
            TopBar("$action $count ${if (count == 1) "image" else "images"} to…", onBack = onDismiss)
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(AppIcons.Search, null, tint = c.subtext) },
                placeholder = { Text("Find folder") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().height(60.dp).clickable { newFolder = true }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(c.chipBg), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Plus, null, tint = c.accent)
                }
                Spacer(Modifier.width(14.dp))
                Text("New folder", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(list, key = { it.path }) { f ->
                    Row(
                        Modifier.fillMaxWidth().height(62.dp).clickable { onPick(f.path) }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(c.placeholder)) {
                            if (f.coverPath != null) AsyncImage(
                                Thumb(f.coverPath, f.coverMtime), null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${fmtNum(f.imageCount)} images", fontSize = 12.sp, color = c.subtext)
                        }
                        if (f.pinned) Icon(AppIcons.PinFilled, null, tint = c.accent, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Text(
                "Same file name already there? It's renamed automatically, e.g. img (1).webp",
                fontSize = 12.sp, color = c.subtext, modifier = Modifier.padding(16.dp),
            )
        }
    }
    if (newFolder) {
        RenameDialog("New folder", "", "", { newFolder = false }) { name ->
            newFolder = false
            vm.createFolder(newParent, name) { p -> if (p != null) onPick(p) }
        }
    }
}

// ---------------- Slideshow settings ----------------

/** onStart(fromHere). With onStart == null it only edits the saved defaults. */
@Composable
fun SlideshowSheet(vm: AppViewModel, canStartHere: Boolean, onDismiss: () -> Unit, onStart: ((Boolean) -> Unit)?) {
    val s = vm.settings.state
    val c = LocalAppColors.current
    var from by remember { mutableStateOf(if (canStartHere) "here" else "start") }
    var secs by remember { mutableFloatStateOf(s.slideSecs.toFloat()) }
    var tr by remember { mutableStateOf(s.slideTransition) }
    var loop by remember { mutableStateOf(s.slideLoop) }
    var shuffle by remember { mutableStateOf(s.slideShuffle) }
    var keepOn by remember { mutableStateOf(s.slideKeepOn) }

    fun save() = vm.settings.update {
        it.copy(slideSecs = secs.roundToInt(), slideTransition = tr, slideLoop = loop, slideShuffle = shuffle, slideKeepOn = keepOn)
    }

    Sheet({ if (onStart == null) save(); onDismiss() }) {
        Text(if (onStart == null) "Slideshow defaults" else "Slideshow", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (onStart != null && canStartHere) {
            SectionLabel("Start from")
            ChoiceRow(listOf("here" to "This image", "start" to "First image"), from) { from = it }
            Text(
                if (from == "here") "Plays from this image to the end, wraps to the start, and stops when the whole folder has been shown once."
                else "Plays the whole folder once from the first image.",
                fontSize = 12.sp, color = c.subtext, modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Time per slide", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.subtext, modifier = Modifier.weight(1f))
            Text("${secs.roundToInt()} s", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.accent)
        }
        Slider(
            value = secs, onValueChange = { secs = it }, valueRange = 1f..120f, steps = 118,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1 s", fontSize = 12.sp, color = c.subtext); Text("120 s", fontSize = 12.sp, color = c.subtext)
        }
        SectionLabel("Transition")
        Segmented(listOf("Fade" to "Fade", "Slide" to "Slide", "None" to "None"), tr) { tr = it }
        Spacer(Modifier.height(8.dp))
        SwitchRow("Loop (repeat forever)", loop) { loop = it }
        SwitchRow("Shuffle", shuffle) { shuffle = it }
        SwitchRow("Keep screen on", keepOn) { keepOn = it }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                save()
                if (onStart != null) onStart(from == "here") else onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (onStart != null) {
                Icon(AppIcons.PlayFilled, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start slideshow", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            } else Text("Save", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Share images to other apps (WhatsApp, Telegram, ...). */
fun shareImages(ctx: android.content.Context, list: List<ImageEntity>) {
    if (list.isEmpty()) return
    val max = 300
    val items = list.take(max)
    val auth = ctx.packageName + ".files"
    val uris = ArrayList<android.net.Uri>()
    for (img in items) {
        try { uris += androidx.core.content.FileProvider.getUriForFile(ctx, auth, File(img.path)) } catch (_: Exception) {}
    }
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) {
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = when (items[0].ext) { "webp" -> "image/webp"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; else -> "image/*" }
            putExtra(android.content.Intent.EXTRA_STREAM, uris[0])
        }
    } else {
        android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
        }
    }
    val clip = android.content.ClipData.newRawUri("images", uris[0])
    for (i in 1 until uris.size) clip.addItem(android.content.ClipData.Item(uris[i]))
    intent.clipData = clip
    intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(android.content.Intent.createChooser(intent, "Share"))
    } catch (_: Exception) {
    }
    if (list.size > max) android.widget.Toast.makeText(ctx, "Sharing the first $max images", android.widget.Toast.LENGTH_SHORT).show()
}
