package com.midad.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midad.app.glass.GlassSurface
import com.midad.app.store.Task
import com.midad.app.store.TimerState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * شاشة المهام — بطاقة تقدم + قائمتا غير مكتمل/مكتمل + سحب إنجاز/حذف
 */

@Composable
fun TasksContent(ui: MidadUi) {
    val store = ui.store
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val appState = store.state
    val _dv = store.dataVersion
    val q = ui.query.trim().lowercase()
    val tasks = if (q.isEmpty()) appState.tasks else appState.tasks.filter {
        it.text.lowercase().contains(q) || it.note.lowercase().contains(q)
    }
    val undone = tasks.filter { !it.done }
    val done = tasks.filter { it.done }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 132.dp)
    ) {
        Spacer(Modifier.height(70.dp))

        // بطاقة التقدم الإجمالي
        val total = appState.tasks.size
        val dCount = appState.tasks.count { it.done }
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(colors.card)
                .border(1.dp, colors.line, RoundedCornerShape(15.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                MText("التقدم الإجمالي", color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight(600), fontFamily = fonts.sans)
                MText(
                    if (total > 0) "${dCount * 100 / total}%" else "0%",
                    color = colors.inkSoft, fontSize = 11.5.sp, fontFamily = fonts.mono
                )
            }
            val barAnim by animateFloatAsState(
                if (total > 0) dCount.toFloat() / total else 0f,
                tween(500), label = "bar"
            )
            Box(
                Modifier
                    .padding(top = 9.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.well)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(barAnim)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.acc)
                )
            }
        }

        if (tasks.isEmpty()) {
            EmptyState(
                icon = if (q.isEmpty()) "tasks" else "search",
                title = if (q.isEmpty()) "لا مهام بعد" else "لا نتائج مطابقة",
                sub = if (q.isEmpty()) "اضغط زر الإضافة لإنشاء مهمة" else ""
            )
            return@Column
        }

        if (undone.isNotEmpty()) {
            ListHead("غير مكتمل", undone.size)
            for (t in undone) {
                TaskCard(ui, t)
            }
        }
        if (done.isNotEmpty()) {
            ListHead("مكتمل", done.size)
            for (t in done) {
                TaskCard(ui, t)
            }
        }
    }
}

@Composable
private fun ListHead(label: String, count: Int) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    Row(
        Modifier
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MText(label, color = colors.ink, fontSize = 13.5.sp, fontWeight = FontWeight(600), fontFamily = fonts.sans)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(99.dp)).background(colors.card2)
                .padding(horizontal = 9.dp, vertical = 2.dp)
        ) {
            MText("$count", color = colors.inkSoft, fontSize = 11.sp, fontFamily = fonts.sans)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(colors.line))
    }
}

/* ---------- بطاقة مهمة مع سحب إنجاز/حذف ---------- */
@Composable
fun TaskCard(ui: MidadUi, task: Task) {
    val store = ui.store
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    val _dv2 = store.dataVersion
    val _tick = store.tickState
    var dragX by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf<String?>(null) } // "done" | "del"
    val leavingAnim by animateFloatAsState(if (leaving != null) 0f else 1f, tween(250), label = "lv")
    val maxDrag = with(density) { 110.dp.toPx() }

    LaunchedEffect(leaving) {
        if (leaving != null) {
            delay(260)
            if (leaving == "done") { task.done = true; store.saveSoon() }
            else if (leaving == "del") { store.deleteTask(task.id); store.toast("حُذفت المهمة") }
            store.vibrate()
        }
    }

    val zoneDone = dragX > maxDrag * 0.55f
    val zoneDel = dragX < -maxDrag * 0.55f

    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .alpha(leavingAnim)
            .onGloballyPositioned { }
    ) {
        // الطبقة الخلفية (مناطق الإنجاز/الحذف)
        Row(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        zoneDone -> Color(0x249BB77F)
                        zoneDel -> Color(0x24E06C55)
                        else -> Color.Transparent
                    }
                )
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val show = zoneDone || zoneDel
            MidadIcon(
                if (zoneDel) "trash" else "check", 20.dp,
                if (show) (if (zoneDone) colors.ok else colors.red) else Color.Transparent,
                Modifier.alpha(if (show) 1f else 0.35f)
            )
        }

        // البطاقة نفسها
        var pressed by remember { mutableStateOf(false) }
        Row(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(dragX.roundToInt(), 0) }
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.card)
                .border(
                    1.dp,
                    if (ui.selMode && ui.selTasks.contains(task.id)) colors.acc else colors.line,
                    RoundedCornerShape(16.dp)
                )
                .pointerInput(ui.selMode) {
                    if (ui.selMode) {
                        detectTapGestures(onTap = { ui.toggleTaskSel(task.id) })
                    } else {
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                when {
                                    dragX > maxDrag * 0.55f -> leaving = "done"
                                    dragX < -maxDrag * 0.55f -> leaving = "del"
                                    else -> dragX = 0f
                                }
                            },
                            onDragCancel = { dragging = false; dragX = 0f }
                        ) { change, amount ->
                            change.consume()
                            dragX = (dragX + amount).coerceIn(-maxDrag * 1.4f, maxDrag * 1.4f)
                        }
                    }
                }
                .pointerInput(ui.selMode, task.done) {
                    if (!ui.selMode) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                try { awaitRelease() } finally { pressed = false }
                            }
                        )
                    }
                }
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            // حلقة المؤقت أو خانة الإنجاز
            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                val timer = task.timer
                if (timer != null && !task.done) {
                    Canvas(Modifier.size(38.dp)) {
                        val stroke = 2.4.dp.toPx()
                        drawArc(
                            color = colors.card2, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                        val rem = timer.remainingNow().toFloat()
                        val frac = if (timer.duration > 0) rem / timer.duration else 0f
                        drawArc(
                            color = colors.acc, startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                // خانة الإنجاز
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (task.done) colors.ok else colors.bg)
                        .border(1.5.dp, if (task.done) colors.ok else Color(0xFF4A4335), CircleShape)
                        .pointerInput(task.id, ui.selMode) {
                            if (!ui.selMode) detectTapGestures(onTap = {
                                task.done = !task.done
                                store.saveSoon(); store.vibrate()
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (task.done) MidadIcon("check", 12.dp, Color(0xFF141710))
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                MText(
                    text = task.text,
                    color = if (task.done) colors.inkFaint else colors.ink,
                    fontSize = 14.sp, fontWeight = FontWeight(500), lineHeight = 21.sp,
                    fontFamily = fonts.sans,
                    style = if (task.done) androidx.compose.ui.text.TextStyle(
                        textDecoration = TextDecoration.LineThrough
                    ) else null
                )
                if (task.timer != null) {
                    Row(
                        Modifier.padding(top = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MText(
                            text = store.fmtHMS(task.timer!!.remainingNow()),
                            color = colors.acc, fontSize = 11.sp, fontWeight = FontWeight(500),
                            fontFamily = fonts.mono
                        )
                    }
                }
            }

            // زر تشغيل المؤقت
            if (task.timer != null && !task.done) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.card2)
                        .border(1.dp, colors.line, CircleShape)
                        .pointerInput(task.id) {
                            detectTapGestures(onTap = {
                                val t = task.timer!!
                                if (t.running) t.pause() else t.start()
                                store.bumpData()
                                store.saveSoon(); store.vibrate()
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    MidadIcon(
                        if (task.timer!!.running) "pause" else "play", 16.dp, colors.inkSoft
                    )
                }
            }
        }
    }
}
