package com.midad.app.store

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/* ============================================================
   نموذج بيانات مِداد — مطابق حرفيًا لمخطط midad.v2 في نسخة الويب
   ============================================================ */

fun uid(): String = UUID.randomUUID().toString().replace("-", "").take(12)

data class GlassSettings(
    var on: Boolean = true,
    var specularOpacity: Float = 0.40f,
    var specularSaturation: Float = 6f,
    var refractionLevel: Float = 1.00f,
    var blur: Float = 1.0f,
    var progressiveBlurStrength: Float = 1.00f,
    var glassBgOpacity: Float = 0.60f
)

data class Settings(
    var haptics: Boolean = true,
    var minimap: Boolean = true,
    var glass: GlassSettings = GlassSettings()
)

data class TodoItem(var done: Boolean = false, var text: String = "")

data class TimerState(
    var duration: Long = 0,
    var remaining: Long = 0,
    var running: Boolean = false,
    var startedAt: Long? = null,
    var remainingAtStart: Long = 0
) {
    fun start() { if (!running) { startedAt = System.currentTimeMillis(); remainingAtStart = remaining; running = true } }
    fun pause() {
        if (!running) return
        val elapsed = (System.currentTimeMillis() - (startedAt ?: System.currentTimeMillis())) / 1000
        remaining = maxOf(0, remainingAtStart - elapsed)
        running = false; startedAt = null
    }
    fun remainingNow(): Long {
        if (!running || startedAt == null) return remaining
        val elapsed = (System.currentTimeMillis() - startedAt!!) / 1000
        return maxOf(0, remainingAtStart - elapsed)
    }
}

sealed class Block {
    var id: String = uid()

    data class Text(var style: String = "p", var text: String = "") : Block()
    data class Todo(var items: MutableList<TodoItem> = mutableListOf()) : Block()
    data class Code(var title: String = "", var code: String = "") : Block()
    data class Timer(var timer: TimerState = TimerState()) : Block()
    data class Image(var src: String = "") : Block()
    data class Voice(var url: String = "", var duration: Long = 0) : Block()
}

