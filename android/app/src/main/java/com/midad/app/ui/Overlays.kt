package com.midad.app.ui

import android.media.MediaRecorder
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midad.app.glass.GlassSurface
import com.midad.app.store.AppStore
import com.midad.app.store.Block
import com.midad.app.store.Note
import com.midad.app.store.TimerState
import kotlinx.coroutines.delay
import java.io.File

/*
 * النوافذ: المؤقت، مهمة جديدة، تسجيل، الدرج، تأكيد الحذف، التنبيهات، قائمة الكتلة
 */

/** حاجب شفاف للنوافذ */
@Composable
fun Scrim(alpha: Float = 0.52f, onDismiss: () -> Unit) {
    val colors = LocalColors.current
    var visible by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { visible = 1f }
    Box(
        Modifier
            .fillMaxSize()
            .alpha(visible * alpha)
            .background(Color(0x050403).copy(alpha = visible * alpha))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
    )
}

/* ---------- نافذة اختيار مدة المؤقت (عجلات) ---------- */
@Composable
fun TimePopOverlay(ui: MidadUi, onDismiss: () -> Unit, onSet: (Long) -> Unit) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    var h by remember { mutableStateOf(0) }
    var m by remember { mutableStateOf(25) }
    var s by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        Scrim { onDismiss() }
        GlassSurface(
            radius = 24.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                MText(
                    "مدة المؤقت", color = colors.ink, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, fontFamily = fonts.sans,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
                Row(Modifier.padding(top = 8.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Wheel("ساعة", 24, h, Modifier.weight(1f)) { h = it; if (h + m + s == 0) m = 1 }
                    Wheel("دقيقة", 60, m, Modifier.weight(1f)) { m = it; if (h + m + s == 0) s = 1 }
                    Wheel("ثانية", 60, s, Modifier.weight(1f)) { s = it; if (h + m + s == 0) m = 1 }
                }
                Box(Modifier.fillMaxWidth()) {
                    PrimaryButton(
                        text = "تعيين المدة",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val secs = h * 3600L + m * 60L + s
                            if (secs > 0) onSet(secs)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Wheel(label: String, count: Int, sel: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit = {}) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = sel)
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
            MText(label, color = colors.inkSoft, fontSize = 11.sp, fontFamily = fonts.sans)
        }
        Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(12.dp)).background(colors.well)) {
            LazyColumn(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(count + 1) { i ->
                    Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                        val selected = i == sel
                        MText(
                            String.format("%02d", i),
                            color = if (selected) colors.acc else colors.inkFaint,
                            fontSize = if (selected) 22.sp else 19.sp,
                            fontWeight = if (selected) FontWeight(500) else FontWeight.Normal,
                            fontFamily = fonts.mono
                        )
                    }
                }
            }
            // الشريط المركزي
            Column(Modifier.align(Alignment.Center)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x66F2C14E)))
                Spacer(Modifier.height(42.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x66F2C14E)))
            }
        }
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val idx = listState.firstVisibleItemIndex +
                    if (listState.firstVisibleItemScrollOffset > 22) 1 else 0
            onChange(idx.coerceIn(0, count))
        }
    }
    LaunchedEffect(Unit) {
        onChange(sel.coerceIn(0, count))
    }
}

