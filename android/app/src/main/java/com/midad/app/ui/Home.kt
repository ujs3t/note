package com.midad.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midad.app.glass.BackdropHost
import com.midad.app.glass.GLASS_FALLBACK_SEARCH
import com.midad.app.glass.GlassSurface
import com.midad.app.glass.LocalBackdrop
import com.midad.app.glass.rememberBackdropSource
import com.midad.app.store.AppStore
import com.midad.app.store.Block
import com.midad.app.store.Note
import com.midad.app.store.Task
import com.midad.app.store.TimerState
import kotlinx.coroutines.delay

/*
 * الشاشة الرئيسية — topbar + بحث زجاجي + بطاقات + مهام + شريط سفلي زجاجي
 */

@Composable
fun HomeScreen(ui: MidadUi) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val store = ui.store
    val appState = store.state
    val _dv = store.dataVersion // إعادة تركيب عند تغيّر هيكلي

    // مصدر الخلفية المشترك — نفس فكرة backdrop-filter: القوائم تُسجَّل
    // ويلتقطها الزجاج (البحث + الشريط السفلي) بضبابية وانكسار حقيقي
    val backdrop = rememberBackdropSource()
    androidx.compose.runtime.CompositionLocalProvider(LocalBackdrop provides backdrop) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---- الشريط العلوي ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.bg)
                    .padding(start = 18.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    MText(
                        text = if (ui.view == "notes") "الملاحظات" else "المهام",
                        color = colors.ink, fontSize = 23.sp, fontWeight = FontWeight.Bold,
                        fontFamily = fonts.sans
                    )
                    val sub = if (ui.view == "notes") {
                        arCountNotes(appState.notes.size)
                    } else {
                        val t = appState.tasks.size
                        val d = appState.tasks.count { it.done }
                        if (t > 0) "$d من $t مكتملة" else "لا مهام بعد"
                    }
                    MText(
                        text = sub, color = colors.inkSoft, fontSize = 11.5.sp,
                        fontFamily = fonts.sans, modifier = Modifier.padding(top = 3.dp)
                    )
                }
                IconBtn(icon = "dots", tint = colors.ink, onClick = { ui.drawerOpen = true })
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.lineSoft)
            )

            // ---- المحتوى + البحث الزجاجي العائم ----
            Box(Modifier.weight(1f)) {
                // القوائم تُسجَّل كخلفية يلتقطها الزجاج (BackdropHost)
                BackdropHost(backdrop, Modifier.fillMaxSize()) {
                    if (ui.view == "notes") NotesContent(ui) else TasksContent(ui)
                }

                // شريط البحث الزجاجي — يطفو فوق القائمة وينكسر خلفه التمرير
                GlassSurface(
                    radius = 24.dp,
                    fallbackColor = GLASS_FALLBACK_SEARCH,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MidadIcon("search", 20.dp, colors.inkSoft)
                        androidx.compose.foundation.text.BasicTextField(
                            value = ui.query,
                            onValueChange = { ui.query = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = colors.ink, fontSize = 14.sp, fontFamily = fonts.sans
                            ),
                            cursorBrush = Brush.verticalGradient(listOf(colors.acc, colors.acc)),
                            modifier = Modifier.weight(1f).padding(start = 10.dp, end = 6.dp),
                            decorationBox = { inner ->
                                if (ui.query.isEmpty()) {
                                    Box {
                                        MText(
                                            "ابحث: العنوان أولًا ثم النص",
                                            color = colors.inkFaint, fontSize = 14.sp,
                                            fontFamily = fonts.sans
                                        )
                                        inner()
                                    }
                                } else inner()
                            }
                        )
                        if (ui.query.isNotEmpty()) {
                            IconBtn(
                                icon = "x", tint = colors.inkSoft, sizeDp = 28.dp, iconSize = 15.dp,
                                onClick = { ui.query = "" }
                            )
                        }
                    }
                }
            }
        }

        // ---- عناصر عائمة سفلية ----
        if (!ui.selMode) {
            FabButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 92.dp),
                onClick = {
                    if (ui.view == "tasks") {
                        ui.taskCardOpen = true
                        store.vibrate()
                    } else {
                        val note = store.newNote()
                        ui.morphOrigin = null // من موضع الزر نفسه
                        ui.editorNoteId = note.id
                        store.initHistory(note.id)
                    }
                }
            )
            BottomNav(ui, Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
        } else {
            SelBar(ui, Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
        }
    }
    }
}

private fun arCountNotes(n: Int): String = when (n) {
    0 -> "لا ملاحظات"
    1 -> "ملاحظة واحدة"
    2 -> "ملاحظتان"
    else -> "$n ملاحظات"
}

