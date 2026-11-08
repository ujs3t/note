package com.midad.app.ui

import android.media.MediaPlayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midad.app.glass.GlassSurface
import com.midad.app.store.AppStore
import com.midad.app.store.Block
import com.midad.app.store.Note
import com.midad.app.store.TimerState
import com.midad.app.store.TodoItem
import kotlinx.coroutines.delay

/* ============================================================
   كتل المحرر — نفس أنماط b-text/b-todo/codeblock/b-timer/b-image/b-voice
   ============================================================ */

@Composable
fun BlockView(
    store: AppStore,
    note: Note,
    block: Block,
    index: Int,
    ui: MidadUi,
    scroll: androidx.compose.foundation.ScrollState
) {
    var blockTopY by remember { mutableStateOf(0f) }
    var blockH by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .onGloballyPositioned {
                blockH = it.size.height.toFloat()
                blockTopY = it.positionInRoot().y - ui.editorContentY
                ui.blockMetrics[block.id] = blockTopY to blockH
            }
    ) {
        when (block) {
            is Block.Text -> TextBlock(store, note, block, index, ui)
            is Block.Todo -> TodoBlock(store, note, block, ui)
            is Block.Code -> CodeBlock(store, note, block, ui)
            is Block.Timer -> TimerBlock(store, note, block.timer, ui)
            is Block.Image -> ImageBlock(store, note, block, ui)
            is Block.Voice -> VoiceBlock(store, note, block, ui)
        }
    }
}

/** قائمة الكتلة عند الضغط المطوّل — تفتح كما في الويب */
private fun Modifier.blockLongPress(ui: MidadUi, note: Note, index: Int): Modifier =
    this.pointerInput(note.id, index) {
        detectTapGestures(onLongPress = {
            ui.blockMenu = BlockMenuState(note.id, index, androidx.compose.ui.geometry.Rect.Zero)
            ui.store.vibrate()
        })
    }

/* ---------- كتلة نص ---------- */
@Composable
private fun TextBlock(store: AppStore, note: Note, block: Block.Text, index: Int, ui: MidadUi) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember(block.id) { FocusRequester() }
    var textState by remember(block.id, ui.textEpoch) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(block.text))
    }

    // تركيز معلّق (بعد تقسيم/دمج)
    LaunchedEffect(block.id) {
        if (ui.pendingFocus == block.id) {
            focusRequester.requestFocus()
            ui.pendingFocus = null
        }
    }

    val styleSpec = when (block.style) {
        "h1" -> Triple(22.sp, FontWeight.Bold, 34.sp)
        "h2" -> Triple(18.5.sp, FontWeight.Bold, 29.sp)
        "quote" -> Triple(15.sp, FontWeight.Normal, 30.sp)
        else -> Triple(15.sp, FontWeight.Normal, 30.sp)
    }

    fun push() {
        store.touch(note)
        store.pushHistorySoon(note.id)
    }

    /** معالجة Enter (تقسيم) وحذف الفراغ (دمج/حذف) — مثل سلوك الويب */
    fun handleKey(ev: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val code = ev.nativeKeyEvent.keyCode
        val isEnter = code == android.view.KeyEvent.KEYCODE_ENTER ||
                code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
        val isDel = code == android.view.KeyEvent.KEYCODE_DEL
        if (isEnter) {
            val sel = textState.selection
            val txt = textState.text
            val before = txt.substring(0, sel.min)
            val after = txt.substring(sel.max)
            block.text = before
            val nb = Block.Text(block.style, after)
            note.blocks.add(note.blocks.indexOf(block) + 1, nb)
            ui.textEpoch++
            ui.pendingFocus = nb.id
            push()
            return true
        }
        if (isDel && textState.text.isEmpty()) {
            val i = note.blocks.indexOf(block)
            if (i > 0) {
                val prev = note.blocks[i - 1]
                if (prev is Block.Text) {
                    prev.text += block.text
                    note.blocks.removeAt(i)
                    ui.textEpoch++
                    ui.pendingFocus = prev.id
                    push()
                    return true
                }
            }
            note.blocks.remove(block)
            push()
            return true
        }
        return false
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0x0AF2C14E) else Color.Transparent)
    ) {
        if (block.style == "quote") {
            // خط الاقتباس على اليمين (RTL مثل الويب)
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(3.dp)
                    .height(22.dp)
                    .background(colors.acc)
            )
        }

        // زر حذف الكتلة عند التركيز
        if (focused) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset((-26).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(colors.card2)
                    .border(1.dp, colors.line, CircleShape)
                    .alpha(0.75f)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            note.blocks.remove(block)
                            store.pushHistory(note.id)
                            store.touch(note)
                            store.vibrate()
                        })
                    },
                contentAlignment = Alignment.Center
            ) {
                MidadIcon("x", 13.dp, colors.inkSoft)
            }
        }

        BasicTextField(
            value = textState,
            onValueChange = { v ->
                textState = v
                block.text = v.text
                push()
            },
            textStyle = TextStyle(
                color = if (block.style == "quote") colors.inkSoft else colors.ink,
                fontSize = styleSpec.first,
                fontWeight = styleSpec.second,
                lineHeight = styleSpec.third,
                fontFamily = fonts.sans,
                textDirection = TextDirection.Rtl
            ),
            cursorBrush = Brush.verticalGradient(listOf(colors.acc, colors.acc)),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { ev -> handleKey(ev) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            decorationBox = { inner ->
                Box {
                    if (textState.text.isEmpty()) {
                        MText(
                            "اكتب شيئًا…", color = colors.inkFaint,
                            fontSize = styleSpec.first, fontFamily = fonts.sans
                        )
                    }
                    inner()
                }
            }
        )
    }
}

