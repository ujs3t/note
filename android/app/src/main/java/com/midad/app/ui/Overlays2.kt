package com.midad.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import com.midad.app.store.TimerState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midad.app.glass.GlassSurface
import com.midad.app.store.AppStore
import com.midad.app.store.Block
import com.midad.app.store.GlassSettings

/*
 * الدرج (إعدادات الزجاج) + تأكيد الحذف + التنبيهات + قائمة الكتلة
 */

/* ---------- درج الإعدادات ---------- */
@Composable
fun DrawerOverlay(ui: MidadUi, onDismiss: () -> Unit) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val store = ui.store
    val g = store.state.settings.glass

    var open by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { open = 1f }
    val slide by animateFloatAsState(open, tween(520), label = "dr")
    val scrimA by animateFloatAsState(open, tween(380), label = "sc")

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .alpha(scrimA * 0.52f)
                .background(Color(0x85050403))
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
        )
        GlassSurface(
            radius = 24.dp,
            modifier = Modifier
                .align(androidx.compose.ui.AbsoluteAlignment.CenterLeft)
                .statusBarsPaddingSafe()
                .navigationBarsPaddingSafe()
                .padding(10.dp)
                .width(320.dp)
                .fillMaxHeight()
                .alpha(slide.coerceIn(0f, 1f))
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 24.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        MText("مِداد", color = colors.acc, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = fonts.sans)
                        MText(
                            "دفتر ملاحظات ومهام يعمل محليًا بالكامل على جهازك",
                            color = colors.inkSoft, fontSize = 11.5.sp, lineHeight = 18.sp,
                            fontFamily = fonts.sans, modifier = Modifier.padding(top = 8.dp).width(200.dp)
                        )
                    }
                    IconBtn("x", colors.ink, onClick = onDismiss)
                }

                SecTitle("تأثير الزجاج السائل", colors, fonts)
                SetRow(
                    name = "تفعيل الزجاج السائل",
                    hint = "يطبّق انكسارًا حقيقيًا على كل العناصر",
                    colors = colors, fonts = fonts
                ) {
                    MSwitch(checked = g.on, onToggle = {
                        g.on = !g.on
                        store.glassVersion++
                        store.saveSoon()
                    })
                }
                GlassSlider("لمعان السطح", g.specularOpacity / 100f, "0.40→1", "%.2f".format(g.specularOpacity), colors, fonts) { v -> g.specularOpacity = v * 100f; store.glassVersion++; store.saveSoon() }
                GlassSlider("تشبّع اللمعان", g.specularSaturation / 10f, null, "%.0f".format(g.specularSaturation), colors, fonts) { v -> g.specularSaturation = v * 10f; store.glassVersion++; store.saveSoon() }
                GlassSlider("مستوى الانكسار", g.refractionLevel / 2f, null, "%.2f".format(g.refractionLevel), colors, fonts) { v -> g.refractionLevel = v * 2f; store.glassVersion++; store.saveSoon() }
                GlassSlider("شدة الضبابية", g.blur / 10f, null, "%.1f".format(g.blur), colors, fonts) { v -> g.blur = v * 10f; store.glassVersion++; store.saveSoon() }
                GlassSlider("الضبابية التدريجية", g.progressiveBlurStrength / 2f, null, "%.2f".format(g.progressiveBlurStrength), colors, fonts) { v -> g.progressiveBlurStrength = v * 2f; store.glassVersion++; store.saveSoon() }
                GlassSlider("شفافية الخلفية", g.glassBgOpacity, null, "%.2f".format(g.glassBgOpacity), colors, fonts) { v -> g.glassBgOpacity = v; store.glassVersion++; store.saveSoon() }

                ResetBtn("استعادة الإعدادات الافتراضية", colors, fonts) {
                    val s = store.state.settings
                    s.glass = GlassSettings()
                    store.glassVersion++
                    store.saveSoon()
                    store.toast("استُعيدت الإعدادات الافتراضية")
                }

                SecTitle("إعدادات النظام", colors, fonts)
                SetRow("الاهتزاز التفاعلي", "تغذية راجعة عند السحب والتحديد", colors, fonts) {
                    MSwitch(checked = store.state.settings.haptics, onToggle = {
                        store.state.settings.haptics = !store.state.settings.haptics
                        store.saveSoon()
                    })
                }
                SetRow("الخريطة المصغّرة", "العمود الزجاجي الجانبي في المحرر", colors, fonts) {
                    MSwitch(checked = store.state.settings.minimap, onToggle = {
                        store.state.settings.minimap = !store.state.settings.minimap
                        store.saveSoon()
                    })
                }
                DangerBtn("تفريغ جميع البيانات", colors, fonts) {
                    ui.confirmState = ConfirmState(
                        "تفريغ جميع البيانات",
                        "ستُحذف كل الملاحظات والمهام نهائيًا. لا يمكن التراجع.",
                        "تفريغ"
                    ) {
                        store.state.notes.clear()
                        store.state.tasks.clear()
                        store.saveNow()
                        store.toast("فُرّغت البيانات")
                    }
                }

                SecTitle("بيانات المطور", colors, fonts)
                DevRow("التطبيق", "مِداد · الإصدار 2.9", colors, fonts)
                DevRow("البنية", "أندرويد أصلي · Kotlin + Compose", colors, fonts)
                DevRow("التخزين", "محلي · midad.v2.json", colors, fonts)
                DevRow("الخطوط", "IBM Plex Sans Arabic · Mono", colors, fonts)
                DevRow("الزجاج", "AGSL · انكسار سنيل حقيقي", colors, fonts)
            }
        }
    }
}

