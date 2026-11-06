package com.rr.numio.pdf

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rr.numio.pdf.data.RecentStore
import com.rr.numio.pdf.pdf.OpenFailure
import com.rr.numio.pdf.pdf.PdfDoc
import com.rr.numio.pdf.pdf.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReaderState {
    data object Closed : ReaderState
    data class Loading(val name: String) : ReaderState
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
        reader = ReaderState.Loading(store.get(key)?.name ?: "")
        openJob = viewModelScope.launch {
            val ctx = getApplication<Application>()
            val (name, size) = withContext(Dispatchers.IO) { describe(uri) }
            reader = try {
                val doc = PdfDoc.open(ctx, uri)
                store.opened(key, name, size, doc.pageCount)
                ReaderState.Open(doc, key, name, store.get(key)?.lastPage ?: 0)
            } catch (e: OpenFailure) {
                ReaderState.Failed(e.message ?: "This file couldn't be opened.", key.takeIf { store.get(it) != null })
            }
        }
    }

    fun closeReader() {
        openJob?.cancel()
        closeDoc()
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
