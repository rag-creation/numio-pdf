package com.rr.numio.pdf.pages

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import com.rr.numio.pdf.pdf.PdfDoc

/** Another PDF whose pages were added. */
data class AddedPdf(val uri: Uri, val name: String, val doc: PdfDoc)

/** The "Organise pages" screen's state: the arrangement and what's selected. */
class Organiser(val doc: PdfDoc) {
    val items = mutableStateListOf<PageItem>().apply { addAll(PagePlan.start(doc.pageCount)) }
    val added = mutableStateListOf<AddedPdf>()
    val selected = mutableStateListOf<Long>()
    private var nextKey = doc.pageCount.toLong()

    val changed: Boolean get() = PagePlan.changed(items, doc.pageCount)

    /** The PDF a page comes from, to draw its picture. */
    fun docFor(item: PageItem): PdfDoc? = if (item.source == 0) doc else added.getOrNull(item.source - 1)?.doc

    private fun set(list: List<PageItem>) {
        items.clear()
        items.addAll(list)
    }

    fun toggle(key: Long) {
        if (!selected.remove(key)) selected.add(key)
    }

    fun selectAll() {
        if (selected.size == items.size) selected.clear() else {
            selected.clear()
            selected.addAll(items.map { it.key })
        }
    }

    fun rotate(quarters: Int) = set(PagePlan.rotate(items, selected.toSet(), quarters))

    /** False if that would leave no pages at all. */
    fun delete(): Boolean {
        if (selected.size >= items.size) return false
        set(PagePlan.delete(items, selected.toSet()))
        selected.clear()
        return true
    }

    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        items.add(to, items.removeAt(from))
    }

    fun add(pdf: AddedPdf) {
        added += pdf
        set(PagePlan.add(items, added.size, pdf.doc.pageCount, nextKey))
        nextKey += pdf.doc.pageCount
    }

    fun selectedPages(): List<PageItem> = PagePlan.selected(items, selected.toSet())
}