/* ---------- كتلة مهام ---------- */
@Composable
private fun TodoBlock(store: AppStore, note: Note, block: Block.Todo, ui: MidadUi) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .blockLongPress(ui, note, note.blocks.indexOf(block))
    ) {
        val items = block.items
        var i = 0
        while (i < items.size) {
            val idx = i
            val item = items[idx]
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (item.done) colors.ok else colors.bg)
                        .border(1.5.dp, if (item.done) colors.ok else Color(0xFF4A4335), CircleShape)
                        .pointerInput(item) {
                            detectTapGestures(onTap = {
                                item.done = !item.done
                                store.bumpData()
                                store.touch(note)
                                store.pushHistorySoon(note.id)
                                store.vibrate()
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (item.done) MidadIcon("check", 12.dp, Color(0xFF141710))
                }
                var itemText by remember(item, idx) { mutableStateOf(item.text) }
                BasicTextField(
                    value = itemText,
                    onValueChange = {
                        itemText = it
                        item.text = it
                        store.touch(note)
                        store.pushHistorySoon(note.id)
                    },
                    textStyle = TextStyle(
                        color = if (item.done) colors.inkFaint else colors.ink,
                        fontSize = 14.sp, fontFamily = fonts.sans,
                        textDecoration = if (item.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                        textDirection = TextDirection.Rtl
                    ),
                    cursorBrush = Brush.verticalGradient(listOf(colors.acc, colors.acc)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                )
            }
            i++
        }
        // إضافة عنصر
        var addText by remember { mutableStateOf("") }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .border(0.5.dp, colors.line, RoundedCornerShape(1.dp)).let { it }
                .padding(top = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MidadIcon("plus", 16.dp, colors.inkSoft)
            BasicTextField(
                value = addText,
                onValueChange = { addText = it },
                textStyle = TextStyle(color = colors.ink, fontSize = 13.sp, fontFamily = fonts.sans, textDirection = TextDirection.Rtl),
                cursorBrush = Brush.verticalGradient(listOf(colors.acc, colors.acc)),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                    if (addText.isNotBlank()) {
                        items.add(TodoItem(false, addText.trim()))
                        addText = ""
                        store.touch(note)
                        store.pushHistory(note.id)
                    }
                }),
                decorationBox = { inner ->
                    Box(Modifier.padding(start = 10.dp)) {
                        if (addText.isEmpty()) {
                            MText("أضف مهمة…", color = colors.inkFaint, fontSize = 13.sp, fontFamily = fonts.sans)
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/* ---------- كتلة سكربت ---------- */
@Composable
private fun CodeBlock(store: AppStore, note: Note, block: Block.Code, ui: MidadUi) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.codeBg)
            .blockLongPress(ui, note, note.blocks.indexOf(block))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = block.title,
                onValueChange = {
                    block.title = it
                    store.touch(note)
                    store.pushHistorySoon(note.id)
                },
                textStyle = TextStyle(color = Color(0xFFD8CFB9), fontSize = 12.sp, fontFamily = fonts.sans, textDirection = TextDirection.Rtl),
                cursorBrush = Brush.verticalGradient(listOf(colors.acc, colors.acc)),
                decorationBox = { inner ->
                    Box(Modifier.weight(1f)) {
                        if (block.title.isEmpty()) {
                            MText("اسم السكربت", color = Color(0xFF6E675A), fontSize = 12.sp, fontFamily = fonts.sans)
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            var pressedCopy by remember { mutableStateOf(false) }
            val copyScale by animateFloatAsState(if (pressedCopy) 0.92f else 1f, JELLY, label = "cp")
            Row(
                Modifier
                    .scale(copyScale)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.codeLine)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressedCopy = true
                                try { awaitRelease() } finally {
                                    pressedCopy = false
                                    clipboard.setText(AnnotatedString(block.code))
                                    store.toast("نُسخ السكربت")
                                }
                            }
                        )
                    }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MidadIcon("copy", 13.dp, Color(0xFFBCB29A))
                MText("نسخ", color = Color(0xFFBCB29A), fontSize = 11.sp, fontFamily = fonts.sans, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(140.dp)) {
            BasicTextField(
                value = block.code,
                onValueChange = {
                    block.code = it
                    store.touch(note)
                    store.pushHistorySoon(note.id)
                },
                textStyle = TextStyle(
                    color = colors.codeInk, fontSize = 12.5.sp, lineHeight = 22.sp,
                    fontFamily = fonts.mono, textDirection = TextDirection.Ltr
                ),
                cursorBrush = Brush.verticalGradient(listOf(colors.acc, colors.acc)),
                modifier = Modifier
                    .matchParentSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            )
        }
    }
}

/* تلوين خفيف — يُستخدم لاحقًا في عرض السكربت للقراءة فقط */
private fun highlightCode(code: String): AnnotatedString = buildAnnotatedString { append(code) }

/* ---------- كتلة مؤقت ---------- */
@Composable
fun TimerBlock(store: AppStore, note: Note?, timer: TimerState, ui: MidadUi) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val _tick = store.tickState // يُعيد التركيب كل ثانية
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(
                if (note != null) Modifier.blockLongPress(ui, note, note.blocks.indexOfFirst { it is Block.Timer && it.timer == timer }.coerceAtLeast(0))
                else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val remaining = timer.remainingNow()
        MText(
            text = store.fmtHMS(remaining),
            color = colors.acc, fontSize = 22.sp, fontWeight = FontWeight(500),
            fontFamily = fonts.mono
        )
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.well)
        ) {
            val frac = if (timer.duration > 0) remaining.toFloat() / timer.duration else 0f
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.acc)
            )
        }
        TimerBtn(if (timer.running) "pause" else "play") {
            if (timer.running) timer.pause() else timer.start()
            store.bumpData()
            store.saveSoon(); store.vibrate()
        }
        Spacer(Modifier.width(6.dp))
        TimerBtn("reset") {
            timer.pause()
            timer.remaining = timer.duration
            timer.remainingAtStart = timer.duration
            store.bumpData()
            store.saveSoon(); store.vibrate()
        }
    }
}