@Composable
private fun SecTitle(t: String, colors: MidadColors, fonts: MidadFonts) {
    Row(
        Modifier
            .padding(top = 26.dp, bottom = 12.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MText(t, color = colors.acc, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = fonts.sans)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(colors.line))
    }
}

@Composable
private fun SetRow(name: String, hint: String, colors: MidadColors, fonts: MidadFonts, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            MText(name, color = colors.ink, fontSize = 13.5.sp, fontWeight = FontWeight(600), fontFamily = fonts.sans)
            MText(hint, color = colors.inkSoft, fontSize = 11.sp, fontFamily = fonts.sans, modifier = Modifier.padding(top = 2.dp))
        }
        trailing()
    }
}

/** منزلق بنفس شكل slider-row في الويب (مقبض #82B1FF) */
@Composable
private fun GlassSlider(
    label: String,
    value: Float,
    range: String?,
    valueText: String,
    colors: MidadColors,
    fonts: MidadFonts,
    onChange: (Float) -> Unit
) {
    var v by remember(value) { mutableStateOf(value.coerceIn(0f, 1f)) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MText(label, color = colors.inkSoft, fontSize = 12.sp, fontFamily = fonts.sans, modifier = Modifier.width(104.dp))
        var dragging by remember { mutableStateOf(false) }
        val thumbScale by animateFloatAsState(if (dragging) 1.15f else 1f, tween(150), label = "th")
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(22.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, _ ->
                        change.consume()
                        v = (change.position.x / size.width).coerceIn(0f, 1f)
                        onChange(v)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        v = (p.x / size.width).coerceIn(0f, 1f)
                        onChange(v)
                    }
                }
        ) {
            val travel = maxWidth - 16.dp
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.16f))
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(v.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF82B1FF).copy(alpha = 0.55f))
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = travel * v)
                    .size(16.dp)
                    .scale(thumbScale)
                    .clip(CircleShape)
                    .background(Color(0xFF82B1FF))
            )
        }
        MText(
            valueText, color = colors.acc, fontSize = 11.sp, fontFamily = fonts.mono,
            modifier = Modifier.width(42.dp).padding(start = 6.dp), textAlign = TextAlign.End, maxLines = 1
        )
    }
}

/* ---------- زر إعادة التعيين / الخطِر ---------- */
@Composable
private fun ResetBtn(label: String, colors: MidadColors, fonts: MidadFonts, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14F2C14E))
            .border(1.dp, Color(0x59F2C14E), RoundedCornerShape(12.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        MText(label, color = colors.acc, fontSize = 13.sp, fontWeight = FontWeight(600), fontFamily = fonts.sans)
    }
}

@Composable
private fun DangerBtn(label: String, colors: MidadColors, fonts: MidadFonts, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1AE06C55))
            .border(1.dp, Color(0x66E06C55), RoundedCornerShape(12.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        MText(label, color = colors.red, fontSize = 13.5.sp, fontWeight = FontWeight(600), fontFamily = fonts.sans)
    }
}

@Composable
private fun DevRow(k: String, v: String, colors: MidadColors, fonts: MidadFonts) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MText(k, color = colors.inkSoft, fontSize = 12.5.sp, fontFamily = fonts.sans)
        MText(v, color = colors.ink, fontSize = 12.5.sp, fontWeight = FontWeight(600), fontFamily = fonts.sans, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/* ---------- نافذة التأكيد ---------- */
@Composable
fun ConfirmOverlay(ui: MidadUi) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val c = ui.confirmState ?: return
    var appear by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { appear = 1f }
    val scaleA by animateFloatAsState(if (appear > 0f) 1f else 0.82f, JELLY, label = "cf")
    val fade by animateFloatAsState(appear, tween(250), label = "cf2")

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .alpha(fade * 0.6f)
                .background(Color(0x99050403))
                .pointerInput(Unit) { detectTapGestures(onTap = { ui.confirmState = null }) }
        )
        GlassSurface(
            radius = 20.dp,
            tintAlpha = 0.55f,
            tintColor = Color(0xFF1E1B17),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .scale(scaleA)
                .alpha(fade)
        ) {
            Column(Modifier.padding(22.dp)) {
                MText(c.title, color = colors.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = fonts.sans)
                MText(c.body, color = colors.inkSoft, fontSize = 13.sp, lineHeight = 22.sp, fontFamily = fonts.sans, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.card2)
                            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { ui.confirmState = null }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MText("إلغاء", color = colors.ink, fontSize = 14.sp, fontWeight = FontWeight(600), fontFamily = fonts.sans)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.red)
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                ui.confirmState = null
                                c.onOk()
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MText(c.okLabel, color = Color(0xFF1B0E0A), fontSize = 14.sp, fontWeight = FontWeight(600), fontFamily = fonts.sans)
                    }
                }
            }
        }
    }
}