data class Note(
    var id: String = uid(),
    var title: String = "",
    var blocks: MutableList<Block> = mutableListOf(Block.Text()),
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

data class Task(
    var id: String = uid(),
    var text: String = "",
    var note: String = "",
    var image: String? = null,
    var done: Boolean = false,
    var createdAt: Long = System.currentTimeMillis(),
    var timer: TimerState? = null
)

data class AppState(
    var settings: Settings = Settings(),
    var notes: MutableList<Note> = mutableListOf(),
    var tasks: MutableList<Task> = mutableListOf()
)

/* ---------- تسلسل JSON (مطابق لمفاتيح الويب) ---------- */

object MidadJson {

    fun toJson(s: AppState): JSONObject {
        val root = JSONObject()
        val st = JSONObject()
        st.put("haptics", s.settings.haptics)
        st.put("minimap", s.settings.minimap)
        val g = s.settings.glass
        st.put("glass", JSONObject()
            .put("on", g.on).put("specularOpacity", g.specularOpacity.toDouble())
            .put("specularSaturation", g.specularSaturation.toDouble())
            .put("refractionLevel", g.refractionLevel.toDouble())
            .put("blur", g.blur.toDouble())
            .put("progressiveBlurStrength", g.progressiveBlurStrength.toDouble())
            .put("glassBgOpacity", g.glassBgOpacity.toDouble()))
        root.put("settings", st)

        val notes = JSONArray()
        for (n in s.notes) notes.put(noteJson(n))
        root.put("notes", notes)

        val tasks = JSONArray()
        for (t in s.tasks) tasks.put(taskJson(t))
        root.put("tasks", tasks)
        return root
    }

    private fun timerJson(t: TimerState): JSONObject = JSONObject()
        .put("duration", t.duration).put("remaining", t.remaining)
        .put("running", false) // عند الحفظ نجمّد المؤقتات مثل الويب عند الإخفاء
        .put("startedAt", if (t.running) System.currentTimeMillis() else JSONObject.NULL)
        .put("remainingAtStart", if (t.running) t.remainingNow() else t.remaining)

    fun noteJson(n: Note): JSONObject {
        val o = JSONObject()
        o.put("id", n.id); o.put("title", n.title)
        o.put("createdAt", n.createdAt); o.put("updatedAt", n.updatedAt)
        val arr = JSONArray()
        for (b in n.blocks) {
            val bo = JSONObject()
            bo.put("id", b.id)
            when (b) {
                is Block.Text -> { bo.put("type", "text"); bo.put("style", b.style); bo.put("text", b.text); bo.put("html", b.text.replace("\n", "<br>")) }
                is Block.Todo -> {
                    bo.put("type", "todo")
                    val items = JSONArray()
                    for (it in b.items) items.put(JSONObject().put("done", it.done).put("text", it.text))
                    bo.put("items", items)
                }
                is Block.Code -> { bo.put("type", "code"); bo.put("title", b.title); bo.put("code", b.code) }
                is Block.Timer -> { bo.put("type", "timer"); bo.put("duration", b.timer.duration); bo.put("remaining", b.timer.remainingNow()); bo.put("running", false); bo.put("startedAt", JSONObject.NULL); bo.put("remainingAtStart", b.timer.remainingNow()) }
                is Block.Image -> { bo.put("type", "image"); bo.put("src", b.src) }
                is Block.Voice -> { bo.put("type", "voice"); bo.put("url", b.url); bo.put("duration", b.duration) }
            }
            arr.put(bo)
        }
        o.put("blocks", arr)
        return o
    }

    fun taskJson(t: Task): JSONObject {
        val o = JSONObject()
        o.put("id", t.id); o.put("text", t.text); o.put("note", t.note)
        o.put("image", t.image ?: JSONObject.NULL)
        o.put("done", t.done); o.put("createdAt", t.createdAt)
        t.timer?.let { o.put("timer", timerJson(it)) }
        return o
    }

    fun fromJson(raw: String): AppState? = try {
        val root = JSONObject(raw)
        val st = root.optJSONObject("settings") ?: JSONObject()
        val gRaw = st.optJSONObject("glass")
        val glass = if (gRaw == null) GlassSettings() else GlassSettings(
            on = gRaw.optBoolean("on", true),
            specularOpacity = gRaw.optDouble("specularOpacity", 0.40).toFloat(),
            specularSaturation = gRaw.optDouble("specularSaturation", 6.0).toFloat(),
            refractionLevel = gRaw.optDouble("refractionLevel", 1.0).toFloat(),
            blur = gRaw.optDouble("blur", 1.0).toFloat(),
            progressiveBlurStrength = gRaw.optDouble("progressiveBlurStrength", 1.0).toFloat(),
            glassBgOpacity = gRaw.optDouble("glassBgOpacity", 0.6).toFloat()
        )
        val settings = Settings(
            haptics = st.optBoolean("haptics", true),
            minimap = st.optBoolean("minimap", true),
            glass = glass
        )
        val notes = mutableListOf<Note>()
        val notesArr = root.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until notesArr.length()) {
            val no = notesArr.getJSONObject(i)
            val blocks = mutableListOf<Block>()
            val bArr = no.optJSONArray("blocks") ?: JSONArray()
            for (j in 0 until bArr.length()) {
                val bo = bArr.getJSONObject(j)
                val blk = when (bo.optString("type")) {
                    "text" -> Block.Text(bo.optString("style", "p"), bo.optString("text", ""))
                    "todo" -> {
                        val items = mutableListOf<TodoItem>()
                        val ia = bo.optJSONArray("items") ?: JSONArray()
                        for (k in 0 until ia.length()) {
                            val io = ia.getJSONObject(k)
                            items.add(TodoItem(io.optBoolean("done", false), io.optString("text", "")))
                        }
                        Block.Todo(items)
                    }
                    "code" -> Block.Code(bo.optString("title", ""), bo.optString("code", ""))
                    "timer" -> {
                        val started = bo.optLong("startedAt", 0L)
                        val running = bo.optBoolean("running", false)
                        val rem = bo.optLong("remaining", 0)
                        val t = TimerState(
                            duration = bo.optLong("duration", 0), remaining = rem,
                            running = running && started > 0,
                            startedAt = if (running && started > 0) started else null,
                            remainingAtStart = bo.optLong("remainingAtStart", rem)
                        )
                        Block.Timer(t)
                    }
                    "image" -> Block.Image(bo.optString("src", ""))
                    "voice" -> Block.Voice(bo.optString("url", ""), bo.optLong("duration", 0))
                    else -> null
                } ?: continue
                bo.optString("id", "").takeIf { it.isNotEmpty() }?.let { blk.id = it }
                blocks.add(blk)
            }
            notes.add(Note(
                id = no.optString("id", uid()),
                title = no.optString("title", ""),
                blocks = blocks,
                createdAt = no.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = no.optLong("updatedAt", System.currentTimeMillis())
            ))
        }
        val tasks = mutableListOf<Task>()
        val tasksArr = root.optJSONArray("tasks") ?: JSONArray()
        for (i in 0 until tasksArr.length()) {
            val to = tasksArr.getJSONObject(i)
            var timer: TimerState? = null
            to.optJSONObject("timer")?.let { to2 ->
                val started = to2.optLong("startedAt", 0L)
                val running = to2.optBoolean("running", false)
                val rem = to2.optLong("remaining", 0)
                timer = TimerState(
                    duration = to2.optLong("duration", 0), remaining = rem,
                    running = running && started > 0,
                    startedAt = if (running && started > 0) started else null,
                    remainingAtStart = to2.optLong("remainingAtStart", rem)
                )
            }
            tasks.add(Task(
                id = to.optString("id", uid()),
                text = to.optString("text", ""),
                note = to.optString("note", ""),
                image = if (to.isNull("image")) null else to.optString("image"),
                done = to.optBoolean("done", false),
                createdAt = to.optLong("createdAt", System.currentTimeMillis()),
                timer = timer
            ))
        }
        AppState(settings, notes, tasks)
    } catch (e: Exception) { null }
}
