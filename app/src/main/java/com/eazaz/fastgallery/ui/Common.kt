package com.eazaz.fastgallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---------- Colors ----------

data class AppColors(
    val appBg: Color,
    val text: Color,
    val accent: Color,
    val viewerBg: Color,
    val barBg: Color,
    val barText: Color,
) {
    val subtext: Color get() = text.copy(alpha = 0.68f)
    val divider: Color get() = text.copy(alpha = 0.10f)
    val chipBg: Color get() = accent.copy(alpha = 0.12f).compositeOver(appBg)
    val placeholder: Color get() = text.copy(alpha = 0.07f).compositeOver(appBg)
    val field: Color get() = text.copy(alpha = 0.05f).compositeOver(appBg)
    val danger: Color get() = Color(0xFFB91C1C)
}

fun parseHex(s: String): Color? {
    val t = s.trim()
    if (!Regex("^#[0-9a-fA-F]{6}$").matches(t)) return null
    return Color(("FF" + t.substring(1)).toLong(16))
}

val DefaultColors = AppColors(
    Color(0xFFFFFFFF), Color(0xFF111827), Color(0xFF2563EB),
    Color(0xFFF3F4F6), Color(0xFFFFFFFF), Color(0xFF111827),
)

val LocalAppColors = staticCompositionLocalOf { DefaultColors }

@Composable
fun AppTheme(c: AppColors, content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = c.accent, onPrimary = Color.White,
        secondaryContainer = c.chipBg, onSecondaryContainer = c.text,
        background = c.appBg, onBackground = c.text,
        surface = c.appBg, onSurface = c.text,
        surfaceVariant = c.field, onSurfaceVariant = c.subtext,
        surfaceContainer = c.appBg, surfaceContainerLow = c.appBg, surfaceContainerHigh = c.appBg,
        surfaceContainerHighest = c.field, surfaceContainerLowest = c.appBg,
        outline = c.text.copy(alpha = 0.35f), outlineVariant = c.divider,
    )
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme) {
            CompositionLocalProvider(LocalContentColor provides c.text, content = content)
        }
    }
}

// ---------- Icons (drawn from simple paths, no big icon library) ----------

object AppIcons {
    private fun stroke(vararg paths: String, w: Float = 2f): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it), fill = null, stroke = SolidColor(Color.Black),
                    strokeLineWidth = w, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun filled(vararg paths: String): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            paths.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()

    val Back = stroke("M15 18l-6-6 6-6")
    val Next = stroke("M9 6l6 6-6 6")
    val More = filled(
        "M12 3.2a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 1 0 0-3.6z",
        "M12 10.2a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 1 0 0-3.6z",
        "M12 17.2a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 1 0 0-3.6z",
    )
    val Sort = stroke("M3 6h18M6 12h12M10 18h4")
    val Pin = stroke("M12 17v5M9 3h6l-1 7 4 3H6l4-3-1-7z")
    val PinFilled = filled("M9 3h6l-1 7 4 3h-5v8.5a1 1 0 0 1-2 0V13H6l4-3z")
    val Play = stroke("M7 4l13 8-13 8z")
    val PlayFilled = filled("M7 4l13 8-13 8z")
    val Pause = stroke("M8 5v14M16 5v14", w = 3f)
    val Close = stroke("M6 6l12 12M18 6L6 18")
    val Check = stroke("M5 12l5 5 9-9", w = 3f)
    val Info = stroke("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 11v5M12 8h.01")
    val Edit = stroke("M4 20h4L19 9l-4-4L4 16z")
    val Wallpaper = stroke("M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M3 16l5-5 5 5 3-3 5 5")
    val Move = stroke("M3 6h6l2 2h10v11H3z", "M10 14h6M13 11l3 3-3 3")
    val Trash = stroke("M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14")
    val SelectAll = stroke("M7 4h10a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z", "M8 12l3 3 5-6")
    val Plus = stroke("M12 5v14M5 12h14")
    val Search = stroke("M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M20 20l-4-4")
    val Folder = stroke("M3 6h6l2 2h10v11H3z")
    val Share = stroke("M18 3a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M6 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M18 15a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M8.6 13.5l6.8 4M15.4 6.5l-6.8 4")
    val Copy = stroke("M9 9h11v11H9z", "M5 15H4V4h11v1")
    val Crop = stroke("M6 2v14a2 2 0 0 0 2 2h14", "M18 22V8a2 2 0 0 0-2-2H2")
    val RotateLeft = stroke("M3 4v6h6", "M3.5 14a8.5 8.5 0 1 0 2-8.5L3 10")
    val RotateRight = stroke("M21 4v6h-6", "M20.5 14a8.5 8.5 0 1 1-2-8.5L21 10")
    val FlipH = stroke("M12 3v18", "M8 7L3 12l5 5z", "M16 7l5 5-5 5z")
    val FlipV = stroke("M3 12h18", "M7 8l5-5 5 5z", "M7 16l5 5 5-5z")
    val Sun = stroke("M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")
    val Resize = stroke("M15 3h6v6", "M9 21H3v-6", "M21 3l-7 7", "M3 21l7-7")
    val Image = stroke("M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M3 16l5-5 5 5 3-3 5 5", "M16 8.5h.01")
}

