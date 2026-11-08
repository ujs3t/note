package com.midad.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midad.app.glass.BackdropHost
import com.midad.app.glass.LocalBackdrop
import com.midad.app.glass.rememberBackdropSource
import com.midad.app.store.AppStore
import com.midad.app.store.Block
import com.midad.app.store.Note

/*
 * شاشة المحرر — الرأس الزجاجي + العنوان + الكتل + الخريطة المصغرة + شريط الأدوات
 * ملف الواجهة: التركيب فقط؛ الكتل في EditorBlocks.kt
 */

@Composable
fun EditorScreen(store: AppStore, note: Note, ui: MidadUi, chromeAlpha: () -> Float) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    val scroll = rememberScrollState()
    val _dv = store.dataVersion // إعادة تركيب عند إضافة/حذف كتل

    // مصدر الخلفية المشترك للشاشة — الرأس والشريط الزجاجي يلتقطان الكتل
    val backdrop = rememberBackdropSource()
    androidx.compose.runtime.CompositionLocalProvider(LocalBackdrop provides backdrop) {

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(Modifier.fillMaxSize()) {
            // ---------- الرأس الزجاجي ----------
            Row(
                Modifier
                    .fillMaxWidth()
                    .alpha(chromeAlpha())
                    .statusBarsPadding()
                    .background(colors.bg)
                    .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton("back", colors.ink, store = store) { ui.closeEditorMorph() }
                Spacer(Modifier.width(2.dp))
                GlassIconButton("undo", colors.ink, enabled = store.canUndo(note.id), store = store) {
                    store.undo(note.id)
                }
                Spacer(Modifier.width(2.dp))
                GlassIconButton("redo", colors.ink, enabled = store.canRedo(note.id), store = store) {
                    store.redo(note.id)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    MText(
                        text = store.fmtDate(note.updatedAt),
                        color = colors.inkSoft, fontSize = 11.sp, fontFamily = fonts.sans
                    )
                }
                GlassIconButton("trash", colors.red, store = store) { ui.confirmDeleteNote(note) }
            }

            // ---------- العنوان ----------
            TitleField(store, note, ui, chromeAlpha)

            // ---------- الكتل (تُسجَّل كخلفية للزجاج) ----------
            Box(Modifier.weight(1f)) {
                BackdropHost(
                    backdrop,
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { ui.editorViewport = it.height.toFloat() }
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .insetsBottom()
                            .padding(start = 24.dp, end = 42.dp)
                            .padding(top = 6.dp, bottom = 150.dp)
                            .onGloballyPositioned { ui.editorContentY = it.positionInRoot().y }
                    ) {
                        val blocks = note.blocks
                        var i = 0
                        while (i < blocks.size) {
                            val idx = i
                            val block = blocks[idx]
                            Box(Modifier.padding(bottom = 4.dp)) {
                                BlockView(store, note, block, idx, ui, scroll)
                            }
                            i++
                        }
                        // منطقة فارغة أسفل الكتل — نقرة تضيف كتلة نص
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .pointerInput(note.id, blocks.size) {
                                    detectTapGestures(onTap = {
                                        blocks.add(Block.Text())
                                        store.touch(note)
                                        store.pushHistorySoon(note.id)
                                    })
                                }
                        )
                    }
                }

                // الخريطة المصغّرة على اليمين (فيزيائيًا)
                if (store.state.settings.minimap) {
                    Minimap(
                        store, note, ui, scroll,
                        Modifier
                            .align(androidx.compose.ui.AbsoluteAlignment.CenterRight)
                            .padding(end = 4.dp, top = 40.dp, bottom = 110.dp)
                            .fillMaxHeight()
                            .alpha(chromeAlpha())
                    )
                }

                // شريط الأدوات الزجاجي فوق الكيبورد والشريط السفلي
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .insetsBottom()
                        .alpha(chromeAlpha())
                ) {
                    EditorToolbar(
                        store, note, ui,
                        Modifier.padding(bottom = 14.dp)
                    )
                    // نافذة التنسيق فوق الشريط
                    val aaIdx = ui.aaPopFor
                    if (aaIdx != null) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 86.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            AaPop(store, note, ui)
                        }
                    }
                }
            }
        }
    }

    // إغلاق مزوّد الخلفية
    }
}

/** حشوة أسفل: الكيبورد أو شريط التنقل — أيهما أعلى */
@Composable
fun Modifier.insetsBottom(): Modifier {
    val insets = WindowInsets.ime.union(WindowInsets.navigationBars)
    return this.windowInsetsPadding(insets)
}

/** عنوان الملاحظة — 24sp عريض مثل ed-title-input */
@Composable
private fun TitleField(store: AppStore, note: Note, ui: MidadUi, chromeAlpha: () -> Float) {
    val colors = LocalColors.current
    val fonts = LocalFonts.current
    var title by remember(note.id, ui.textEpoch) { mutableStateOf(note.title) }
    androidx.compose.foundation.text.BasicTextField(
        value = title,
        onValueChange = {
            title = it
            note.title = it
            store.touch(note)
            store.pushHistorySoon(note.id)
        },
        textStyle = androidx.compose.ui.text.TextStyle(
            color = colors.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            fontFamily = fonts.sans, textDirection = androidx.compose.ui.text.style.TextDirection.Rtl
        ),
        cursorBrush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(colors.acc, colors.acc)),
        modifier = Modifier.fillMaxWidth().alpha(chromeAlpha()),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 22.dp).padding(top = 6.dp, bottom = 8.dp)) {
                if (title.isEmpty()) {
                    MText(
                        "عنوان الملاحظة", color = colors.inkFaint, fontSize = 24.sp,
                        fontWeight = FontWeight.Bold, fontFamily = fonts.sans
                    )
                }
                inner()
            }
        }
    )
}
