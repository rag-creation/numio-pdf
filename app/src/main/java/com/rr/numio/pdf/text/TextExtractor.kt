package com.rr.numio.pdf.text

import android.content.Context
import android.net.Uri
import com.rr.numio.pdf.edit.Mark
import com.rr.numio.pdf.edit.MarkKind
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.rr.numio.pdf.edit.Ink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDVariableText
import com.rr.numio.pdf.form.FieldKind
import com.rr.numio.pdf.form.FormField
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

    // Form fields, read once for the whole document
    private var fieldsByPage: Map<Int, List<FormField>>? = null

    /** The fillable form boxes on a page (none for most PDFs). */
    suspend fun fields(index: Int): List<FormField> = lock.withLock {
        val all = fieldsByPage ?: withContext(Dispatchers.IO) { readFields() }.also { fieldsByPage = it }
        all[index] ?: emptyList()
    }

    /** Form boxes of a page if already read, without waiting. */
    fun cachedFields(index: Int): List<FormField> = fieldsByPage?.get(index) ?: emptyList()

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
                        val pts = tp.fontSizeInPt.takeIf { it >= 1f } ?: tp.yScale
                        glyphs += Glyph(
                            unicode, box, separator = unicode.isBlank(),
                            size = pts / pageW, baseline = (baseline / pageH).coerceIn(0f, 1f),
                            font = tp.font?.name ?: "",
                        )
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
                when (any.subtype) {
                    "Ink" -> return@mapNotNull readInk(any, index, ref, geo)
                    "FreeText" -> return@mapNotNull readText(any, index, ref, geo)
                    "Text" -> return@mapNotNull readNote(any, index, ref, geo)
                }
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

    /** A pen drawing saved in the file (by this app or any other). */
    private fun isSignature(a: PDAnnotation) = a.cosObject.getBoolean(COSName.getPDFName("NumioSign"), false)

    private fun readInk(a: PDAnnotation, page: Int, ref: Int, geo: PageGeometry): Mark? {
        val dict = a.cosObject
        val list = dict.getDictionaryObject(COSName.getPDFName("InkList")) as? COSArray ?: return null
        val strokes = (0 until list.size()).mapNotNull { i ->
            val path = list.getObject(i) as? COSArray ?: return@mapNotNull null
            val pts = FloatArray(path.size() / 2 * 2)
            var k = 0
            while (k + 1 < path.size()) {
                val x = (path.getObject(k) as? COSNumber)?.floatValue() ?: return@mapNotNull null
                val y = (path.getObject(k + 1) as? COSNumber)?.floatValue() ?: return@mapNotNull null
                val (u, v) = geo.toShown(x, y)
                pts[k] = u
                pts[k + 1] = v
                k += 2
            }
            pts.takeIf { it.size >= 2 }
        }
        if (strokes.isEmpty()) return null
        val bs = dict.getDictionaryObject(COSName.BS) as? COSDictionary
        val points = bs?.getFloat(COSName.W, 1f) ?: 1f
        val width = (points / geo.shownWidth).coerceIn(0.0005f, 0.05f)
        val color = runCatching { a.color?.toRGB() }.getOrNull() ?: 0x1A1A1A
        if (isSignature(a)) {
            // Back to a signature: strokes as fractions of its own box
            val b = rectBox(a, geo) ?: return null
            val bw = b.width().takeIf { it > 0f } ?: return null
            val bh = b.height().takeIf { it > 0f } ?: return null
            val inBox = strokes.map { s ->
                FloatArray(s.size) { i -> if (i % 2 == 0) (s[i] - b.left) / bw else (s[i] - b.top) / bh }
            }
            return Mark(
                page, MarkKind.SIGN, listOf(b), color and 0xFFFFFF, "",
                Mark.savedId(page, ref), ref, inBox, width / bw,
            )
        }
        return Mark(
            page, MarkKind.INK, listOf(Ink.bounds(strokes, width)), color and 0xFFFFFF, "",
            Mark.savedId(page, ref), ref, strokes, width,
        )
    }

    private fun rectBox(a: PDAnnotation, geo: PageGeometry): NBox? {
        val r = a.rectangle ?: return null
        return geo.boxAround(
            floatArrayOf(r.lowerLeftX, r.upperRightY, r.upperRightX, r.upperRightY, r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.lowerLeftY),
        )
    }

    /** Typed text on the page. Its size and colour come from the /DA setting ("/Helv 12 Tf 0 0 0 rg"). */
    private fun readText(a: PDAnnotation, page: Int, ref: Int, geo: PageGeometry): Mark? {
        val box = rectBox(a, geo) ?: return null
        if (isSignature(a)) {
            return Mark(
                page, MarkKind.SIGN, listOf(box), 0x111111, a.contents ?: "", Mark.savedId(page, ref), ref,
                fontName = a.cosObject.getString(COSName.getPDFName("NumioFont")) ?: "cursive",
            )
        }
        val da = a.cosObject.getString(COSName.DA) ?: ""
        val points = Regex("""([0-9.]+)\s+Tf""").find(da)?.groupValues?.get(1)?.toFloatOrNull()?.takeIf { it > 0f } ?: 12f
        val rgb = Regex("""([0-9.]+)\s+([0-9.]+)\s+([0-9.]+)\s+rg""").find(da)?.groupValues
        val gray = Regex("""([0-9.]+)\s+g(\s|$)""").find(da)?.groupValues?.get(1)?.toFloatOrNull()
        fun c(v: Float) = (v.coerceIn(0f, 1f) * 255).toInt()
        val color = when {
            rgb != null -> (c(rgb[1].toFloat()) shl 16) or (c(rgb[2].toFloat()) shl 8) or c(rgb[3].toFloat())
            gray != null -> (c(gray) shl 16) or (c(gray) shl 8) or c(gray)
            else -> 0x000000
        }
        return Mark(
            page, MarkKind.TEXT, listOf(box), color, a.contents ?: "",
            Mark.savedId(page, ref), ref, fontSize = (points / geo.shownWidth).coerceIn(0.005f, 0.2f),
            fill = a.cosObject.getInt(COSName.getPDFName("NumioFill"), -1),
        )
    }

    /** A sticky note: shown as PDF Reader's own note icon at the note's top-left corner. */
    private fun readNote(a: PDAnnotation, page: Int, ref: Int, geo: PageGeometry): Mark? {
        val r = rectBox(a, geo) ?: return null
        val aspect = geo.shownHeight / geo.shownWidth
        val box = NBox(r.left, r.top, r.left + Mark.NOTE_SIZE, r.top + Mark.NOTE_SIZE / aspect)
        val color = runCatching { a.color?.toRGB() }.getOrNull() ?: 0xFFD43B
        return Mark(page, MarkKind.NOTE, listOf(box), color and 0xFFFFFF, a.contents ?: "", Mark.savedId(page, ref), ref)
    }

    private fun readFields(): Map<Int, List<FormField>> {
        val d = load() ?: return emptyMap()
        return try {
            val form = d.documentCatalog.acroForm ?: return emptyMap()
            // Which field each box belongs to
            val owner = HashMap<COSDictionary, PDTerminalField>()
            for (f in form.fieldTree) {
                if (f is PDTerminalField) f.widgets.forEach { w -> owner[w.cosObject] = f }
            }
            if (owner.isEmpty()) return emptyMap()
            val out = HashMap<Int, MutableList<FormField>>()
            d.pages.forEachIndexed { index, page ->
                val geo = geometry(page)
                val shownW = geo.shownWidth
                page.annotations.forEach { a ->
                    val w = a as? PDAnnotationWidget ?: return@forEach
                    val f = owner[w.cosObject] ?: return@forEach
                    val r = w.rectangle ?: return@forEach
                    val box = geo.boxAround(
                        floatArrayOf(r.lowerLeftX, r.upperRightY, r.upperRightX, r.upperRightY, r.lowerLeftX, r.lowerLeftY, r.upperRightX, r.lowerLeftY),
                    )
                    if (box.width() <= 0f || box.height() <= 0f) return@forEach
                    val kind = when (f) {
                        is PDTextField -> FieldKind.TEXT
                        is PDCheckBox -> FieldKind.CHECK
                        is PDRadioButton -> FieldKind.RADIO
                        is PDChoice -> FieldKind.CHOICE
                        else -> return@forEach // push buttons and signatures aren't filled here
                    }
                    // The name this box stands for when ticked: its appearance other than "Off"
                    val onValue = runCatching {
                        w.appearance?.normalAppearance?.subDictionary?.keys?.map { it.name }?.firstOrNull { it != "Off" }
                    }.getOrNull() ?: ""
                    val da = (f as? PDVariableText)?.defaultAppearance ?: ""
                    val points = Regex("""([0-9.]+)\s+Tf""").find(da)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                    val value = when (kind) {
                        // A ticked box's value is its appearance state; that's what's drawn
                        FieldKind.CHECK, FieldKind.RADIO ->
                            w.appearanceState?.name?.takeIf { it != "Off" } ?: f.valueAsString.takeIf { it == onValue } ?: "Off"
                        else -> f.valueAsString ?: ""
                    }
                    out.getOrPut(index) { ArrayList() } += FormField(
                        name = f.fullyQualifiedName ?: return@forEach,
                        label = f.alternateFieldName?.takeIf { it.isNotBlank() } ?: f.partialName ?: "Field",
                        kind = kind,
                        page = index,
                        box = box,
                        value = value,
                        onValue = onValue,
                        options = (f as? PDChoice)?.optionsDisplayValues ?: emptyList(),
                        multiline = (f as? PDTextField)?.isMultiline == true,
                        readOnly = f.isReadOnly,
                        fontSize = if (points > 0f) points / shownW else 0f,
                        maxLength = (f as? PDTextField)?.maxLen?.coerceAtLeast(0) ?: 0,
                    )
                }
            }
            out
        } catch (e: Throwable) {
            emptyMap()
        }
    }

    companion object {
        fun geometry(page: PDPage): PageGeometry {
            val c = page.cropBox
            return PageGeometry(c.lowerLeftX, c.lowerLeftY, c.upperRightX, c.upperRightY, page.rotation)
        }
    }
}
