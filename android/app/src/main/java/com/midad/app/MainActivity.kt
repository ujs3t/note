package com.midad.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.midad.app.glass.LocalGlassSettings
import com.midad.app.store.AppStore
import com.midad.app.store.Block
import com.midad.app.ui.BlockMenuOverlay
import com.midad.app.ui.ConfirmOverlay
import com.midad.app.ui.DrawerOverlay
import com.midad.app.ui.EditorScreen
import com.midad.app.ui.HomeScreen
import com.midad.app.ui.LocalColors
import com.midad.app.ui.LocalStore
import com.midad.app.ui.MidadTheme
import com.midad.app.ui.MidadUi
import com.midad.app.ui.RecOverlay
import com.midad.app.ui.RecorderHolder
import com.midad.app.ui.Scrim
import com.midad.app.ui.TaskCardOverlay
import com.midad.app.ui.TimePopOverlay
import com.midad.app.ui.ToastsLayer
import com.midad.app.store.TimerState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    lateinit var store: AppStore
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = AppStore(applicationContext)
        enableEdgeToEdge()
        setContent {
            MidadTheme {
                // اتجاه RTL شامل — التطبيق عربي كامل
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                    LocalStore provides store
                ) {
                    GlassProvider(store) { MidadRoot(store) }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        store.persistTimersOnBackground()
    }
}

/** يزوّد محرك الزجاج بالإعدادات الحية من الدرج (نسخة ثابتة عند التغيّر فقط) */
@Composable
private fun GlassProvider(store: AppStore, content: @Composable () -> Unit) {
    val gs = remember(store.glassVersion) { store.state.settings.glass.copy() }
    com.midad.app.glass.CompositionLocalGlassSettings(gs, content)
}

/* ============================================================
   الجذر: الرئيسية + المحرر (بأنميشن morph) + كل النوافذ
   ============================================================ */

private val MORPH_IN = CubicBezierEasing(0.3f, 1f, 0.3f, 1f)
private val MORPH_OUT = CubicBezierEasing(0.55f, 0f, 0.75f, 0.4f)