// ---------- Small building blocks ----------

@Composable
fun IconBtn(icon: ImageVector, desc: String, tint: Color = LocalAppColors.current.text, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    bg: Color = LocalAppColors.current.appBg,
    fg: Color = LocalAppColors.current.text,
    large: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().background(bg).height(60.dp)
            .padding(start = if (onBack == null) 16.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconBtn(AppIcons.Back, "Back", fg, onBack)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                title, color = fg, fontSize = if (large) 24.sp else 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, color = fg.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = LocalAppColors.current
    Row(modifier.padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = c.subtext, modifier = Modifier.size(14.dp))
            Box(Modifier.width(6.dp))
        }
        Text(text, color = c.subtext, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Pill(text: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Text(
        text, color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(c.chipBg).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** Segmented control. options = (id, label). */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.field).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (id, label) ->
            val on = id == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (on) c.appBg else Color.Transparent)
                    .clickable { onSelect(id) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) c.text else c.subtext)
            }
        }
    }
}

/** Two big choice buttons. */
@Composable
fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label) ->
            val on = id == selected
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) c.chipBg else c.appBg)
                    .border(2.dp, if (on) c.accent else c.divider, RoundedCornerShape(12.dp))
                    .clickable { onSelect(id) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (on) c.accent else c.text,
                )
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    val c = LocalAppColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = c.subtext)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = Color.White),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalAppColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.appBg,
        contentColor = c.text,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
            content()
        }
    }
}

@Composable
fun SheetAction(icon: ImageVector, label: String, color: Color = LocalAppColors.current.text, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Box(Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, color = color)
    }
}

// ---------- Formatting ----------

private val numFmt: NumberFormat = NumberFormat.getIntegerInstance()
fun fmtNum(n: Int): String = numFmt.format(n)

fun fmtSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

fun fmtDate(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(Date(ms))

fun fmtMonth(ms: Long): String = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(ms))

fun sortLabel(key: String, asc: Boolean): String = when (key) {
    "name" -> if (asc) "Name · A→Z" else "Name · Z→A"
    "modified" -> if (asc) "Date modified · Oldest" else "Date modified · Newest"
    "size" -> if (asc) "Size · Smallest" else "Size · Largest"
    else -> if (asc) "Date taken · Oldest" else "Date taken · Newest"
}

// ---------- Grid helpers ----------

fun LazyGridState.indexAt(offset: Offset): Int? =
    layoutInfo.visibleItemsInfo.firstOrNull {
        offset.x.toInt() in it.offset.x until it.offset.x + it.size.width &&
            offset.y.toInt() in it.offset.y until it.offset.y + it.size.height
    }?.index

/** Pinch in/out with two fingers changes the number of columns. One-finger scrolling is untouched. */
fun Modifier.pinchToChangeColumns(current: () -> Int, range: IntRange, onChange: (Int) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zoom = 1f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size >= 2) {
                    zoom *= event.calculateZoom()
                    val cols = current()
                    if (zoom > 1.25f && cols > range.first) { onChange(cols - 1); zoom = 1f }
                    else if (zoom < 0.8f && cols < range.last) { onChange(cols + 1); zoom = 1f }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

/** Draggable fast-scroll handle on the right edge, with a label bubble while dragging. */
@Composable
fun BoxScope.FastScroller(state: LazyGridState, itemCount: Int, label: (Int) -> String) {
    if (itemCount < 60) return
    val c = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val thumbPx = with(density) { 56.dp.toPx() }

    BoxWithConstraints(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(160.dp)) {
        val trackPx = constraints.maxHeight.toFloat()
        val live = run {
            val vis = state.layoutInfo.visibleItemsInfo.size
            val max = (itemCount - vis).coerceAtLeast(1)
            (state.firstVisibleItemIndex.toFloat() / max).coerceIn(0f, 1f)
        }
        val fraction = if (dragging) dragFraction else live
        val currentFraction by rememberUpdatedState(fraction)
        val y = (fraction * (trackPx - thumbPx)).coerceAtLeast(0f)

        if (dragging) {
            val idx = (dragFraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
            Text(
                label(idx), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(0, (y + thumbPx / 2 - 40f).roundToInt()) }
                    .padding(end = 28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF111827))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).offset { IntOffset(0, y.roundToInt()) }
                .size(width = 28.dp, height = 56.dp)
                .pointerInput(itemCount, trackPx) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; dragFraction = currentFraction },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, dy ->
                        change.consume()
                        dragFraction = (dragFraction + dy / (trackPx - thumbPx)).coerceIn(0f, 1f)
                        val idx = (dragFraction * (itemCount - 1)).roundToInt()
                        scope.launch { state.scrollToItem(idx) }
                    }
                },
        ) {
            Box(
                Modifier.align(Alignment.CenterEnd).padding(end = 2.dp).width(if (dragging) 8.dp else 6.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp)).background(if (dragging) c.accent else c.subtext.copy(alpha = 0.55f)),
            )
        }
    }
}
