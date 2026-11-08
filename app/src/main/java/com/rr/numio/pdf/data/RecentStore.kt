package com.rr.numio.pdf.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The library list, kept in the app's private storage on this phone only. */
class RecentStore(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val _files = MutableStateFlow(RecentCodec.decode(prefs.getString(KEY, "") ?: ""))
    val files: StateFlow<List<RecentFile>> = _files.asStateFlow()

    fun get(uri: String): RecentFile? = _files.value.firstOrNull { it.uri == uri }

    fun opened(uri: String, name: String, sizeBytes: Long, pageCount: Int) =
        save(Library.opened(_files.value, uri, name, sizeBytes, pageCount, System.currentTimeMillis()))

    fun setPage(uri: String, page: Int) {
        if (get(uri)?.lastPage == page) return
        save(Library.withPage(_files.value, uri, page))
    }

    fun toggleStar(uri: String) = save(Library.toggleStar(_files.value, uri))

    fun setPrivate(uri: String, value: Boolean) = save(Library.setPrivate(_files.value, uri, value))

    fun remove(uri: String) = save(Library.remove(_files.value, uri))

    fun clear() = save(emptyList())

    private fun save(files: List<RecentFile>) {
        _files.value = files
        prefs.edit().putString(KEY, RecentCodec.encode(files)).apply()
    }

    private companion object {
        const val KEY = "files"
    }
}