/* ---------- طبقة التنبيهات ---------- */
@Composable
fun ToastsLayer(ui: MidadUi) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        for ((id, msg) in ui.store.toastQueue) {
            var appear by remember(id) { mutableStateOf(0f) }
            LaunchedEffect(id) { appear = 1f }
            val s by animateFloatAsState(if (appear > 0f) 1f else 0.9f, JELLY, label = "t")
            val a by animateFloatAsState(appear, tween(350), label = "t2")
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .scale(s)
                    .alpha(a)
                    .clip(RoundedCornerShape(99.dp))
                    .background(colors.ink)
                    .padding(horizontal = 19.dp, vertical = 9.dp)
            ) {
                MText(msg, color = Color(0xFF191610), fontSize = 12.5.sp, fontWeight = FontWeight(600), fontFamily = fonts.sans, maxLines = 2)
            }
        }
    }
}

/* ---------- قائمة الكتلة (ضغط مطوّل) ---------- */
@Composable
fun BlockMenuOverlay(ui: MidadUi) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val store = ui.store
    val menu = ui.blockMenu ?: return
    val note = store.noteById(menu.noteId) ?: return
    val block = note.blocks.getOrNull(menu.blockIndex) ?: return
    val idx = menu.blockIndex

    var appear by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { appear = 1f }
    val s by animateFloatAsState(if (appear > 0f) 1f else 0.85f, JELLY, label = "bm")

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { ui.blockMenu = null }) })
        Column(
            Modifier
                .align(Alignment.Center)
                .width(210.dp)
                .scale(s)
                .alpha(appear)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF5252018))
                .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                .padding(5.dp)
        ) {
            // صف الأنماط (لكتل النص)
            if (block is Block.Text) {
                Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for ((style, lab) in listOf("h1" to "H1", "h2" to "H2", "p" to "P", "quote" to "\"")) {
                        val active = block.style == style
                        Box(
                            Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) colors.accDim else Color.Transparent)
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                                    block.style = style
                                    ui.textEpoch++
                                    store.touch(note); store.pushHistory(note.id)
                                    ui.blockMenu = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            MText(lab, color = if (active) colors.acc else colors.ink, fontSize = 12.sp, fontWeight = FontWeight(500), fontFamily = fonts.sans)
                        }
                    }
                }
            }
            MenuRow {
                MenuBtn("arrowUp", colors.ink, "تحريك للأعلى") {
                    if (idx > 0) { note.blocks.removeAt(idx); note.blocks.add(idx - 1, block); store.touch(note); store.pushHistory(note.id) }
                    ui.blockMenu = null
                }
                MenuBtn("arrowDown", colors.ink, "تحريك للأسفل") {
                    if (idx < note.blocks.size - 1) { note.blocks.removeAt(idx); note.blocks.add(idx + 1, block); store.touch(note); store.pushHistory(note.id) }
                    ui.blockMenu = null
                }
                MenuBtn("copy", colors.ink, "تكرار") {
                    val copyBlock = when (block) {
                        is Block.Text -> Block.Text(block.style, block.text)
                        is Block.Todo -> Block.Todo(block.items.map { com.midad.app.store.TodoItem(it.done, it.text) }.toMutableList())
                        is Block.Code -> Block.Code(block.title, block.code)
                        is Block.Timer -> Block.Timer(TimerState(block.timer.duration, block.timer.remaining))
                        is Block.Image -> Block.Image(block.src)
                        is Block.Voice -> Block.Voice(block.url, block.duration)
                    }
                    note.blocks.add(idx + 1, copyBlock)
                    store.touch(note); store.pushHistory(note.id)
                    ui.blockMenu = null
                }
                MenuBtn("trash", colors.red, "حذف") {
                    note.blocks.removeAt(idx)
                    store.touch(note); store.pushHistory(note.id)
                    ui.blockMenu = null
                }
            }
            // نسخ النص
            MenuRow {
                Box(
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x0DFFFFFF))
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            val txt = when (block) {
                                is Block.Text -> block.text
                                is Block.Code -> block.code
                                is Block.Todo -> block.items.joinToString("\n") { (if (it.done) "☑ " else "☐ ") + it.text }
                                else -> ""
                            }
                            if (txt.isNotEmpty()) store.toast("نُسخ النص")
                            ui.blockMenu = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MidadIcon("copy", 14.dp, colors.ink)
                        MText("نسخ المحتوى", color = colors.ink, fontSize = 11.5.sp, fontWeight = FontWeight(600), fontFamily = fonts.sans, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(content: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) { content() }
        Box(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors_lineSoft())
        )
    }
}

private fun colors_lineSoft(): Color = Color(0xFF282218)

@Composable
private fun MenuBtn(icon: String, tint: Color, cd: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        MidadIcon(icon, 15.dp, tint)
    }
}
