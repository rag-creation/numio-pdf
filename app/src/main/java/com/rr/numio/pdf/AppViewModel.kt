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
import com.rr.numio.pdf.data.Bookmark
import com.rr.numio.pdf.data.Bookmarks
import com.rr.numio.pdf.data.PageLayout
import com.rr.numio.pdf.data.PageLook
import com.rr.numio.pdf.ui.theme.AppTheme
import com.rr.numio.pdf.pages.AddedPdf
import com.rr.numio.pdf.pages.Organiser
import com.rr.numio.pdf.pages.PagePlan
import com.rr.numio.pdf.pages.PageWriter
import com.rr.numio.pdf.data.RecentStore
import com.rr.numio.pdf.edit.Edit
import com.rr.numio.pdf.edit.Edits
import com.rr.numio.pdf.edit.Mark
import com.rr.numio.pdf.edit.PdfSaver
import com.rr.numio.pdf.edit.SaveFailure
import com.rr.numio.pdf.pdf.OpenFailure
import com.rr.numio.pdf.pdf.PdfDoc
import com.rr.numio.pdf.pdf.Unlock
import com.rr.numio.pdf.security.AppLock
import com.rr.numio.pdf.security.LockAfter
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
    /** Needs a password to open; [wrong] once a password didn't fit; [page] to open at. */
    data class Locked(val uri: String, val name: String, val wrong: Boolean = false, val page: Int? = null) : ReaderState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = RecentStore(app)

    // ---- Reading: page colours and layout (remembered for every PDF), bookmarks (per PDF)
    private val readingPrefs = app.getSharedPreferences("reading", android.content.Context.MODE_PRIVATE)

    var look by mutableStateOf(
        runCatching { PageLook.valueOf(readingPrefs.getString("look", null) ?: "") }.getOrDefault(PageLook.NORMAL),
    )
        private set
    var layout by mutableStateOf(
        runCatching { PageLayout.valueOf(readingPrefs.getString("layout", null) ?: "") }.getOrDefault(PageLayout.SCROLL),
    )
        private set

    fun chooseLook(value: PageLook) {
        look = value
        readingPrefs.edit().putString("look", value.name).apply()
    }

    fun chooseLayout(value: PageLayout) {
        layout = value
        readingPrefs.edit().putString("layout", value.name).apply()
    }

    // ---- App lock and Private

    private val lockPrefs = app.getSharedPreferences("lock", android.content.Context.MODE_PRIVATE)

    /** Ask for the phone's lock when PDF Reader opens or comes back. */
    var appLock by mutableStateOf(lockPrefs.getBoolean("on", false))
        private set

    var lockAfter by mutableStateOf(
        runCatching { LockAfter.valueOf(lockPrefs.getString("after", null) ?: "") }.getOrDefault(LockAfter.MINUTE),
    )
        private set

    /** Showing the lock screen; starts locked when the lock is on. */
    var locked by mutableStateOf(appLock)
        private set

    /** Private PDFs unlocked for now. */
    var privateOpen by mutableStateOf(false)

    private var leftAt = 0L

    /** The app sent the person somewhere on purpose (a file picker, sharing, the PIN screen). */
    var outing = false

    fun chooseAppLock(on: Boolean) {
        appLock = on
        lockPrefs.edit().putBoolean("on", on).apply()
    }

    fun chooseLockAfter(value: LockAfter) {
        lockAfter = value
        lockPrefs.edit().putString("after", value.name).apply()
    }

    fun unlocked() {
        locked = false
        outing = false
    }

    /** PDF Reader went to the background. */
    fun left(now: Long) {
        leftAt = now
    }

    /** PDF Reader is on screen again: lock it, or close Private, if away long enough. */
    fun cameBack(now: Long) {
        val lock = AppLock.shouldLock(appLock, lockAfter, leftAt, now, outing)
        if (lock) locked = true
        if (AppLock.shouldClosePrivate(lock, leftAt, now, outing)) privateOpen = false
        outing = false
        leftAt = 0L
    }

    fun setPrivate(uri: String, value: Boolean) {
        store.setPrivate(uri, value)
        notice = if (value) "Moved to Private" else "Moved out of Private"
    }

    /** Dark, light, or whatever the phone uses. */
    var appTheme by mutableStateOf(
        runCatching { AppTheme.valueOf(readingPrefs.getString("theme", null) ?: "") }.getOrDefault(AppTheme.SYSTEM),
    )
        private set

    fun chooseTheme(value: AppTheme) {
        appTheme = value
        readingPrefs.edit().putString("theme", value.name).apply()
    }

    private val bookmarkPrefs = app.getSharedPreferences("bookmarks", android.content.Context.MODE_PRIVATE)

    /** Bookmarks of the open PDF, in page order. */
    var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
        private set

    private fun loadBookmarks(uri: String) {
        bookmarks = Bookmarks.decode(bookmarkPrefs.getString(uri, "") ?: "")
    }

    private fun saveBookmarks(list: List<Bookmark>) {
        val open = reader as? ReaderState.Open ?: return
        saveBookmarksFor(open.uri, list)
    }

    private fun saveBookmarksFor(uri: String, list: List<Bookmark>) {
        bookmarks = list
        bookmarkPrefs.edit().apply {
            if (list.isEmpty()) remove(uri) else putString(uri, Bookmarks.encode(list))
        }.apply()
    }

    fun toggleBookmark(page: Int) = saveBookmarks(Bookmarks.toggle(bookmarks, page))

    fun renameBookmark(page: Int, name: String) = saveBookmarks(Bookmarks.rename(bookmarks, page, name))

    /** Settings screen open (from the library's gear). */
    var settingsOpen by mutableStateOf(false)

    /** Forgets the library: list, stars, reading places and bookmarks. The PDFs aren't touched. */
    fun clearRecent() {
        store.files.value.forEach { Thumbnails.forget(getApplication(), it.uri) }
        store.clear()
        bookmarkPrefs.edit().clear().apply()
    }

    /** Saved signatures, and whether they've been unlocked since the app opened. */
    val signatures = com.rr.numio.pdf.data.SignatureStore(app)
    var signaturesUnlocked by mutableStateOf(false)

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
        stopOrganising()
        openedFromOutside = fromOutside
        openJob?.cancel()
        closeDoc()
        Unlock.forget(getApplication())
        edits.clear()
        reader = ReaderState.Loading(store.get(key)?.name ?: "")
        openJob = viewModelScope.launch { load(uri, page = null) }
    }

    /**
     * Opens [uri]; [page] overrides the remembered reading place (after saving);
     * [password] for a locked PDF.
     */
    private suspend fun load(uri: Uri, page: Int?, password: String? = null) {
        val key = uri.toString()
        val ctx = getApplication<Application>()
        val (name, size) = withContext(Dispatchers.IO) { describe(uri) }
        reader = try {
            val doc = PdfDoc.open(ctx, uri, password)
            store.opened(key, name, size, doc.pageCount)
            if (page != null) store.setPage(key, page.coerceIn(0, doc.pageCount - 1))
            loadBookmarks(key)
            ReaderState.Open(doc, key, name, store.get(key)?.lastPage ?: 0)
        } catch (e: OpenFailure) {
            if (e.locked) {
                ReaderState.Locked(key, name, wrong = e.wrongPassword, page = page)
            } else {
                ReaderState.Failed(e.message ?: "This file couldn't be opened.", key.takeIf { store.get(it) != null })
            }
        }
    }

    /** Tries the password for the locked PDF waiting to open. */
    fun unlock(password: String) {
        val locked = reader as? ReaderState.Locked ?: return
        if (password.isEmpty()) return
        openJob?.cancel()
        reader = ReaderState.Loading(locked.name)
        openJob = viewModelScope.launch { load(Uri.parse(locked.uri), locked.page, password) }
    }

    // ---- Changes to marks not yet saved into the file

    /** Every change since the PDF was opened or last saved, oldest first. Undo drops the last. */
    val edits = mutableStateListOf<Edit>()

    private var nextNewId = -1L

    /** A short notice to show once ("Saved", or why saving failed). */
    var notice by mutableStateOf<String?>(null)

    private var savePage = 0

    /** Adds a new mark and returns its id, so it can be picked straight away. */
    fun addMark(mark: Mark): Long {
        val id = nextNewId--
        edits.add(Edit.Add(mark.copy(id = id, ref = -1)))
        return id
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

    /** A form field filled in (text, chosen option, or a box ticked / "Off"). */
    fun fill(field: String, value: String) {
        edits.add(Edit.Fill(field, value))
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
                val missed = PdfSaver.save(ctx, open.doc.source, dest, plan, open.doc.password)
                edits.clear()
                Thumbnails.forget(ctx, dest.toString())
                load(dest, page, open.doc.password)
                // Changes that couldn't be made stay on screen, to delete or try again
                missed.forEach { m -> edits.add(Edit.Add(m)) }
                val saved = if (target == null) "Saved" else "Saved as a new PDF"
                notice = when (missed.size) {
                    0 -> saved
                    1 -> "$saved. 1 text change couldn't be made (the text may be part of an image). It's still shown, so you can delete it or try again."
                    else -> "$saved. ${missed.size} text changes couldn't be made (the text may be part of an image). They're still shown, so you can delete them or try again."
                }
            } catch (e: SaveFailure) {
                notice = e.message
                load(original, page, open.doc.password) // the marks stay, so nothing is lost
            }
        }
    }

    // ---- Organise pages: turn, delete, move, extract, add other PDFs

    /** Set while the "Organise pages" screen is open. */
    var organiser by mutableStateOf<Organiser?>(null)
        private set

    /** Writing pages out (extract) without leaving the screen. */
    var organiseBusy by mutableStateOf(false)
        private set

    /** [page]: the page being read, to come back to (wherever it moved) afterwards. */
    fun startOrganising(page: Int) {
        val open = reader as? ReaderState.Open ?: return
        savePage = page
        if (edits.isNotEmpty()) {
            notice = "Save your marks first, then organise the pages."
            return
        }
        organiser = Organiser(open.doc)
    }

    fun stopOrganising() {
        val org = organiser ?: return
        organiser = null
        viewModelScope.launch { org.added.forEach { it.doc.close() } }
    }

    /** Adds every page of another PDF at the end. */
    fun addToOrganise(uri: Uri) {
        val org = organiser ?: return
        viewModelScope.launch {
            organiseBusy = true
            try {
                val ctx = getApplication<Application>()
                val (name, _) = withContext(Dispatchers.IO) { describe(uri) }
                val doc = PdfDoc.open(ctx, uri, copyName = "added-${org.added.size + 1}.pdf")
                if (organiser === org) org.add(AddedPdf(uri, name, doc)) else doc.close()
                notice = if (doc.pageCount == 1) "Added 1 page from $name" else "Added ${doc.pageCount} pages from $name"
            } catch (e: OpenFailure) {
                notice = if (e.locked) "That PDF is locked with a password, so its pages can't be added yet." else e.message
            } finally {
                organiseBusy = false
            }
        }
    }

    /** Writes the selected pages, as arranged, to a new PDF; the open one stays as it is. */
    fun extractPages(target: Uri) {
        val org = organiser ?: return
        val open = reader as? ReaderState.Open ?: return
        val pages = org.selectedPages()
        if (pages.isEmpty()) return
        viewModelScope.launch {
            organiseBusy = true
            try {
                PageWriter.write(getApplication(), open.doc.source, org.added.map { it.doc.source }, pages, target, open.doc.password)
                notice = if (pages.size == 1) "Saved 1 page as a new PDF" else "Saved ${pages.size} pages as a new PDF"
                org.selected.clear()
            } catch (e: SaveFailure) {
                notice = e.message
            } finally {
                organiseBusy = false
            }
        }
    }

    /** Saves the new arrangement into the open file ([target] null) or into a new copy. */
    fun saveOrganised(target: Uri?) {
        val org = organiser ?: return
        val open = reader as? ReaderState.Open ?: return
        if (!org.changed) {
            stopOrganising()
            return
        }
        val items = org.items.toList()
        val added = org.added.map { it.doc.source }
        val moved = PagePlan.moved(items)
        val original = Uri.parse(open.uri)
        val dest = target ?: original
        val page = moved[savePage] ?: 0
        organiser = null
        openJob?.cancel()
        reader = ReaderState.Loading(open.name, saving = true)
        openJob = viewModelScope.launch {
            val ctx = getApplication<Application>()
            open.doc.close()
            try {
                PageWriter.write(ctx, open.doc.source, added, items, dest, open.doc.password)
                // Bookmarks follow their pages when the file itself changed
                if (target == null) {
                    saveBookmarksFor(open.uri, bookmarks.mapNotNull { b -> moved[b.page]?.let { b.copy(page = it) } })
                }
                Thumbnails.forget(ctx, dest.toString())
                load(dest, page, open.doc.password)
                notice = if (target == null) "Saved" else "Saved as a new PDF"
            } catch (e: SaveFailure) {
                notice = e.message
                load(original, savePage, open.doc.password)
            } finally {
                org.added.forEach { it.doc.close() }
            }
        }
    }

    fun closeReader() {
        stopOrganising()
        openJob?.cancel()
        closeDoc()
        Unlock.forget(getApplication())
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
        Unlock.forget(getApplication())
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