@Composable
fun MidadRoot(store: AppStore) {
    val colors = LocalColors.current
    val context = LocalContext.current
    val ui = remember { MidadUi(store) }
    val scope = rememberCoroutineScope()

    // نبضة المؤقتات العالمية — كل ثانية كما في الويب
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            store.tickTimers()
            store.tickState++
        }
    }

    // أنميشن فتح/إغلاق المحرر (morph من البطاقة إلى ملء الشاشة)
    val morph = remember { Animatable(1f) } // 1 = مفتوح بالكامل
    var editorVisible by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(0f) }

    val editorId = ui.editorNoteId
    val editorNote = editorId?.let { store.noteById(it) }

    // فتح
    LaunchedEffect(editorId) {
        if (editorId != null && editorNote != null) {
            editorVisible = true
            morph.snapTo(0f)
            chrome = 0f
            val m = launch { morph.animateTo(1f, tween(500, easing = MORPH_IN)) }
            launch {
                kotlinx.coroutines.delay(200)
                chrome = 1f
            }
            m.join()
        } else if (editorId == null) {
            editorVisible = false
        }
    }
    // إغلاق
    LaunchedEffect(ui.closingEditor) {
        if (ui.closingEditor) {
            chrome = 0f
            morph.animateTo(0f, tween(420, easing = MORPH_OUT))
            ui.editorNoteId = null
            ui.closingEditor = false
        }
    }

    // رجوع النظام: نفس سلسلة الإغلاق في الويب
    BackHandler(enabled = editorVisible) {
        if (ui.confirmState != null) ui.confirmState = null
        else if (ui.blockMenu != null) ui.blockMenu = null
        else if (ui.aaPopFor != null) ui.aaPopFor = null
        else if (ui.timePopOpen) ui.timePopOpen = false
        else if (ui.taskCardOpen) ui.taskCardOpen = false
        else if (ui.recOpen) { /* يُغلق عبر holder */ ui.recOpen = false }
        else if (ui.drawerOpen) ui.drawerOpen = false
        else if (ui.selMode) ui.exitSelection()
        else ui.closeEditorMorph()
    }
    BackHandler(enabled = !editorVisible) {
        if (ui.confirmState != null) ui.confirmState = null
        else if (ui.blockMenu != null) ui.blockMenu = null
        else if (ui.drawerOpen) ui.drawerOpen = false
        else if (ui.selMode) ui.exitSelection()
        else if (ui.taskCardOpen) ui.taskCardOpen = false
        else if (ui.timePopOpen) ui.timePopOpen = false
        else if (ui.view == "tasks") ui.view = "notes"
        else (context as? ComponentActivity)?.moveTaskToBack(true)
    }

    // منتقي الصور (بدون أذونات — Photo Picker)
    var pickTarget by remember { mutableStateOf<String?>(null) } // "block" | "task"
    val imagePick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = pickTarget
        pickTarget = null
        if (uri != null && target != null) {
            scope.launch {
                try {
                    val bmp = withContextIO {
                        decodeDownscaled(context, uri, if (target == "task") 640 else 1280)
                    }
                    if (bmp != null) {
                        val ref = store.putImage(bmp)
                        if (target == "task") {
                            ui.pendingTaskImage = ref
                        } else {
                            val note = editorNote
                            if (note != null) {
                                note.blocks.add(Block.Image(src = ref))
                                store.touch(note)
                                store.pushHistory(note.id)
                                store.toast("أُضيفت الصورة")
                            }
                        }
                    } else store.toast("تعذّر معالجة الصورة")
                } catch (e: Exception) {
                    store.toast("تعذّر معالجة الصورة")
                }
            }
        }
    }
    LaunchedEffect(ui.requestImageForBlock) {
        if (ui.requestImageForBlock) {
            ui.requestImageForBlock = false
            pickTarget = "block"
            imagePick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    LaunchedEffect(ui.taskCardOpen) {
        if (ui.taskCardOpen) {
            // عند فتح بطاقة المهمة ننتظر طلب الصورة منها
        }
    }

    // إذن الميكروفون + مسجّل
    val recorder = remember { RecorderHolder(context, store) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            ui.recTargetNote = editorNote
            recorder.start(store.audioFileFor(java.util.UUID.randomUUID().toString().replace("-", "").take(12)))
        } else {
            store.toast("تعذّر الوصول إلى الميكروفون")
            ui.recOpen = false
        }
    }
    LaunchedEffect(ui.recOpen) {
        if (ui.recOpen) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                ui.recTargetNote = editorNote
                recorder.start(store.audioFileFor(java.util.UUID.randomUUID().toString().replace("-", "").take(12)))
            } else {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            if (recorder.recorder != null) recorder.cancel()
            ui.recTargetNote = null
        }
    }

    // ---- الهيكل ----
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
    ) {
        // الشاشة الرئيسية (تبقى تحت المحرر أثناء morph)
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            HomeScreen(ui)
        }

        // المحرر — يتحوّل من البطاقة إلى الشاشة
        if (editorVisible && editorNote != null) {
            val t = morph.value
            val origin = ui.morphOrigin
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (origin != null && origin != Rect.Zero && t < 1f) {
                            val rootW = size.width
                            val rootH = size.height
                            val dx = (origin.center.x - rootW / 2f)
                            val dy = (origin.center.y - rootH / 2f)
                            translationX = dx * (1f - t)
                            translationY = dy * (1f - t)
                            val sx = (origin.width / rootW).coerceIn(0.05f, 1f)
                            val sy = (origin.height / rootH).coerceIn(0.02f, 1f)
                            scaleX = sx + (1f - sx) * t
                            scaleY = sy + (1f - sy) * t
                        }
                        val r = 18.dp.toPx() * (1f - t)
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(r)
                        clip = true
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
            ) {
                EditorScreen(store, editorNote, ui) { chrome }
            }
        }

        // ---- النوافذ العامة ----
        if (ui.drawerOpen) {
            DrawerOverlay(ui) { ui.drawerOpen = false }
        }
        if (ui.timePopOpen) {
            TimePopOverlay(
                ui = ui,
                onDismiss = { ui.timePopOpen = false },
                onSet = { secs ->
                    ui.timePopOpen = false
                    val target = ui.timePopTarget
                    ui.timePopTarget = null
                    if (target == "task") {
                        ui.pendingTaskTimer = TimerState(duration = secs, remaining = secs)
                    } else {
                        val note = editorNote
                        if (note != null) {
                            note.blocks.add(Block.Timer(TimerState(duration = secs, remaining = secs)))
                            store.touch(note)
                            store.pushHistory(note.id)
                            store.toast("أُضيف مؤقت " + store.fmtHMS(secs))
                        }
                    }
                }
            )
        }
        if (ui.taskCardOpen) {
            TaskCardOverlay(
                ui = ui,
                onDismiss = { ui.taskCardOpen = false },
                onPickImage = {
                    pickTarget = "task"
                    imagePick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
        }
        if (ui.recOpen) {
            Box(Modifier.fillMaxSize()) {
                com.midad.app.ui.Scrim { ui.recOpen = false }
                Box(
                    Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    com.midad.app.glass.GlassSurface(radius = 24.dp, modifier = Modifier.width(300.dp)) {
                        Box(Modifier.padding(22.dp)) {
                            RecOverlay(ui = ui, recorderHolder = recorder, onDismiss = { ui.recOpen = false })
                        }
                    }
                }
            }
        }
        if (ui.blockMenu != null) {
            BlockMenuOverlay(ui)
        }
        if (ui.confirmState != null) {
            ConfirmOverlay(ui)
        }
        ToastsLayer(ui)
    }
}

private suspend fun <T> withContextIO(block: suspend () -> T): T =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }

/** فك ترميز صورة مصغّرة بالحد الأقصى للبعد */
private fun decodeDownscaled(context: android.content.Context, uri: android.net.Uri, maxDim: Int): Bitmap? {
    return try {
        val ins = context.contentResolver.openInputStream(uri) ?: return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(ins, null, opts)
        ins.close()
        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) sample *= 2
        val ins2 = context.contentResolver.openInputStream(uri) ?: return null
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeStream(ins2, null, o2)
        ins2.close()
        bmp
    } catch (e: Exception) { null }
}