/* ---------- زر الإضافة العنبري ---------- */
@Composable
private fun FabButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalColors.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, JELLY, label = "fab")
    Box(
        modifier
            .size(56.dp)
            .scale(scale)
            .shadow(28.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(colors.acc)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try { awaitRelease() } finally { pressed = false; onClick() }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        MidadIcon("plus", 24.dp, colors.accInk)
    }
}

/* ---------- الشريط السفلي الزجاجي مع الكبسولة العنبري ---------- */
@Composable
private fun BottomNav(ui: MidadUi, modifier: Modifier = Modifier) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    var switching by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.width(320.dp).height(58.dp)) {
        val w = maxWidth
        val pillW = w / 2 - 8.dp
        val shiftTarget = if (ui.view == "notes") 0.dp else (w - 12.dp - pillW)
        val shift by animateFloatAsState(shiftTarget.value, JELLY, label = "pill")

        GlassSurface(radius = 29.dp, modifier = Modifier.fillMaxSize()) {
            // خلفية داكنة أثناء التبديل (كما في الويب)
            if (switching) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(29.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }
            // الكبسولة المنزلقة
            Box(
                Modifier
                    .offset(x = shift.dp)
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .width(pillW)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(
                        if (switching) androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.10f))
                        else Brush.horizontalGradient(listOf(colors.acc, colors.acc))
                    )
                    .then(
                        if (switching) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.88f), RoundedCornerShape(23.dp))
                        else Modifier
                    )
            )
            Row(Modifier.fillMaxSize().padding(6.dp)) {
                NavBtn("الملاحظات", "note", ui.view == "notes", switching, fonts, colors, Modifier.weight(1f)) {
                    if (ui.view != "notes") { ui.view = "notes"; switching = true }
                }
                NavBtn("المهام", "tasks", ui.view == "tasks", switching, fonts, colors, Modifier.weight(1f)) {
                    if (ui.view != "tasks") { ui.view = "tasks"; switching = true }
                }
            }
        }
    }
    LaunchedEffect(switching) {
        if (switching) { delay(340); switching = false }
    }
}

@Composable
private fun NavBtn(
    label: String, icon: String, active: Boolean, switching: Boolean,
    fonts: MidadFonts, colors: MidadColors, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(23.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MidadIcon(icon, 16.dp, if (active && !switching) colors.accInk else colors.inkSoft)
        MText(
            text = label,
            color = if (active && !switching) colors.accInk
                    else if (switching) Color.White.copy(alpha = 0.9f) else colors.inkSoft,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = fonts.sans,
            modifier = Modifier.padding(start = 7.dp)
        )
    }
}

/* ---------- شريط التحديد ---------- */
@Composable
private fun SelBar(ui: MidadUi, modifier: Modifier = Modifier) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val count = ui.selNotes.size + ui.selTasks.size
    GlassSurface(radius = 29.dp, modifier = modifier.width(340.dp).height(58.dp)) {
        Row(
            Modifier.fillMaxSize().padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(icon = "x", tint = colors.ink, onClick = { ui.exitSelection() })
            MText(
                text = when (count) {
                    0 -> "لم تُحدد عناصر"
                    1 -> "عنصر واحد محدد"
                    2 -> "عنصران محددان"
                    else -> "$count عناصر محددة"
                },
                color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = fonts.sans,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            IconBtn(icon = "selectAll", tint = colors.ink, onClick = { ui.selectAll() })
            var pressedDel by remember { mutableStateOf(false) }
            val delScale by animateFloatAsState(if (pressedDel) 0.92f else 1f, JELLY, label = "del")
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .scale(delScale)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.red)
                    .alpha(if (count == 0) 0.35f else 1f)
                    .pointerInput(count) {
                        if (count > 0) detectTapGestures(
                            onPress = {
                                pressedDel = true
                                try { awaitRelease() } finally {
                                    pressedDel = false
                                    ui.deleteSelected {
                                        ui.store.toast("حُذفت العناصر المحددة")
                                        ui.store.vibrate()
                                    }
                                }
                            }
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MidadIcon("trash", 16.dp, Color(0xFF1B0E0A))
                    MText(
                        "حذف", color = Color(0xFF1B0E0A), fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, fontFamily = fonts.sans,
                        modifier = Modifier.padding(start = 7.dp)
                    )
                }
            }
        }
    }
}

