package com.rr.numio.pdf.text

import android.content.Context
import android.net.Uri
import com.rr.numio.pdf.edit.Mark
import com.rr.numio.pdf.edit.MarkKind
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Reads what Android's page renderer can't: where the text sits on each page
 * (for selecting) and the highlights / underlines saved in the file (Android
 * doesn't draw those, so PDF Reader draws them itself). Uses PdfBox
 * (Apache-2.0), fully on the phone, with spare data in a temporary file
 * rather than memory so big PDFs don't run the phone out of memory.
 */
class TextExtractor(private val context: Context, private val source: Uri) {
    private val lock = Mutex()
    private var doc: PDDocument? = null
    private var failed = false
    private var closed = false
    private val pages = HashMap<Int, PageText>()
    // Read from the screen thread for taps, so safe to share
    private val marks = java.util.concurrent.ConcurrentHashMap<Int, List<Mark>>()

    /** The page's text, or an empty page if it has none (a scan) or can't be read. */
    suspend fun page(index: Int): PageText = lock.withLock {
        pages[index] ?: withContext(Dispatchers.IO) { extract(index) }.also { pages[index] = it }
    }

    /** Saved marks of a page if already read, without waiting. */
    fun cachedMarks(index: Int): List<Mark> = marks[index] ?: emptyList()

    /** Highlights, underlines and strike-outs saved in the file on this page. */
    suspend fun marks(index: Int): List<Mark> = lock.withLock {
        marks[index] ?: withContext(Dispatchers.IO) { readMarks(index) }.also { marks[index] = it }
    }

    suspend fun close() {
        lock.withLock {
            closed = true
            pages.clear()
            marks.clear()
            withContext(Dispatchers.IO) { runCatching { doc?.close() } }
            doc = null
        }
    }

    private fun load(): PDDocument? {
        if (closed || failed) return null
        doc?.let { return it }
        return try {
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(source)?.use {
                PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly())
            }
                .also { doc = it }
        } catch (e: Throwable) {
            // Out of memory on a huge file, or something PdfBox can't parse:
            // reading still works, there is just no text to select.
            failed = true
            null
        }
    }

    private fun extract(index: Int): PageText {
        val d = load() ?: return PageText(emptyList())
        return try {
            val page = d.getPage(index)
            val crop = page.cropBox
            val turned = page.rotation % 180 != 0
            val pageW = if (turned) crop.height else crop.width
            val pageH = if (turned) crop.width else crop.height
            if (pageW <= 0f || pageH <= 0f) return PageText(emptyList())

            val glyphs = ArrayList<Glyph>()
            fun separator(text: String) {
                val last = glyphs.lastOrNull() ?: return
                if (last.separator && (last.text == "\n" || text == " ")) return
                glyphs += Glyph(text, last.box, separator = true)
            }

            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                    textPositions?.forEach { tp ->
                        val unicode = tp.unicode ?: return@forEach
                        if (unicode.isEmpty()) return@forEach
                        val h = maxOf(tp.heightDir, tp.fontSizeInPt * 0.7f)
                        val left = tp.xDirAdj
                        val baseline = tp.yDirAdj
                        val box = NBox(
                            left = (left / pageW).coerceIn(0f, 1f),
                            top = ((baseline - h * 1.15f) / pageH).coerceIn(0f, 1f),
                            right = ((left + tp.widthDirAdj) / pageW).coerceIn(0f, 1f),
                            bottom = ((baseline + maxOf(h * 0.3f, tp.fontSizeInPt * 0.26f)) / pageH).coerceIn(0f, 1f),
                        )
                        glyphs += Glyph(unicode, box, separator = unicode.isBlank())
                    }
                }

                override fun writeWordSeparator() = separator(" ")

                override fun writeLineSeparator() = separator("\n")
            }
            stripper.sortByPosition = true
            stripper.startPage = index + 1
            stripper.endPage = index + 1
            stripper.getText(d)
            PageText(glyphs)
        } catch (e: Throwable) {
            PageText(emptyList())
        }
    }

    private fun readMarks(index: Int): List<Mark> {
        val d = load() ?: return emptyList()
        return try {
            val page = d.getPage(index)
            val geo = geometry(page)
            page.annotations.withIndex().mapNotNull { (ref, any) ->
                val a = any as? PDAnnotationTextMarkup ?: return@mapNotNull null
                val kind = when (a.subtype) {
                    PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT -> MarkKind.HIGHLIGHT
                    PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE, PDAnnotationTextMarkup.SUB_TYPE_SQUIGGLY -> MarkKind.UNDERLINE
                    PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT -> MarkKind.STRIKEOUT
                    else -> return@mapNotNull null
                }
                val q = a.quadPoints
                val boxes = if (q != null && q.size >= 8) {
                    (0 until q.size / 8).map { geo.boxAround(q, it * 8) }
                } else {
                    val r = a.rectangle ?: return@mapNotNull null
                    listOf(geo.boxAround(floatArrayOf(r.lowerLeftX, r.upperRightY, r.upperRightX, r.upperRightY, r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.lowerLeftY)))
                }
                val color = runCatching { a.color?.toRGB() }.getOrNull() ?: 0xFFD43B
                Mark(index, kind, boxes, color and 0xFFFFFF, a.contents ?: "", Mark.savedId(index, ref), ref)
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    companion object {
        fun geometry(page: PDPage): PageGeometry {
            val c = page.cropBox
            return PageGeometry(c.lowerLeftX, c.lowerLeftY, c.upperRightX, c.upperRightY, page.rotation)
        }
    }
}
