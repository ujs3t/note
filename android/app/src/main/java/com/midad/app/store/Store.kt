package com.midad.app.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ============================================================
   مخزن التطبيق — حفظ/تحميل/تراجع/تنبيهات/صور
   ============================================================ */

class NoteHistory {
    val states = mutableListOf<String>()
    var index = 0
}

class AppStore(private val context: Context) {

    var state by mutableStateOf(load() ?: seed())
        private set

    val toastQueue = mutableStateListOf<Pair<Long, String>>()
    var hapticTick by mutableStateOf(0)
        private set
    var tickState by mutableStateOf(0)
    var dataVersion by mutableStateOf(0)
    var glassVersion by mutableStateOf(0)

    // سجل التراجع لكل ملاحظة (مطابق للويب: حد 50 حالة)
    val noteHistory = HashMap<String, NoteHistory>()
    var undoInProgress = false

    private val handler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null
    private var histRunnable: Runnable? = null
    private var lastHistNoteId: String? = null

    /* ---------- تحميل / حفظ ---------- */

    private val dataFile: File get() = File(context.filesDir, "midad.v2.json")

    private fun load(): AppState? = try {
        if (dataFile.exists()) MidadJson.fromJson(dataFile.readText()) else null
    } catch (e: Exception) { null }

    /** إشعار Compose بتغيّر هيكلي (إضافة/حذف/تبديل) */
    fun bumpData() { dataVersion++ }

    fun saveNow() {
        try {
            dataFile.writeText(MidadJson.toJson(state).toString())
        } catch (_: Exception) { }
    }

    fun saveSoon() {
        saveRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { saveNow() }
        saveRunnable = r
        handler.postDelayed(r, 500)
    }

    fun persistTimersOnBackground() {
        // تجميد المؤقتات الجارية عند الخلفية مثل الويب (visibilitychange)
        for (n in state.notes) for (b in n.blocks) if (b is Block.Timer && b.timer.running) b.timer.pause()
        for (t in state.tasks) t.timer?.let { if (it.running) it.pause() }
        saveNow()
    }

    /* ---------- الاهتزاز ---------- */

    fun vibrate() { hapticTick++ }

    /* ---------- نبضة المؤقتات (كل ثانية مثل حلقة الويب) ---------- */

    fun tickTimers() {
        var changed = false
        val now = System.currentTimeMillis()
        for (n in state.notes) for (b in n.blocks) {
            if (b is Block.Timer && b.timer.running) {
                val rem = b.timer.remainingNow()
                if (rem <= 0) {
                    b.timer.remaining = 0; b.timer.pause()
                    toast("انتهى المؤقت")
                }
                changed = true
            }
        }
        for (t in state.tasks) {
            val timer = t.timer ?: continue
            if (timer.running) {
                val rem = timer.remainingNow()
                if (rem <= 0) {
                    timer.remaining = 0; timer.pause()
                    toast("انتهى وقت المهمة: " + t.text)
                }
                changed = true
            }
        }
        if (changed) saveSoon()
        tickState++
    }


    /* ---------- تنبيهات Toast ---------- */

    fun toast(msg: String) {
        val key = System.nanoTime()
        toastQueue.add(key to msg)
        handler.postDelayed({
            toastQueue.removeAll { it.first == key }
        }, 2700)
    }

    /* ---------- سجل التراجع ---------- */

    private fun snapshotNote(n: Note): String {
        val o = org.json.JSONObject()
        o.put("title", n.title)
        val arr = org.json.JSONArray()
        for (b in n.blocks) {
            val bo = org.json.JSONObject()
            when (b) {
                is Block.Text -> { bo.put("type", "text"); bo.put("style", b.style); bo.put("text", b.text) }
                is Block.Todo -> {
                    bo.put("type", "todo")
                    val ia = org.json.JSONArray()
                    for (it in b.items) ia.put(org.json.JSONObject().put("done", it.done).put("text", it.text))
                    bo.put("items", ia)
                }
                is Block.Code -> { bo.put("type", "code"); bo.put("title", b.title); bo.put("code", b.code) }
                is Block.Timer -> { bo.put("type", "timer"); bo.put("duration", b.timer.duration); bo.put("remaining", b.timer.remaining); bo.put("running", b.timer.running); bo.put("startedAt", b.timer.startedAt ?: org.json.JSONObject.NULL); bo.put("remainingAtStart", b.timer.remainingAtStart) }
                is Block.Image -> { bo.put("type", "image"); bo.put("src", b.src) }
                is Block.Voice -> { bo.put("type", "voice"); bo.put("url", b.url); bo.put("duration", b.duration) }
            }
            arr.put(bo)
        }
        o.put("blocks", arr)
        return o.toString()
    }

