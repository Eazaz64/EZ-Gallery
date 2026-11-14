package com.eazaz.fastgallery.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazaz.fastgallery.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Crop rectangle in 0..1 of the rotated image. */
data class Rect01(val l: Float = 0f, val t: Float = 0f, val r: Float = 1f, val b: Float = 1f)

data class EditState(
    val rot90: Int = 0,          // 0..3 quarter turns clockwise
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val straighten: Float = 0f,  // -45..45 degrees
    val crop: Rect01 = Rect01(),
    val brightness: Float = 0f,  // -100..100
    val contrast: Float = 0f,    // -100..100
    val scale: Float = 100f,     // 10..100 %
)

object EditRender {
    fun decode(path: String, maxSide: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { d, info, _ ->
            val big = max(info.size.width, info.size.height)
            if (big > maxSide) {
                val s = maxSide.toFloat() / big
                d.setTargetSize(max(1, (info.size.width * s).roundToInt()), max(1, (info.size.height * s).roundToInt()))
            }
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    /** Rotate 90° steps, flip, and straighten (zoomed so no empty corners). */
    fun orient(src: Bitmap, st: EditState): Bitmap {
        val w = src.width
        val h = src.height
        val swap = st.rot90 % 2 != 0
        val ow = if (swap) h else w
        val oh = if (swap) w else h
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        val m = Matrix()
        m.postTranslate(-w / 2f, -h / 2f)
        m.postRotate(st.rot90 * 90f)
        m.postScale(if (st.flipH) -1f else 1f, if (st.flipV) -1f else 1f)
        if (st.straighten != 0f) {
            val a = Math.toRadians(abs(st.straighten).toDouble())
            val c = cos(a)
            val s = sin(a)
            val k = max((ow * c + oh * s) / ow, (ow * s + oh * c) / oh).toFloat()
            m.postRotate(st.straighten)
            m.postScale(k, k)
        }
        m.postTranslate(ow / 2f, oh / 2f)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    fun colorMatrix(brightness: Float, contrast: Float): FloatArray {
        val cc = (100f + contrast) / 100f
        val t = (1f - cc) * 127.5f + brightness * 1.5f
        return floatArrayOf(
            cc, 0f, 0f, 0f, t,
            0f, cc, 0f, 0f, t,
            0f, 0f, cc, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Crop + brightness/contrast + resize, drawn in one step. */
    fun finish(oriented: Bitmap, st: EditState): Bitmap {
        val ow = oriented.width
        val oh = oriented.height
        val x = (st.crop.l * ow).roundToInt().coerceIn(0, ow - 1)
        val y = (st.crop.t * oh).roundToInt().coerceIn(0, oh - 1)
        val cw = ((st.crop.r - st.crop.l) * ow).roundToInt().coerceIn(1, ow - x)
        val ch = ((st.crop.b - st.crop.t) * oh).roundToInt().coerceIn(1, oh - y)
        val outW = max(1, (cw * st.scale / 100f).roundToInt())
        val outH = max(1, (ch * st.scale / 100f).roundToInt())
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        if (st.brightness != 0f || st.contrast != 0f) {
            paint.colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(colorMatrix(st.brightness, st.contrast)))
        }
        Canvas(out).drawBitmap(oriented, android.graphics.Rect(x, y, x + cw, y + ch), android.graphics.Rect(0, 0, outW, outH), paint)
        return out
    }

    /** Full-quality render saved next to the original as name_edit.ext. Returns the new file. */
    fun saveCopy(path: String, st: EditState): File {
        val src = File(path)
        val full = decode(path, 8000)
        val oriented = orient(full, st)
        full.recycle()
        val result = finish(oriented, st)
        oriented.recycle()

        val ext = src.extension.lowercase()
        val (fmt, quality, outExt) = when (ext) {
            "webp" -> Triple(Bitmap.CompressFormat.WEBP_LOSSY, 92, "webp")
            "png" -> Triple(Bitmap.CompressFormat.PNG, 100, "png")
            else -> Triple(Bitmap.CompressFormat.JPEG, 95, if (ext == "jpeg") "jpeg" else "jpg")
        }
        val dir = src.parentFile ?: throw IllegalStateException("no folder")
        var name = "${src.nameWithoutExtension}_edit.$outExt"
        var i = 1
        while (File(dir, name).exists()) { name = "${src.nameWithoutExtension}_edit ($i).$outExt"; i++ }
        val out = File(dir, name)
        FileOutputStream(out).use { result.compress(fmt, quality, it) }
        result.recycle()
        return out
    }
}

private val ratios = listOf(
    "Free" to 0f, "Original" to -1f, "1:1" to 1f, "4:3" to 4f / 3f, "3:4" to 3f / 4f, "16:9" to 16f / 9f, "9:16" to 9f / 16f,
)

/** Largest centred crop with the given pixel ratio. */
private fun cropForRatio(ratio: Float, iw: Int, ih: Int): Rect01 {
    if (ratio <= 0f) return Rect01()
    val imgRatio = iw.toFloat() / ih
    return if (imgRatio > ratio) {
        val nw = ratio / imgRatio
        Rect01((1 - nw) / 2, 0f, (1 + nw) / 2, 1f)
    } else {
        val nh = imgRatio / ratio
        Rect01(0f, (1 - nh) / 2, 1f, (1 + nh) / 2)
    }
}

private const val MIN = 0.06f

/** mode: 1=top-left 2=top-right 3=bottom-left 4=bottom-right 5=move. k = height per width (normalized) when ratio is locked. */
private fun dragCrop(r: Rect01, mode: Int, dx: Float, dy: Float, k: Float?): Rect01 {
    if (mode == 5) {
        val w = r.r - r.l
        val h = r.b - r.t
        val l = (r.l + dx).coerceIn(0f, 1f - w)
        val t = (r.t + dy).coerceIn(0f, 1f - h)
        return Rect01(l, t, l + w, t + h)
    }
    var l = r.l; var t = r.t; var rr = r.r; var b = r.b
    when (mode) {
        1 -> { l += dx; t += dy }
        2 -> { rr += dx; t += dy }
        3 -> { l += dx; b += dy }
        4 -> { rr += dx; b += dy }
    }
    if (k == null) {
        l = l.coerceIn(0f, r.r - MIN); rr = rr.coerceIn(r.l + MIN, 1f)
        t = t.coerceIn(0f, r.b - MIN); b = b.coerceIn(r.t + MIN, 1f)
        return Rect01(l, t, rr, b)
    }
    val w = rr - l
    val h = w * k
    when (mode) {
        1, 2 -> t = b - h
        else -> b = t + h
    }
    val ok = l >= 0f && t >= 0f && rr <= 1f && b <= 1f && w >= MIN && h >= MIN
    return if (ok) Rect01(l, t, rr, b) else r
}

@Composable
fun EditorScreen(vm: AppViewModel, path: String) {
    val c = LocalAppColors.current
    var st by remember { mutableStateOf(EditState()) }
    var tab by remember { mutableStateOf("crop") }
    var ratio by remember { mutableStateOf(0f) }
    var ratioLabel by remember { mutableStateOf("Free") }
    var saving by remember { mutableStateOf(false) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val preview by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { try { EditRender.decode(path, 1600) } catch (_: Throwable) { null } }
    }
    // Rotation/flip/straighten are applied to a small preview copy; crop and colors are live overlays.
    val oriented by produceState<Bitmap?>(null, preview, st.rot90, st.flipH, st.flipV, st.straighten) {
        val p = preview ?: return@produceState
        value = withContext(Dispatchers.Default) { EditRender.orient(p, st) }
    }
    val fullSize by produceState(IntSize.Zero, path) {
        value = withContext(Dispatchers.IO) {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, o)
            IntSize(o.outWidth, o.outHeight)
        }
    }

    Column(Modifier.fillMaxSize().background(c.viewerBg)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(c.barBg).statusBarsPadding().height(60.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(AppIcons.Close, "Cancel", c.barText) { vm.back() }
            Column(Modifier.weight(1f)) {
                Text("Edit", color = c.barText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Saved as a copy, original is kept", color = c.barText.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            Text(
                "Save", color = c.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = !saving && preview != null) {
                    saving = true
                    vm.saveEdit(path, st) { ok -> saving = false; if (ok) vm.back() }
                }.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        // Preview + crop frame
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(20.dp).onSizeChanged { box = it },
            contentAlignment = Alignment.Center,
        ) {
            val bmp = oriented
            if (bmp == null) {
                CircularProgressIndicator(color = c.accent)
            } else {
                val img = remember(bmp) { bmp.asImageBitmap() }
                val cm = remember(st.brightness, st.contrast) { ColorMatrix(EditRender.colorMatrix(st.brightness, st.contrast)) }
                // Where the picture is drawn inside the box (ContentScale.Fit)
                val scale = if (box.width == 0) 1f else min(box.width.toFloat() / bmp.width, box.height.toFloat() / bmp.height)
                val dw = bmp.width * scale
                val dh = bmp.height * scale
                val ox = (box.width - dw) / 2f
                val oy = (box.height - dh) / 2f
                Image(
                    img, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                    colorFilter = if (st.brightness != 0f || st.contrast != 0f) ColorFilter.colorMatrix(cm) else null,
                )
                val handlePx = with(density) { 36.dp.toPx() }
                val k: Float? = when {
                    ratio > 0f -> bmp.width / (ratio * bmp.height)
                    else -> null
                }
                var mode by remember { mutableStateOf(0) }
                ComposeCanvas(
                    Modifier.fillMaxSize().then(
                        if (tab != "crop") Modifier else Modifier.pointerInput(bmp, ratio, box) {
                            detectDragGestures(
                                onDragStart = { p ->
                                    val r = st.crop
                                    val corners = listOf(
                                        1 to Offset(ox + r.l * dw, oy + r.t * dh),
                                        2 to Offset(ox + r.r * dw, oy + r.t * dh),
                                        3 to Offset(ox + r.l * dw, oy + r.b * dh),
                                        4 to Offset(ox + r.r * dw, oy + r.b * dh),
                                    )
                                    val near = corners.minBy { (_, o) -> hypot(o.x - p.x, o.y - p.y) }
                                    val dist = hypot(near.second.x - p.x, near.second.y - p.y)
                                    val inside = p.x in (ox + r.l * dw)..(ox + r.r * dw) && p.y in (oy + r.t * dh)..(oy + r.b * dh)
                                    mode = if (dist < handlePx) near.first else if (inside) 5 else 0
                                },
                                onDragEnd = { mode = 0 },
                                onDragCancel = { mode = 0 },
                            ) { change, d ->
                                if (mode != 0 && dw > 0f && dh > 0f) {
                                    change.consume()
                                    st = st.copy(crop = dragCrop(st.crop, mode, d.x / dw, d.y / dh, k))
                                }
                            }
                        }
                    ),
                ) {
                    val r = st.crop
                    val l = ox + r.l * dw
                    val t = oy + r.t * dh
                    val rr = ox + r.r * dw
                    val b = oy + r.b * dh
                    val dim = Color.Black.copy(alpha = 0.5f)
                    // darken outside the crop
                    drawRect(dim, Offset(ox, oy), Size(dw, t - oy))
                    drawRect(dim, Offset(ox, b), Size(dw, oy + dh - b))
                    drawRect(dim, Offset(ox, t), Size(l - ox, b - t))
                    drawRect(dim, Offset(rr, t), Size(ox + dw - rr, b - t))
                    if (tab == "crop") {
                        val thin = Color.White.copy(alpha = 0.5f)
                        for (i in 1..2) {
                            val gx = l + (rr - l) * i / 3f
                            val gy = t + (b - t) * i / 3f
                            drawLine(thin, Offset(gx, t), Offset(gx, b), 1f)
                            drawLine(thin, Offset(l, gy), Offset(rr, gy), 1f)
                        }
                        drawRect(Color.White, Offset(l, t), Size(rr - l, b - t), style = Stroke(2.dp.toPx()))
                        val hl = 18.dp.toPx()
                        val sw = 4.dp.toPx()
                        listOf(Offset(l, t) to Offset(1f, 1f), Offset(rr, t) to Offset(-1f, 1f), Offset(l, b) to Offset(1f, -1f), Offset(rr, b) to Offset(-1f, -1f)).forEach { (p, dir) ->
                            drawLine(Color.White, p, Offset(p.x + hl * dir.x, p.y), sw)
                            drawLine(Color.White, p, Offset(p.x, p.y + hl * dir.y), sw)
                        }
                    }
                }
            }
            if (saving) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        // Tools
        Column(Modifier.fillMaxWidth().background(c.barBg).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            val ob = oriented
            when (tab) {
                "crop" -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ratios.forEach { (label, rv) ->
                        val on = ratioLabel == label
                        Text(
                            label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = if (on) c.accent else c.barText,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) c.chipBg else c.barText.copy(alpha = 0.06f))
                                .clickable {
                                    ratioLabel = label
                                    ratio = rv
                                    if (ob != null) {
                                        val px = if (rv < 0f) ob.width.toFloat() / ob.height else rv
                                        st = st.copy(crop = if (rv == 0f) st.crop else cropForRatio(px, ob.width, ob.height))
                                        if (rv < 0f) ratio = px
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
                "rotate" -> Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ToolButton(AppIcons.RotateLeft, "Left") { st = st.copy(rot90 = (st.rot90 + 3) % 4, crop = Rect01()); ratio = 0f; ratioLabel = "Free" }
                        ToolButton(AppIcons.RotateRight, "Right") { st = st.copy(rot90 = (st.rot90 + 1) % 4, crop = Rect01()); ratio = 0f; ratioLabel = "Free" }
                        ToolButton(AppIcons.FlipH, "Flip ↔") { st = st.copy(flipH = !st.flipH) }
                        ToolButton(AppIcons.FlipV, "Flip ↕") { st = st.copy(flipV = !st.flipV) }
                    }
                    LabeledSlider("Straighten", st.straighten, -45f..45f, "%.1f°".format(st.straighten)) { st = st.copy(straighten = it) }
                }
                "adjust" -> Column {
                    LabeledSlider("Brightness", st.brightness, -100f..100f, st.brightness.roundToInt().toString()) { st = st.copy(brightness = it) }
                    LabeledSlider("Contrast", st.contrast, -100f..100f, st.contrast.roundToInt().toString()) { st = st.copy(contrast = it) }
                }
                "resize" -> Column {
                    val w = if (fullSize.width > 0) fullSize else IntSize(ob?.width ?: 0, ob?.height ?: 0)
                    val swap = st.rot90 % 2 != 0
                    val bw = (if (swap) w.height else w.width) * (st.crop.r - st.crop.l) * st.scale / 100f
                    val bh = (if (swap) w.width else w.height) * (st.crop.b - st.crop.t) * st.scale / 100f
                    LabeledSlider("Scale", st.scale, 10f..100f, "${st.scale.roundToInt()}%  ·  ${bw.roundToInt()} × ${bh.roundToInt()} px") {
                        st = st.copy(scale = it.roundToInt().toFloat())
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf(
                    Triple("crop", AppIcons.Crop, "Crop"),
                    Triple("rotate", AppIcons.RotateRight, "Rotate"),
                    Triple("adjust", AppIcons.Sun, "Adjust"),
                    Triple("resize", AppIcons.Resize, "Resize"),
                ).forEach { (id, icon, label) ->
                    val on = tab == id
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { tab = id }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(icon, null, tint = if (on) c.accent else c.barText, modifier = Modifier.size(22.dp))
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) c.accent else c.barText)
                    }
                }
            }
            Text(
                "Reset all", fontSize = 13.sp, color = c.barText.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp))
                    .clickable { st = EditState(); ratio = 0f; ratioLabel = "Free" }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = c.barText, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 12.sp, color = c.barText)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onChange: (Float) -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.barText, modifier = Modifier.weight(1f))
            Text(shown, fontSize = 13.sp, color = c.accent, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Text(
                "Reset", fontSize = 12.sp, color = c.barText.copy(alpha = 0.7f),
                modifier = Modifier.clickable { onChange(if (range.start < 0f) 0f else range.endInclusive) },
            )
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
        )
    }
}
