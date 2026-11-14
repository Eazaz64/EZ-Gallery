package com.eazaz.fastgallery.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazaz.fastgallery.AppViewModel
import com.eazaz.fastgallery.Screen
import com.eazaz.fastgallery.data.AppSettings
import com.eazaz.fastgallery.data.shortPath
import com.eazaz.fastgallery.data.FolderSuggestion
import com.eazaz.fastgallery.data.findImageFolders
import com.eazaz.fastgallery.data.isCovered
import com.eazaz.fastgallery.data.MEDIA_ROOT
import androidx.compose.runtime.produceState
import com.eazaz.fastgallery.data.treeUriToPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// =================== Permission ===================

@Composable
fun PermissionScreen() {
    val ctx = LocalContext.current
    val c = LocalAppColors.current
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(c.chipBg), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Folder, null, tint = c.accent, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Allow access to your files", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Fast Gallery reads your image folders directly so it can open 400,000+ images quickly, and move, rename or delete them. " +
                "Turn on \"Allow access to manage all files\" on the next screen, then come back.",
            fontSize = 15.sp, color = c.subtext,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))
                try { ctx.startActivity(i) } catch (_: Exception) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Open settings", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

// =================== Main folders ===================

@Composable
fun RootsScreen(vm: AppViewModel, firstRun: Boolean) {
    val c = LocalAppColors.current
    val s = vm.settings.state
    val folders by remember { vm.dao.foldersFlow() }.collectAsState(initial = emptyList())
    val ctx = LocalContext.current
    val suggestions by produceState<List<FolderSuggestion>?>(null) {
        value = withContext(Dispatchers.IO) { findImageFolders(ctx) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val p = uri?.let { treeUriToPath(it) } ?: return@rememberLauncherForActivityResult
        vm.addRoot(p)
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopBar("Folders", onBack = if (firstRun) null else ({ vm.back() }), large = firstRun)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // --- Automatic (like other galleries)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.field).padding(horizontal = 14.dp, vertical = 6.dp)) {
                SwitchRow(
                    "Find images automatically",
                    s.autoFind,
                    "Shows every folder Android knows has images (camera, screenshots, WhatsApp, downloads…), like other galleries. " +
                        "App junk marked with .nomedia (stickers, thumbnails) is skipped.",
                ) { vm.setAutoFind(it) }
                if (s.autoFind) {
                    val auto = folders.filter { it.root == MEDIA_ROOT }
                    Text(
                        "${fmtNum(auto.size)} folders · ${fmtNum(auto.sumOf { it.imageCount })} images found automatically",
                        fontSize = 12.sp, color = c.subtext, modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            // --- Extra folders
            SectionLabel("Extra folders", Modifier.padding(top = 16.dp))
            Text(
                if (s.autoFind) "Only needed for folders Android doesn't list (for example ones with a .nomedia file). Scanned with all subfolders."
                else "Only these folders and everything inside them are scanned.",
                fontSize = 12.sp, color = c.subtext, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            s.roots.forEach { root ->
                val inRoot = folders.filter { it.root == root }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp))
                        .border(1.dp, c.divider, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(c.chipBg), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Folder, null, tint = c.accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(shortPath(root).ifEmpty { "Whole internal storage" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (inRoot.isEmpty()) "Not scanned yet" else "${fmtNum(inRoot.size)} folders · ${fmtNum(inRoot.sumOf { it.imageCount })} images",
                            fontSize = 12.sp, color = c.subtext,
                        )
                    }
                    IconBtn(AppIcons.Close, "Remove folder", c.subtext) { vm.removeRoot(root) }
                }
            }
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                    .border(2.dp, c.subtext.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .clickable { picker.launch(null) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Plus, null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Add extra folder", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
                }
            }
            if (s.roots.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Include subfolders of extra folders up to", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Segmented(listOf("1" to "1 level", "3" to "3 levels", "-1" to "All levels"), s.depth.toString()) { v ->
                        vm.settings.update { it.copy(depth = v.toInt()) }
                    }
                }
            }

            // --- Suggestions (only useful when automatic finding is off)
            if (!s.autoFind) {
                SectionLabel("Found on this phone", Modifier.padding(top = 16.dp))
                val sug = suggestions
                if (sug == null) {
                    Text("Looking for image folders…", fontSize = 13.sp, color = c.subtext, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    sug.forEach { f ->
                        val covered = isCovered(f.path, s.roots)
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${shortPath(f.path).ifEmpty { "/" }} · about ${fmtNum(f.count)} images", fontSize = 12.sp, color = c.subtext, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (covered) {
                                Text("Included", fontSize = 13.sp, color = c.subtext, modifier = Modifier.padding(horizontal = 12.dp))
                            } else {
                                Text(
                                    "Add", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.accent,
                                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(c.chipBg)
                                        .clickable { vm.addRoot(f.path) }.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            // --- Excluded
            SectionLabel("Excluded folders", Modifier.padding(top = 16.dp))
            if (s.excludes.isEmpty()) {
                Text(
                    "None. Long-press a folder on the home screen → Exclude from gallery.",
                    fontSize = 12.sp, color = c.subtext, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
            } else {
                s.excludes.forEach { ex ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ex.substringAfterLast('/'), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(shortPath(ex), fontSize = 12.sp, color = c.subtext, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            "Show again", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.unexclude(ex) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = {
                vm.startScan()
                vm.resetTo(Screen.Home, Screen.Scan)
            },
            enabled = s.autoFind || s.roots.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Scan now", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

// =================== Scan progress ===================

@Composable
fun ScanScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val p by vm.scanner.progress.collectAsState()
    val s = vm.settings.state
    val startedAt = remember { p.finishedAt }
    LaunchedEffect(p.running, p.finishedAt) {
        if (!p.running && p.finishedAt > startedAt) vm.resetTo(Screen.Home)
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp)) {
        Spacer(Modifier.height(100.dp))
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(c.chipBg), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Image, null, tint = c.accent, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Finding your images", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            (if (s.autoFind) "Reading Android's list of image folders" else "Scanning your extra folders") +
                ". This full read happens once; after that only changes are checked, in a moment.",
            fontSize = 15.sp, color = c.subtext,
        )
        Spacer(Modifier.height(28.dp))
        LinearProgressIndicator(
            progress = { if (p.total == 0) 0f else p.done.toFloat() / p.total },
            color = c.accent, trackColor = c.divider,
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text("${fmtNum(p.images)} images found", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${fmtNum(p.done)} / ${fmtNum(p.total)} folders", fontSize = 14.sp, color = c.subtext)
        }
        Text(
            if (p.current.isEmpty()) "" else "Scanning ${shortPath(p.current)}",
            fontSize = 12.sp, color = c.subtext, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = { vm.resetTo(Screen.Home) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Browse while scanning", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text) }
        Text(
            "Folders appear as soon as they're scanned",
            fontSize = 12.sp, color = c.subtext, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
        )
    }
}

// =================== Settings ===================

@Composable
private fun SettingRow(label: String, value: String, action: String? = null, onClick: (() -> Unit)? = null) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp)
            Text(value, fontSize = 12.sp, color = c.subtext)
        }
        if (action != null) Text(action, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
    }
}

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, display: (Int) -> String = { it.toString() }, onChange: (Int) -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        IconBtn(AppIcons.Back, "Less", if (value > range.first) c.text else c.divider) { if (value > range.first) onChange(value - 1) }
        Text(display(value), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(72.dp), color = c.text,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconBtn(AppIcons.Next, "More", if (value < range.last) c.text else c.divider) { if (value < range.last) onChange(value + 1) }
    }
}

@Composable
private fun Group(title: String) {
    val c = LocalAppColors.current
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 2.dp))
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings.state
    val p by vm.scanner.progress.collectAsState()
    var cacheBytes by remember { mutableLongStateOf(-1L) }
    var cacheRefresh by remember { mutableIntStateOf(0) }
    var sortScope by remember { mutableStateOf<String?>(null) }
    var slideDefaults by remember { mutableStateOf(false) }
    LaunchedEffect(cacheRefresh) { cacheBytes = withContext(Dispatchers.IO) { vm.thumbs.sizeBytes() } }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopBar("Settings", onBack = { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Group("Storage")
            SettingRow(
                "Folders",
                (if (s.autoFind) "Automatic" else "Only extra folders") +
                    (if (s.roots.isNotEmpty()) " + ${s.roots.size} extra" else "") +
                    (if (s.excludes.isNotEmpty()) " · ${s.excludes.size} excluded" else ""),
                "Change",
            ) { vm.push(Screen.Roots) }
            SettingRow(
                "Rescan now",
                if (p.running) "Scanning… ${fmtNum(p.done)} / ${fmtNum(p.total)} folders"
                else "Last scan: ${fmtDate(s.lastScan)}",
                if (p.running) null else "Scan",
            ) { if (!p.running) vm.startScan(forceFull = true) }
            SettingRow(
                "Thumbnail cache",
                (if (cacheBytes < 0) "Calculating…" else fmtSize(cacheBytes)) + " of ${fmtSize(s.cacheLimitMb * 1024L * 1024L)} limit",
                "Clear",
            ) { vm.clearThumbCache { cacheRefresh++ } }
            StepperRow("Cache limit", (s.cacheLimitMb / 500).coerceIn(1, 10), 1..10, display = { "%.1f GB".format(it * 0.5) }) { n ->
                vm.settings.update { it.copy(cacheLimitMb = n * 500) }
            }

            Group("Appearance")
            SettingRow("Colors", "App, text, accent and viewer colors (hex)", "Edit") { vm.push(Screen.Colors) }

            Group("Display")
            StepperRow("Folder grid columns", s.folderCols, 2..6) { n -> vm.settings.update { it.copy(folderCols = n) } }
            StepperRow("Image grid columns", s.imageCols, 2..8) { n -> vm.settings.update { it.copy(imageCols = n) } }

            Group("Sorting")
            SettingRow("Default folder sort", sortLabel(s.folderSortKey, s.folderSortAsc)) { sortScope = "folders" }
            SettingRow("Default image sort", sortLabel(s.imageSortKey, s.imageSortAsc)) { sortScope = "images" }
            Column(Modifier.padding(horizontal = 20.dp)) {
                SwitchRow("Remember sort per folder", s.rememberPerFolder, "Otherwise one global sort for all folders") { v ->
                    vm.settings.update { it.copy(rememberPerFolder = v) }
                }
            }

            Group("Slideshow")
            SettingRow(
                "Slideshow defaults",
                "${s.slideSecs} s per slide · ${s.slideTransition}" + (if (s.slideLoop) " · loop" else "") + (if (s.slideShuffle) " · shuffle" else ""),
                "Edit",
            ) { slideDefaults = true }
            Column(Modifier.padding(horizontal = 20.dp)) {
                SwitchRow("Keep screen on", s.slideKeepOn, "While a slideshow plays") { v -> vm.settings.update { it.copy(slideKeepOn = v) } }
            }
        }
    }
    sortScope?.let { sc -> SortSheet(vm, null, sc) { sortScope = null } }
    if (slideDefaults) SlideshowSheet(vm, canStartHere = false, onDismiss = { slideDefaults = false }, onStart = null)
}

// =================== Colors ===================

@Composable
fun ColorsScreen(vm: AppViewModel) {
    val s = vm.settings.state
    val c = LocalAppColors.current
    val d = AppSettings()
    data class Field(val label: String, val hint: String, val get: (AppSettings) -> String, val set: (AppSettings, String) -> AppSettings)
    val fields = listOf(
        Field("App background", "Folders, grid, menus", { it.cAppBg }, { a, v -> a.copy(cAppBg = v) }),
        Field("Text color", "Titles and labels", { it.cText }, { a, v -> a.copy(cText = v) }),
        Field("Accent color", "Buttons, sort chip, selection", { it.cAccent }, { a, v -> a.copy(cAccent = v) }),
        Field("Viewer background", "Behind the full-screen image", { it.cViewerBg }, { a, v -> a.copy(cViewerBg = v) }),
        Field("Viewer bar background", "Top and bottom bars in viewer", { it.cBarBg }, { a, v -> a.copy(cBarBg = v) }),
        Field("Viewer bar text", "Icons and text on viewer bars", { it.cBarText }, { a, v -> a.copy(cBarText = v) }),
    )
    // Text being typed (may be invalid); valid values are saved immediately.
    val drafts = remember { mutableStateOf(fields.map { it.get(s) }) }
    var resetTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(resetTick) { if (resetTick > 0) drafts.value = fields.map { it.get(vm.settings.state) } }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        TopBar("Colors", onBack = { vm.back() }) {
            Text(
                "Reset", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                    vm.settings.update {
                        it.copy(cAppBg = d.cAppBg, cText = d.cText, cAccent = d.cAccent, cViewerBg = d.cViewerBg, cBarBg = d.cBarBg, cBarText = d.cBarText)
                    }
                    resetTick++
                }.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // Live preview
            Row(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, c.divider, RoundedCornerShape(16.dp))) {
                Column(Modifier.weight(1f).fillMaxSize().background(c.appBg).padding(10.dp)) {
                    Text("Album_003", color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf(0xFFC9D6DF, 0xFFE3D5CA, 0xFFB8C4B0).forEach { col ->
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(4.dp)).background(Color(col)))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Name · A→Z", color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).fillMaxSize().background(c.viewerBg), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("img_0042.webp", color = c.barText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().background(c.barBg).padding(8.dp))
                    Box(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 8.dp).background(Color(0xFFA7BBC7)))
                    Row(Modifier.fillMaxWidth().background(c.barBg).padding(8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        Text("Slideshow", color = c.accent, fontSize = 10.sp); Text("Move", color = c.barText, fontSize = 10.sp); Text("Delete", color = c.barText, fontSize = 10.sp)
                    }
                }
            }
            Text("Left: app screens · Right: image viewer", fontSize = 12.sp, color = c.subtext, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))

            fields.forEachIndexed { i, f ->
                val text = drafts.value[i]
                val parsed = parseHex(text)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.label, fontSize = 15.sp)
                        Text(if (parsed == null) "Not a valid hex code" else f.hint, fontSize = 12.sp, color = if (parsed == null) c.danger else c.subtext)
                    }
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(parsed ?: parseHex(f.get(s)) ?: Color.Gray)
                            .border(1.dp, c.divider, RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v ->
                            val t = v.trim().take(7)
                            drafts.value = drafts.value.toMutableList().also { it[i] = t }
                            if (parseHex(t) != null) vm.settings.update { a -> f.set(a, t.uppercase()) }
                        },
                        singleLine = true,
                        isError = parsed == null,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = c.text),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent),
                        modifier = Modifier.width(130.dp),
                    )
                }
            }
            Text(
                "Type a hex code like #000000. Changes apply immediately everywhere in the app.",
                fontSize = 12.sp, color = c.subtext, modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
            )
        }
    }
}