    fun pushHistory(noteId: String) {
        if (undoInProgress) return
        val note = state.notes.firstOrNull { it.id == noteId } ?: return
        val snap = snapshotNote(note)
        val h = noteHistory.getOrPut(noteId) {
            NoteHistory().apply { states.add(snap) }
        }
        if (h.states.lastOrNull() == snap) return
        if (h.index < h.states.size - 1) {
            while (h.states.size > h.index + 1) h.states.removeAt(h.states.size - 1)
        }
        h.states.add(snap)
        if (h.states.size > 50) h.states.removeAt(0)
        h.index = h.states.size - 1
        dataVersion++
    }

    fun pushHistorySoon(noteId: String) {
        lastHistNoteId = noteId
        histRunnable?.let { handler.removeCallbacks(it) }
        val r = Runnable { lastHistNoteId?.let { pushHistory(it) } }
        histRunnable = r
        handler.postDelayed(r, 650)
    }

    fun initHistory(noteId: String) {
        if (!noteHistory.containsKey(noteId)) {
            state.notes.firstOrNull { it.id == noteId }?.let { pushHistory(it.id) }
        }
    }

    fun canUndo(noteId: String?): Boolean {
        val h = noteId?.let { noteHistory[it] } ?: return false
        return h.index > 0
    }

    fun canRedo(noteId: String?): Boolean {
        val h = noteId?.let { noteHistory[it] } ?: return false
        return h.index < h.states.size - 1
    }

    private fun restore(noteId: String, json: String) {
        val note = state.notes.firstOrNull { it.id == noteId } ?: return
        try {
            val o = org.json.JSONObject(json)
            note.title = o.optString("title", "")
            val arr = o.optJSONArray("blocks") ?: org.json.JSONArray()
            val blocks = mutableListOf<Block>()
            for (i in 0 until arr.length()) {
                val bo = arr.getJSONObject(i)
                when (bo.optString("type")) {
                    "text" -> blocks.add(Block.Text(bo.optString("style", "p"), bo.optString("text", "")))
                    "todo" -> {
                        val items = mutableListOf<TodoItem>()
                        val ia = bo.optJSONArray("items") ?: org.json.JSONArray()
                        for (k in 0 until ia.length()) {
                            val io = ia.getJSONObject(k)
                            items.add(TodoItem(io.optBoolean("done"), io.optString("text")))
                        }
                        blocks.add(Block.Todo(items))
                    }
                    "code" -> blocks.add(Block.Code(bo.optString("title", ""), bo.optString("code", "")))
                    "timer" -> blocks.add(Block.Timer(TimerState(bo.optLong("duration"), bo.optLong("remaining"), bo.optBoolean("running"), if (bo.optBoolean("running")) bo.optLong("startedAt", 0).takeIf { it > 0 } else null, bo.optLong("remainingAtStart", 0))))
                    "image" -> blocks.add(Block.Image(bo.optString("src", "")))
                    "voice" -> blocks.add(Block.Voice(bo.optString("url", ""), bo.optLong("duration", 0)))
                }
            }
            note.blocks = blocks
            note.updatedAt = System.currentTimeMillis()
            dataVersion++
            saveSoon()
        } catch (_: Exception) { }
    }

    fun undo(noteId: String) {
        val h = noteHistory[noteId] ?: return
        if (h.index <= 0) return
        undoInProgress = true
        h.index--
        restore(noteId, h.states[h.index])
        undoInProgress = false
        vibrate()
    }

    fun redo(noteId: String) {
        val h = noteHistory[noteId] ?: return
        if (h.index >= h.states.size - 1) return
        undoInProgress = true
        h.index++
        restore(noteId, h.states[h.index])
        undoInProgress = false
        vibrate()
    }

    /* ---------- ملاحظات ---------- */

    fun newNote(): Note {
        val n = Note(blocks = mutableListOf(Block.Text()))
        state.notes.add(0, n)
        dataVersion++
        saveSoon()
        return n
    }

    fun deleteNote(id: String) {
        state.notes.removeAll { it.id == id }
        noteHistory.remove(id)
        dataVersion++
        saveNow()
    }

    fun noteById(id: String): Note? = state.notes.firstOrNull { it.id == id }