/* ---------- محتوى الملاحظات (شبكة متدرجة) ---------- */
@Composable
private fun NotesContent(ui: MidadUi) {
    val store = ui.store
    val notes = remember(ui.query, store.state.notes.size, store.state.notes.hashCode()) {
        val q = ui.query.trim().lowercase()
        if (q.isEmpty()) store.state.notes.toList()
        else store.state.notes.filter { n ->
            n.title.lowercase().contains(q) ||
                n.blocks.any { b ->
                    (b is Block.Text && b.text.lowercase().contains(q)) ||
                        (b is Block.Code && (b.code + " " + b.title).lowercase().contains(q))
                }
        }
    }

    if (notes.isEmpty()) {
        EmptyState(
            icon = if (ui.query.isEmpty()) "note" else "search",
            title = if (ui.query.isEmpty()) "الدفتر فارغ" else "لا نتائج مطابقة",
            sub = if (ui.query.isEmpty()) "اضغط زر الإضافة لبدء أول ملاحظة" else "جرّب كلمة أخرى"
        )
        return
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 70.dp, bottom = 132.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp
    ) {
        items(notes, key = { it.id }) { note ->
            NoteCard(
                note = note,
                selected = ui.selNotes.contains(note.id),
                selMode = ui.selMode,
                onClick = { bounds ->
                    if (ui.selMode) ui.toggleNoteSel(note.id)
                    else {
                        ui.morphOrigin = bounds
                        ui.editorNoteId = note.id
                        store.initHistory(note.id)
                        store.vibrate()
                    }
                },
                onLongPress = {
                    ui.selMode = true
                    ui.toggleNoteSel(note.id)
                    store.vibrate()
                }
            )
        }
    }
}

/* ---------- بطاقة ملاحظة ---------- */
@Composable
fun NoteCard(
    note: Note,
    selected: Boolean,
    selMode: Boolean,
    onClick: (Rect) -> Unit,
    onLongPress: () -> Unit
) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val store = (LocalStore.current)
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(if (pressed) 0.965f else 1f, tween(180), label = "card")
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(340)) }

    val firstText = note.blocks.firstOrNull { it is Block.Text && it.text.isNotBlank() } as? Block.Text
    val img = note.blocks.firstOrNull { it is Block.Image } as? Block.Image
    val code = note.blocks.firstOrNull { it is Block.Code } as? Block.Code
    val todo = note.blocks.firstOrNull { it is Block.Todo } as? Block.Todo
    val tags = mutableListOf<Pair<String, String>>()
    if (code != null) tags.add("code" to "Code")
    if (img != null) tags.add("image" to "Image")
    if (todo != null) tags.add("task" to "Task")

    var bounds by remember { mutableStateOf(Rect.Zero) }

    Column(
        Modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .graphicsLayerCard(appear.value, pressScale)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .border(
                1.dp,
                if (selMode && selected) colors.acc else colors.line,
                RoundedCornerShape(18.dp)
            )
            .pointerInput(selMode) {
                detectTapGestures(
                    onTap = { onClick(bounds) },
                    onLongPress = { onLongPress() },
                    onPress = {
                        pressed = true
                        try { awaitRelease() } finally { pressed = false }
                    }
                )
            }
            .padding(14.dp)
    ) {
        // نقطة التحديد
        if (selMode) {
            Box(
                Modifier
                    .align(Alignment.End)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.acc else Color(0x990C0B09))
                    .border(1.5.dp, if (selected) colors.acc else colors.ink.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) MidadIcon("check", 13.dp, colors.accInk)
            }
            Spacer(Modifier.height(8.dp))
        }

        if (note.title.isNotBlank()) {
            MText(
                text = note.title,
                color = colors.ink, fontSize = 16.5.sp, fontWeight = FontWeight.Bold,
                lineHeight = 23.sp, fontFamily = fonts.sans,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        img?.let {
            MidadImage(store, it.src, Modifier.fillMaxWidth().heightIn(max = 190.dp).clip(RoundedCornerShape(11.dp)))
            Spacer(Modifier.height(10.dp))
        }

        firstText?.let {
            MText(
                text = it.text,
                color = colors.inkSoft, fontSize = 12.5.sp, lineHeight = 22.sp,
                fontFamily = fonts.sans, maxLines = 5, overflow = TextOverflow.Ellipsis
            )
        }

        code?.let {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.codeBg)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                MText(
                    text = it.code.lineSequence().take(4).joinToString("\n"),
                    color = Color(0xFFB9AF97), fontSize = 10.sp, lineHeight = 17.sp,
                    fontFamily = fonts.mono, maxLines = 4, overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (tags.isNotEmpty()) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((kind, label) in tags) {
                    val (bg, fg) = when (kind) {
                        "code" -> Color(0xFF3A2E14) to colors.acc
                        "image" -> Color(0xFF26301F) to colors.ok
                        else -> colors.accDim to colors.acc
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(99.dp)).background(bg)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        MText(label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, fontFamily = fonts.mono)
                    }
                }
            }
        }

        MText(
            text = store.fmtDate(note.updatedAt),
            color = colors.inkFaint, fontSize = 10.5.sp, fontFamily = fonts.sans,
            modifier = Modifier.padding(top = 9.dp)
        )
    }
}

private fun Modifier.graphicsLayerCard(appear: Float, press: Float): Modifier =
    this.graphicsLayer {
        alpha = appear
        translationY = (1f - appear) * 14.dp.toPx()
        val base = 0.97f + 0.03f * appear
        scaleX = base * press
        scaleY = base * press
    }