/* ---------- بطاقة مهمة جديدة ---------- */
@Composable
fun TaskCardOverlay(ui: MidadUi, onDismiss: () -> Unit, onPickImage: () -> Unit) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val store = ui.store
    var text by remember { mutableStateOf("") }
    var useTimer by remember { mutableStateOf(false) }
    var shake by remember { mutableStateOf(false) }
    val dur = ui.pendingTaskTimer

    LaunchedEffect(shake) {
        if (shake) { delay(400); shake = false }
    }
    val shakeX by androidx.compose.animation.core.animateFloatAsState(
        if (shake) 1f else 0f, tween(400), label = "sh"
    )

    Box(Modifier.fillMaxSize()) {
        Scrim { onDismiss() }
        GlassSurface(
            radius = 24.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth()
                .width(400.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                MText("مهمة جديدة", color = colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = fonts.sans)
                Box(
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.well)
                        .border(1.dp, if (shake) colors.red else colors.line, RoundedCornerShape(12.dp))
                        .scale(shakeX.takeIf { it > 0f } ?: 1f)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(color = colors.ink, fontSize = 14.5.sp, fontFamily = fonts.sans, textDirection = TextDirection.Rtl),
                        cursorBrush = Brush.verticalGradient(listOf(colors.acc, colors.acc)),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box {
                                if (text.isEmpty()) {
                                    MText("ما الذي تريد إنجازه؟", color = colors.inkFaint, fontSize = 14.5.sp, fontFamily = fonts.sans)
                                }
                                inner()
                            }
                        }
                    )
                }
                // مرفق صورة
                ui.pendingTaskImage?.let { ref ->
                    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        MidadImage(store, ref, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                        Spacer(Modifier.width(10.dp))
                        IconBtn("x", colors.ink, sizeDp = 24.dp, iconSize = 13.dp, onClick = { ui.pendingTaskImage = null })
                    }
                }
                // صف المؤقت
                Row(
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        MText("مؤقت Chrono-Trigger", color = colors.ink, fontSize = 13.5.sp, fontWeight = FontWeight(600), fontFamily = fonts.sans)
                        MText("حلقة تقدم دائرية حول خانة الإنجاز", color = colors.inkSoft, fontSize = 11.sp, fontFamily = fonts.sans, modifier = Modifier.padding(top = 2.dp))
                    }
                    MSwitch(checked = useTimer, onToggle = {
                        useTimer = !useTimer
                        if (useTimer && ui.pendingTaskTimer == null) { ui.timePopTarget = "task"; ui.timePopOpen = true }
                    })
                }
                if (useTimer && dur != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.well)
                            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { ui.timePopTarget = "task"; ui.timePopOpen = true })
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MText(
                            store.fmtHMS(dur!!.duration),
                            color = colors.acc, fontSize = 13.5.sp, fontWeight = FontWeight(500), fontFamily = fonts.mono
                        )
                        Spacer(Modifier.weight(1f))
                        MText("تغيير", color = colors.inkSoft, fontSize = 12.sp, fontFamily = fonts.sans)
                    }
                }
                Row(Modifier.padding(top = 14.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF4A4335), RoundedCornerShape(12.dp))
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onPickImage() }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MidadIcon("image", 16.dp, colors.inkSoft)
                            MText("إرفاق صورة", color = colors.inkSoft, fontSize = 13.sp, fontFamily = fonts.sans, modifier = Modifier.padding(start = 9.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = "إضافة المهمة",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (text.isBlank()) { shake = true; return@PrimaryButton }
                        val timer = if (useTimer) (ui.pendingTaskTimer ?: TimerState(duration = 1500, remaining = 1500)) else null
                        store.addTask(text.trim(), ui.pendingTaskImage, timer)
                        ui.pendingTaskImage = null
                        ui.pendingTaskTimer = null
                        ui.taskCardOpen = false
                        store.toast("أُضيفت المهمة")
                        store.vibrate()
                    }
                )
            }
        }
    }
}

/* ---------- بطاقة التسجيل الصوتي ---------- */
@Composable
fun RecOverlay(
    ui: MidadUi,
    recorderHolder: RecorderHolder,
    onDismiss: () -> Unit
) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val store = ui.store
    val pulse = rememberInfiniteTransition(label = "rec")
    val ring by pulse.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "ring"
    )

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(15.dp)) {
            drawCircle(colors.red)
            drawCircle(
                colors.red.copy(alpha = (1f - ring) * 0.4f),
                radius = size.minDimension / 2 + ring * 15.dp.toPx(),
                style = Stroke(2.dp.toPx())
            )
        }
        MText(
            text = store.fmtHMS(recorderHolder.secs),
            color = colors.ink, fontSize = 37.sp, fontWeight = FontWeight(500),
            fontFamily = fonts.mono, modifier = Modifier.padding(top = 14.dp)
        )
        MText("جارٍ التسجيل… انطق بحرّية", color = colors.inkSoft, fontSize = 12.5.sp, fontFamily = fonts.sans, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn("x", colors.red, onClick = {
                recorderHolder.cancel()
                onDismiss()
            })
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(colors.acc)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                        val f = recorderHolder.stop()
                        if (f != null) {
                            ui.recTargetNote?.let { note ->
                                note.blocks.add(Block.Voice(url = "aud:${f.nameWithoutExtension}", duration = recorderHolder.secs))
                                store.touch(note)
                                store.pushHistory(note.id)
                            }
                            store.toast("أُضيف تسجيل صوتي (" + store.fmtHMS(recorderHolder.secs) + ")")
                        }
                        onDismiss()
                    }
                    .padding(horizontal = 26.dp, vertical = 11.dp)
            ) {
                MText("إيقاف وحفظ", color = colors.accInk, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = fonts.sans)
            }
        }
    }
}

class RecorderHolder(private val context: android.content.Context, private val store: AppStore) {
    var recorder: MediaRecorder? = null
    var file: File? = null
    var secs by mutableStateOf(0L)
    private var started = 0L

    fun start(target: File) {
        try {
            file = target
            val r: MediaRecorder = if (android.os.Build.VERSION.SDK_INT >= 31)
                @Suppress("DEPRECATION") MediaRecorder(context)
            else
                @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96000)
            r.setOutputFile(target.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            started = System.currentTimeMillis()
        } catch (e: Exception) {
            store.toast("تعذّر الوصول إلى الميكروفون")
            recorder = null
        }
    }

    fun stop(): File? {
        return try {
            recorder?.stop()
            secs = (System.currentTimeMillis() - started) / 1000
            recorder?.release()
            recorder = null
            file
        } catch (e: Exception) {
            recorder?.release(); recorder = null
            null
        }
    }

    fun cancel() {
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        file?.delete()
    }
}