    fun noteIsEmpty(n: Note): Boolean =
        n.title.isBlank() && n.blocks.all { b ->
            (b is Block.Text && b.text.isBlank()) || (b is Block.Todo && b.items.isEmpty())
        }

    fun touch(note: Note) { note.updatedAt = System.currentTimeMillis(); saveSoon() }

    /* ---------- مهام ---------- */

    fun addTask(text: String, image: String?, timer: TimerState?) {
        state.tasks.add(0, Task(text = text, image = image, timer = timer))
        dataVersion++
        saveSoon()
    }

    fun deleteTask(id: String) {
        state.tasks.removeAll { it.id == id }
        dataVersion++
        saveNow()
    }

    /* ---------- تخزين الصور والصوت ---------- */

    private val imageCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun putImage(bitmap: Bitmap): String {
        val id = uid()
        val f = File(context.filesDir, "img/$id.jpg")
        f.parentFile?.mkdirs()
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 72, it) }
        return "img:$id"
    }

    fun resolvePath(ref: String): File? {
        if (!ref.startsWith("img:")) return null
        return File(context.filesDir, "img/${ref.removePrefix("img:")}.jpg")
    }

    fun loadBitmap(ref: String): Bitmap? {
        val f = resolvePath(ref) ?: return null
        imageCache.get(ref)?.let { return it }
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / sample > 1600 || opts.outHeight / sample > 1600) sample *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(f.absolutePath, o2)?.also { imageCache.put(ref, it) }
        } catch (e: Exception) { null }
    }

    fun audioFileFor(id: String): File {
        val d = File(context.filesDir, "audio")
        d.mkdirs()
        return File(d, "$id.m4a")
    }

    /* ---------- تنسيقات ---------- */

    fun fmtDate(ts: Long): String {
        if (ts <= 0) return ""
        val calToday = java.util.Calendar.getInstance()
        val calThen = java.util.Calendar.getInstance().apply { timeInMillis = ts }
        val d1 = calToday.get(java.util.Calendar.DAY_OF_YEAR) + calToday.get(java.util.Calendar.YEAR) * 400
        val d2 = calThen.get(java.util.Calendar.DAY_OF_YEAR) + calThen.get(java.util.Calendar.YEAR) * 400
        return when (d1 - d2) {
            0 -> "اليوم"
            1 -> "أمس"
            else -> SimpleDateFormat("d MMM", Locale("ar")).format(Date(ts))
        }
    }

    fun fmtHMS(totalSecs: Long): String {
        val s = maxOf(0, totalSecs)
        val h = s / 3600
        val m = (s % 3600) / 60
        val x = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, x)
        else String.format(Locale.US, "%02d:%02d", m, x)
    }
}

/* ---------- البيانات الأولية (نفس بذور الويب) ---------- */

fun seed(): AppState {
    val now = System.currentTimeMillis()
    val D = 86_400_000L
    val H = 3_600_000L
    return AppState(
        settings = Settings(),
        notes = mutableListOf(
            Note(
                title = "إطلاق مِداد — قائمة التحقق",
                createdAt = now - 4 * D, updatedAt = now - 2 * H,
                blocks = mutableListOf(
                    Block.Text(text = "قبل إعلان الإصدار الأول لا بد من تثبيت هذه البنود."),
                    Block.Todo(mutableListOf(
                        TodoItem(true, "بناء واجهتي الملاحظات والمهام"),
                        TodoItem(false, "اختبار الزجاج السائل")
                    )),
                    Block.Timer(TimerState(duration = 1500, remaining = 934, running = true, startedAt = null, remainingAtStart = 934))
                )
            ),
            Note(
                title = "سكربتات مفيدة",
                createdAt = now - 7 * D, updatedAt = now - 6 * D,
                blocks = mutableListOf(
                    Block.Text(text = "أرشيف صغير للقصاصات."),
                    Block.Code(title = "debounce.js", code = "function debounce(fn, ms) {\n  let t;\n  return (...args) => {\n    clearTimeout(t);\n    t = setTimeout(() => fn(...args), ms);\n  };\n}\nconst x = 42;")
                )
            )
        ),
        tasks = mutableListOf(
            Task(
                text = "جلسة تركيز: تدوين خطة الإصدار القادم",
                createdAt = now - 40 * 60_000,
                timer = TimerState(duration = 1500, remaining = 1120, running = true, startedAt = null, remainingAtStart = 1120)
            ),
            Task(text = "مراجعة درجة الأصفر على الخلفية الداكنة", createdAt = now - 2 * D)
        )
    )
}
