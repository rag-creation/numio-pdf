package com.rr.numio.pdf.text

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Finds where the text sits on each page, using PdfBox (Apache-2.0), fully
 * on the phone. The file is only read for text the first time someone
 * long-presses, so plain reading stays as light as before.
 */
class TextExtractor(private val context: Context, private val source: Uri) {
    private val lock = Mutex()
    private var doc: PDDocument? = null
    private var failed = false
    private var closed = false
    private val pages = HashMap<Int, PageText>()

    /** The page's text, or an empty page if it has none (a scan) or can't be read. */
    suspend fun page(index: Int): PageText = lock.withLock {
        pages[index] ?: withContext(Dispatchers.IO) { extract(index) }.also { pages[index] = it }
    }

    suspend fun close() {
        lock.withLock {
            closed = true
            pages.clear()
            withContext(Dispatchers.IO) { runCatching { doc?.close() } }
            doc = null
        }
    }

    private fun load(): PDDocument? {
        if (closed || failed) return null
        doc?.let { return it }
        return try {
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(source)?.use { PDDocument.load(it) }
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
                            bottom = ((baseline + h * 0.3f) / pageH).coerceIn(0f, 1f),
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
}
