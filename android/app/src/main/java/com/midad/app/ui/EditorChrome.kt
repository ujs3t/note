package com.midad.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midad.app.glass.GlassSurface
import com.midad.app.store.AppStore
import com.midad.app.store.Block
import com.midad.app.store.Note
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * ملحقات المحرر: الخريطة المصغّرة + شريط الأدوات الزجاجي + نافذة التنسيق
 */

/* ---------- الخريطة المصغّرة (عمود زجاجي يمين المحرر) ---------- */
@Composable
fun Minimap(
    store: AppStore,
    note: Note,
    ui: MidadUi,
    scroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier
) {
    val colors = LocalColors.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var trackH by remember { mutableStateOf(1f) }
    var scrubbing by remember { mutableStateOf(false) }

    val contentH = scroll.maxValue.toFloat() + ui.editorViewport
    val fracY = { y: Float -> (y / trackH).coerceIn(0f, 1f) }

    Box(
        modifier
            .width(30.dp)
    ) {
        // علامات الكتل + نافذة العرض
        Canvas(
            Modifier
                .matchParentSize()
                .onSizeChanged { trackH = it.height.toFloat().coerceAtLeast(1f) }
        ) {
            // نافذة العرض الحالي
            if (contentH > 1f && ui.editorViewport > 0f) {
                val viewTop = ((scroll.value / contentH) * trackH).coerceIn(0f, trackH)
                val viewFrac = (ui.editorViewport / contentH).coerceIn(0.05f, 1f) * trackH
                val vw = 14.dp.toPx()
                drawRoundRect(
                    color = if (scrubbing) Color(0x42F2C14E) else colors.accDim,
                    topLeft = Offset(size.width - vw - 4.dp.toPx(), viewTop),
                    size = Size(vw, viewFrac.coerceAtLeast(30f)),
                    cornerRadius = CornerRadius(99f)
                )
                drawRoundRect(
                    color = Color(0x73F2C14E),
                    topLeft = Offset(size.width - vw - 4.dp.toPx(), viewTop),
                    size = Size(vw, viewFrac.coerceAtLeast(30f)),
                    cornerRadius = CornerRadius(99f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )
            }
            if (contentH > 1f) {
                for ((id, pair) in ui.blockMetrics) {
                    val (topY, bh) = pair
                    val block = note.blocks.firstOrNull { it.id == id } ?: continue
                    val kind = when (block) {
                        is Block.Text -> if (block.style == "h1" || block.style == "h2") "h" else "p"
                        is Block.Code -> "code"
                        is Block.Image -> "image"
                        is Block.Todo -> "todo"
                        is Block.Timer -> "timer"
                        else -> "p"
                    }
                    val y = ((topY / contentH) * trackH).coerceIn(0f, trackH)
                    val (w, c, a) = when (kind) {
                        "h" -> Triple(11.dp.toPx(), colors.acc, 0.95f)
                        "code" -> Triple(7.dp.toPx(), Color(0xFFB08D57), 0.9f)
                        "image" -> Triple(7.dp.toPx(), colors.ok, 0.75f)
                        "todo" -> Triple(7.dp.toPx(), Color(0xFFD9A62B), 0.7f)
                        "timer" -> Triple(7.dp.toPx(), Color(0xFFC88B4A), 0.7f)
                        else -> Triple(6.dp.toPx(), Color(0xFF7A7260), 0.6f)
                    }
                    drawRoundRect(
                        color = c,
                        topLeft = Offset(size.width - w, y),
                        size = Size(w, 2.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                        alpha = a
                    )
                }
            }
        }
        // منطقة السحب
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { scrubbing = true },
                        onDragEnd = { scrubbing = false },
                        onDragCancel = { scrubbing = false }
                    ) { change, _ ->
                        change.consume()
                        val f = fracY(change.position.y)
                        if (scroll.maxValue > 0) scope.launch { scroll.scrollTo((f * scroll.maxValue).roundToInt()) }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val f = fracY(p.y)
                        if (scroll.maxValue > 0) scope.launch { scroll.scrollTo((f * scroll.maxValue).roundToInt()) }
                    }
                }
        )
    }
}

/* ---------- شريط الأدوات الزجاجي ---------- */
@Composable
fun EditorToolbar(
    store: AppStore,
    note: Note,
    ui: MidadUi,
    modifier: Modifier = Modifier
) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    GlassSurface(
        radius = 29.dp,
        modifier = modifier
            .width(360.dp)
            .height(58.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tool("clock", "مؤقت", fonts, colors) {
                ui.timePopTarget = "block"
                ui.timePopOpen = true
            }
            Tool("todo", "مهام", fonts, colors) {
                note.blocks.add(Block.Todo())
                store.touch(note); store.pushHistorySoon(note.id); store.vibrate()
            }
            Tool("code", "سكربت", fonts, colors) {
                note.blocks.add(Block.Code())
                store.touch(note); store.pushHistorySoon(note.id); store.vibrate()
            }
            Tool("aa", "تنسيق", fonts, colors, isAa = true) {
                if (ui.aaPopFor != null) ui.aaPopFor = null
                else ui.aaPopFor = note.blocks.indexOfFirst { it is Block.Text }.takeIf { it >= 0 }
            }
            Tool("mic", "صوت", fonts, colors) {
                ui.recOpen = true
            }
            Tool("image", "صورة", fonts, colors) {
                ui.requestImageForBlock = true
            }
        }
    }
}

@Composable
private fun Tool(
    icon: String,
    label: String,
    fonts: MidadFonts,
    colors: MidadColors,
    isAa: Boolean = false,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (pressed) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try { awaitRelease() } finally { pressed = false; onClick() }
                    }
                )
            }
            .padding(horizontal = 7.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isAa) {
            MText("Aa", color = colors.inkSoft, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = fonts.mono)
        } else {
            MidadIcon(icon, 20.dp, colors.inkSoft)
        }
        MText(
            label, color = colors.inkSoft, fontSize = 9.5.sp, fontWeight = FontWeight(600),
            fontFamily = fonts.sans, modifier = Modifier.padding(top = 3.dp)
        )
    }
}

/* ---------- نافذة التنسيق (Aa) ---------- */
@Composable
fun AaPop(store: AppStore, note: Note, ui: MidadUi, modifier: Modifier = Modifier) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val idx = ui.aaPopFor ?: return
    val block = note.blocks.getOrNull(idx) as? Block.Text ?: return
    GlassSurface(radius = 16.dp, modifier = modifier.width(180.dp)) {
        Column(Modifier.padding(8.dp)) {
            for ((style, label) in listOf(
                "h1" to "عنوان رئيسي", "h2" to "عنوان فرعي",
                "p" to "نص عادي", "quote" to "اقتباس"
            )) {
                val active = block.style == style
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) colors.accDim else Color.Transparent)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            block.style = style
                            ui.textEpoch++
                            ui.aaPopFor = null
                            store.touch(note)
                            store.pushHistory(note.id)
                            store.vibrate()
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    MText(
                        label,
                        color = if (active) colors.acc else colors.ink,
                        fontSize = 13.5.sp,
                        fontWeight = if (style == "h1" || style == "h2") FontWeight.Bold else FontWeight.Normal,
                        fontFamily = fonts.sans
                    )
                }
            }
        }
    }
}
