package com.rr.numio.pdf

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rr.numio.pdf.data.RecentStore
import com.rr.numio.pdf.edit.Edit
import com.rr.numio.pdf.edit.Edits
import com.rr.numio.pdf.edit.Mark
import com.rr.numio.pdf.edit.PdfSaver
import com.rr.numio.pdf.edit.SaveFailure
import com.rr.numio.pdf.pdf.OpenFailure
import com.rr.numio.pdf.pdf.PdfDoc
import com.rr.numio.pdf.pdf.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReaderState {
    data object Closed : ReaderState
    data class Loading(val name: String, val saving: Boolean = false) : ReaderState
    data class Open(val doc: PdfDoc, val uri: String, val name: String, val startPage: Int) : ReaderState
    /** [uri] is set when the file is in the library, so it can be removed from there. */
    data class Failed(val message: String, val uri: String?) : ReaderState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = RecentStore(app)

    var reader by mutableStateOf<ReaderState>(ReaderState.Closed)
        private set

    /** Opened by tapping a PDF in another app: going back returns to that app. */
    var openedFromOutside = false
        private set

    private var openJob: Job? = null

    fun open(uri: Uri, fromOutside: Boolean) {
        val key = uri.toString()
        val now = reader
        if (now is ReaderState.Open && now.uri == key) return
        openedFromOutside = fromOutside
        openJob?.cancel()
        closeDoc()
        edits.clear()
        reader = ReaderState.Loading(store.get(key)?.name ?: "")
        openJob = viewModelScope.launch { load(uri, page = null) }
    }

    /** Opens [uri]; [page] overrides the remembered reading place (after saving). */
    private suspend fun load(uri: Uri, page: Int?) {
        val key = uri.toString()
        val ctx = getApplication<Application>()
        val (name, size) = withContext(Dispatchers.IO) { describe(uri) }
        reader = try {
            val doc = PdfDoc.open(ctx, uri)
            store.opened(key, name, size, doc.pageCount)
            if (page != null) store.setPage(key, page.coerceIn(0, doc.pageCount - 1))
            ReaderState.Open(doc, key, name, store.get(key)?.lastPage ?: 0)
        } catch (e: OpenFailure) {
            ReaderState.Failed(e.message ?: "This file couldn't be opened.", key.takeIf { store.get(it) != null })
        }
    }

    // ---- Changes to marks not yet saved into the file

    /** Every change since the PDF was opened or last saved, oldest first. Undo drops the last. */
    val edits = mutableStateListOf<Edit>()

    private var nextNewId = -1L

    /** A short notice to show once ("Saved", or why saving failed). */
    var notice by mutableStateOf<String?>(null)

    private var savePage = 0

    fun addMark(mark: Mark) {
        edits.add(Edit.Add(mark.copy(id = nextNewId--, ref = -1)))
    }

    fun removeMark(mark: Mark) {
        edits.add(Edit.Remove(mark))
    }

    fun recolorMark(mark: Mark, color: Int) {
        if (mark.color != color) edits.add(Edit.Recolor(mark, color))
    }

    fun changeMark(old: Mark, new: Mark) {
        edits.add(Edit.Change(old, new))
    }

    fun undoMark() {
        if (edits.isNotEmpty()) edits.removeAt(edits.lastIndex)
    }

    fun discardMarks() = edits.clear()

    /** The page to come back to after saving. */
    fun rememberSavePage(page: Int) {
        savePage = page
    }

    /** True if Android lets the app write to the open file itself. */
    fun canWriteOriginal(): Boolean {
        val open = reader as? ReaderState.Open ?: return false
        val uri = Uri.parse(open.uri)
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        return getApplication<Application>().checkUriPermission(
            uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Saves the marks into the open file ([target] null) or into a new copy at [target]. */
    fun save(target: Uri?) {
        val open = reader as? ReaderState.Open ?: return
        if (edits.isEmpty()) return
        val plan = Edits.plan(edits.toList())
        if (plan.isEmpty) {
            // Changes that cancel out (a mark added then deleted): nothing to write
            edits.clear()
            return
        }
        val original = Uri.parse(open.uri)
        val dest = target ?: original
        val page = savePage
        openJob?.cancel()
        reader = ReaderState.Loading(open.name, saving = true)
        openJob = viewModelScope.launch {
            val ctx = getApplication<Application>()
            open.doc.close()
            try {
                PdfSaver.save(ctx, open.doc.source, dest, plan)
                edits.clear()
                Thumbnails.forget(ctx, dest.toString())
                load(dest, page)
                notice = if (target == null) "Saved" else "Saved as a new PDF"
            } catch (e: SaveFailure) {
                notice = e.message
                load(original, page) // the marks stay, so nothing is lost
            }
        }
    }

    fun closeReader() {
        openJob?.cancel()
        closeDoc()
        edits.clear()
        reader = ReaderState.Closed
        openedFromOutside = false
    }

    fun pageChanged(uri: String, page: Int) = store.setPage(uri, page)

    fun toggleStar(uri: String) = store.toggleStar(uri)

    fun remove(uri: String) {
        store.remove(uri)
        Thumbnails.forget(getApplication(), uri)
        if ((reader as? ReaderState.Failed)?.uri == uri) reader = ReaderState.Closed
    }

    private fun closeDoc() {
        val open = reader as? ReaderState.Open ?: return
        viewModelScope.launch { open.doc.close() }
    }

    override fun onCleared() {
        // viewModelScope is already cancelled here, so close straight away
        (reader as? ReaderState.Open)?.doc?.let { doc ->
            kotlinx.coroutines.runBlocking { doc.close() }
        }
    }

    /** File name and size as the app that owns the file reports them. */
    private fun describe(uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = -1L
        runCatching {
            getApplication<Application>().contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                }
            }
        }
        val fallback = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Document.pdf"
        return (name?.takeIf { it.isNotBlank() } ?: fallback) to size
    }
}