@Composable
fun TimerBtn(icon: String, onClick: () -> Unit) {
    val colors = LocalColors.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, JELLY, label = "tb")
    Box(
        Modifier
            .size(32.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(colors.card2)
            .border(1.dp, colors.line, CircleShape)
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
        MidadIcon(icon, 15.dp, colors.inkSoft)
    }
}

/* ---------- كتلة صورة ---------- */
@Composable
private fun ImageBlock(store: AppStore, note: Note, block: Block.Image, ui: MidadUi) {
    Box(
        Modifier
            .fillMaxWidth()
            .blockLongPress(ui, note, note.blocks.indexOf(block))
    ) {
        MidadImage(
            store, block.src,
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(14.dp))
        )
    }
}

/* ---------- كتلة تسجيل صوتي ---------- */
@Composable
private fun VoiceBlock(store: AppStore, note: Note, block: Block.Voice, ui: MidadUi) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val file = store.audioFileFor(block.url.removePrefix("aud:"))
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .blockLongPress(ui, note, note.blocks.indexOf(block))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MidadIcon("mic", 15.dp, colors.inkSoft)
            MText("تسجيل صوتي", color = colors.inkSoft, fontSize = 12.sp, fontFamily = fonts.sans, modifier = Modifier.padding(start = 8.dp))
            MText(
                text = store.fmtHMS(block.duration),
                color = colors.acc, fontSize = 12.sp, fontWeight = FontWeight(500),
                fontFamily = fonts.mono, modifier = Modifier.weight(1f).padding(start = 8.dp),
                maxLines = 1
            )
            TimerBtn(if (playing) "pause" else "play") {
                val p = player
                if (p != null && playing) {
                    p.pause(); playing = false
                } else {
                    player?.release()
                    val mp = MediaPlayer()
                    try {
                        mp.setDataSource(file.absolutePath)
                        mp.prepare()
                        mp.setOnCompletionListener { playing = false }
                        mp.start()
                        player = mp
                        playing = true
                    } catch (e: Exception) {
                        store.toast("تعذّر تشغيل التسجيل")
                    }
                }
            }
        }
    }
}
