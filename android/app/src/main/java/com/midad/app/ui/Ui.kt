package com.midad.app.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.midad.app.store.AppStore
import com.midad.app.store.TimerState

/*
 * حالة واجهة مِداد — تعكس متغيرات JS في الويب (view/query/openNoteId...)
 */

data class BlockMenuState(
    val noteId: String,
    val blockIndex: Int,
    val anchor: Rect
)

data class ConfirmState(
    val title: String,
    val body: String,
    val okLabel: String = "تأكيد",
    val onOk: () -> Unit
)

class MidadUi(val store: AppStore) {
    var view by mutableStateOf("notes")            // notes | tasks
    var query by mutableStateOf("")
    var selMode by mutableStateOf(false)
    val selNotes = mutableStateListOf<String>()
    val selTasks = mutableStateListOf<String>()

    var drawerOpen by mutableStateOf(false)
    var editorNoteId by mutableStateOf<String?>(null)
    var morphOrigin by mutableStateOf<Rect?>(null)
    var closingEditor by mutableStateOf(false)

    var timePopOpen by mutableStateOf(false)
    var timePopTarget by mutableStateOf<String?>(null)   // "block" | "task"
    var taskCardOpen by mutableStateOf(false)
    var recOpen by mutableStateOf(false)
    var aaPopFor by mutableStateOf<Int?>(null)           // index كتلة النص الأخيرة
    var blockMenu by mutableStateOf<BlockMenuState?>(null)

    var pendingTaskImage by mutableStateOf<String?>(null)
    var pendingTaskTimer: com.midad.app.store.TimerState? = null
    var requestImageForBlock by mutableStateOf(false)
    var editorViewport by mutableStateOf(0f)
    var editorContentY by mutableStateOf(0f)
    var recTargetNote: com.midad.app.store.Note? = null

    // إدارة الكتل
    val blockMetrics = mutableStateMapOf<String, Pair<Float, Float>>()
    var pendingFocus by mutableStateOf<String?>(null)
    var textEpoch by mutableStateOf(0)
    var confirmState by mutableStateOf<ConfirmState?>(null)

    fun closeEditorMorph() { closingEditor = true }

    fun confirmDeleteNote(note: com.midad.app.store.Note) {
        confirmState = ConfirmState(
            title = "حذف الملاحظة",
            body = if (note.title.isNotBlank()) "سيتم حذف «${note.title}» نهائيًا." else "سيتم حذف هذه الملاحظة نهائيًا.",
            okLabel = "حذف"
        ) {
            closingEditor = true
            store.deleteNote(note.id)
            store.toast("حُذفت الملاحظة")
        }
    }

    fun exitSelection() {
        selMode = false
        selNotes.clear()
        selTasks.clear()
    }

    fun toggleNoteSel(id: String) {
        if (selNotes.contains(id)) selNotes.remove(id) else selNotes.add(id)
        if (selNotes.isEmpty() && selTasks.isEmpty()) selMode = false
    }

    fun toggleTaskSel(id: String) {
        if (selTasks.contains(id)) selTasks.remove(id) else selTasks.add(id)
        if (selNotes.isEmpty() && selTasks.isEmpty()) selMode = false
    }

    fun selectAll() {
        selNotes.clear()
        selTasks.clear()
        if (view == "notes") stateNotes().forEach { selNotes.add(it.id) }
        else stateTasks().forEach { selTasks.add(it.id) }
    }

    fun deleteSelected(onDone: () -> Unit) {
        val n = selNotes.size + selTasks.size
        if (n == 0) return
        selNotes.forEach { store.deleteNote(it) }
        selTasks.forEach { store.deleteTask(it) }
        exitSelection()
        onDone()
    }

    private fun stateNotes() = store.state.notes
    private fun stateTasks() = store.state.tasks
}
