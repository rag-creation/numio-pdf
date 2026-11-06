package com.rr.numio.pdf.edit

import android.content.Context
import android.net.Uri
import com.rr.numio.pdf.text.TextExtractor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.rr.numio.pdf.text.PageGeometry
import java.util.Locale
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Why saving failed, in words for the person. */
class SaveFailure(message: String) : Exception(message)

/**
 * Writes marks into a PDF as standard PDF annotations, so other PDF apps show
 * them too. The new file is built in the app's cache first and only copied over
 * the target once it is complete, so a failed save never damages the original.
 */
object PdfSaver {

    /**
     * Saves, and returns the text changes that couldn't be made (their text
     * wasn't found in the page, e.g. it's part of an image). Everything else
     * is saved.
     */
    suspend fun save(context: Context, source: Uri, target: Uri, plan: SavePlan): List<Mark> = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext
        val missed = ArrayList<Mark>()
        PDFBoxResourceLoader.init(ctx)
        val temp = File(ctx.cacheDir, "saving.pdf")
        try {
            val input = try {
                ctx.contentResolver.openInputStream(source)
            } catch (e: Exception) {
                null
            } ?: throw SaveFailure("Couldn't read the PDF to save it.")

            input.use { stream ->
                val doc = try {
                    PDDocument.load(stream, MemoryUsageSetting.setupTempFileOnly())
                } catch (e: Exception) {
                    throw SaveFailure("This PDF couldn't be changed. It may be locked or damaged.")
                }
                doc.use {
                    val pages = (plan.added + plan.removed + plan.recolored).map { m -> m.page }.toSortedSet()
                    for (index in pages) {
                        if (index !in 0 until it.numberOfPages) continue
                        val page = it.getPage(index)
                        val geo = TextExtractor.geometry(page)
                        // Text on the page itself first, then the marks on top
                        val replace = plan.added.filter { m -> m.page == index && m.kind == MarkKind.REPLACE }
                        if (replace.isNotEmpty()) {
                            val found = try {
                                TextReplacer.apply(it, page, geo, replace)
                            } catch (e: Exception) {
                                throw SaveFailure("This page's text couldn't be changed, so nothing was saved.")
                            }
                            missed += replace.filter { m -> m.id !in found }
                        }
                        val annotations: MutableList<PDAnnotation> = page.annotations

                        // New colours for marks already in the file
                        plan.recolored.filter { m -> m.page == index }.forEach { m ->
                            val a = annotations.getOrNull(m.ref)?.takeIf(::editable) ?: return@forEach
                            a.color = PDColor(rgb(m.color), PDDeviceRGB.INSTANCE)
                            a.setModifiedDate(Calendar.getInstance())
                            if (m.kind == MarkKind.INK) {
                                a.appearance = inkAppearance(it, m, geo)
                            } else {
                                a.appearance = null
                                runCatching { a.constructAppearances() }
                            }
                        }
                        // Deleted marks, from the back so earlier positions stay right
                        plan.removed.filter { m -> m.page == index }.map { m -> m.ref }.sortedDescending().forEach { ref ->
                            if (annotations.getOrNull(ref)?.let(::editable) == true) annotations.removeAt(ref)
                        }
                        plan.added.filter { m -> m.page == index && m.kind != MarkKind.REPLACE }.forEach { m ->
                            annotations.add(
                                when (m.kind) {
                                    MarkKind.INK -> ink(it, m, geo)
                                    MarkKind.TEXT -> freeText(it, m, geo)
                                    MarkKind.NOTE -> note(it, m, geo)
                                    else -> annotation(m, geo)
                                },
                            )
                        }
                        page.annotations = annotations
                    }
                    it.save(temp)
                }
            }

            val out = try {
                ctx.contentResolver.openOutputStream(target, "wt")
            } catch (e: SecurityException) {
                throw SaveFailure("PDF Reader isn't allowed to change this file. Save a copy instead.")
            } catch (e: Exception) {
                try {
                    ctx.contentResolver.openOutputStream(target, "w")
                } catch (e2: Exception) {
                    null
                }
            } ?: throw SaveFailure("Couldn't write to that file.")
            out.use { o -> temp.inputStream().use { it.copyTo(o) } }
        } catch (e: SaveFailure) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw SaveFailure("This PDF is too big to save on this phone.")
        } catch (e: Exception) {
            throw SaveFailure("Saving didn't work, so nothing was changed.")
        } finally {
            temp.delete()
        }
        missed
    }

    /** Only marks PDF Reader shows and edits are ever changed or removed. */
    private val editableTypes = setOf("Highlight", "Underline", "StrikeOut", "Squiggly", "Ink", "FreeText", "Text")

    private fun editable(a: PDAnnotation): Boolean = a.subtype in editableTypes

    private fun rgb(color: Int) = floatArrayOf(
        ((color shr 16) and 0xFF) / 255f,
        ((color shr 8) and 0xFF) / 255f,
        (color and 0xFF) / 255f,
    )

    private fun annotation(m: Mark, geo: PageGeometry): PDAnnotationTextMarkup {
        val subtype = when (m.kind) {
            MarkKind.HIGHLIGHT -> PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
            MarkKind.UNDERLINE -> PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
            MarkKind.STRIKEOUT -> PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT
            MarkKind.INK, MarkKind.TEXT, MarkKind.NOTE, MarkKind.REPLACE -> error("saved by their own functions")
        }
        val quads = FloatArray(m.boxes.size * 8)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        m.boxes.forEachIndexed { i, box ->
            val q = geo.quad(box)
            q.copyInto(quads, i * 8)
            for (k in 0 until 4) {
                minX = minOf(minX, q[k * 2]); maxX = maxOf(maxX, q[k * 2])
                minY = minOf(minY, q[k * 2 + 1]); maxY = maxOf(maxY, q[k * 2 + 1])
            }
        }
        return PDAnnotationTextMarkup(subtype).apply {
            rectangle = PDRectangle(minX, minY, maxX - minX, maxY - minY)
            quadPoints = quads
            color = PDColor(rgb(m.color), PDDeviceRGB.INSTANCE)
            if (m.text.isNotEmpty()) contents = m.text
            isPrinted = true
            setModifiedDate(Calendar.getInstance())
            // Drawing instructions inside the file, so every PDF app shows the mark the same way
            runCatching { constructAppearances() }
        }
    }

    /** A pen drawing as a standard Ink annotation, with its own drawing instructions. */
    private fun ink(doc: PDDocument, m: Mark, geo: PageGeometry): PDAnnotation {
        val dict = COSDictionary()
        dict.setName(COSName.TYPE, "Annot")
        dict.setName(COSName.SUBTYPE, "Ink")
        val a = PDAnnotation.createAnnotation(dict)
        val list = COSArray()
        m.ink.forEach { stroke ->
            val path = COSArray()
            var i = 0
            while (i + 1 < stroke.size) {
                val (x, y) = geo.toPdf(stroke[i], stroke[i + 1])
                path.add(COSFloat(x))
                path.add(COSFloat(y))
                i += 2
            }
            list.add(path)
        }
        dict.setItem(COSName.getPDFName("InkList"), list)
        dict.setItem(COSName.BS, COSDictionary().apply { setFloat(COSName.W, penPoints(m, geo)) })
        a.rectangle = pdfRect(m, geo)
        a.color = PDColor(rgb(m.color), PDDeviceRGB.INSTANCE)
        a.isPrinted = true
        a.setModifiedDate(Calendar.getInstance())
        a.appearance = inkAppearance(doc, m, geo)
        return a
    }

    private fun penPoints(m: Mark, geo: PageGeometry) = (m.width * geo.shownWidth).coerceAtLeast(0.5f)

    /** The drawing's box in PDF points (the strokes' box already includes the pen width). */
    private fun pdfRect(m: Mark, geo: PageGeometry): PDRectangle {
        val q = geo.quad(m.boxes.first())
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (k in 0 until 4) {
            minX = minOf(minX, q[k * 2]); maxX = maxOf(maxX, q[k * 2])
            minY = minOf(minY, q[k * 2 + 1]); maxY = maxOf(maxY, q[k * 2 + 1])
        }
        return PDRectangle(minX, minY, maxX - minX, maxY - minY)
    }

    /**
     * How the drawing looks, written into the file so every PDF app shows the
     * same strokes: round pen ends, the chosen colour and width.
     */
    private fun inkAppearance(doc: PDDocument, m: Mark, geo: PageGeometry): PDAppearanceDictionary {
        val rect = pdfRect(m, geo)
        val stream = PDAppearanceStream(doc)
        stream.bBox = rect
        val c = rgb(m.color)
        val ops = StringBuilder()
        ops.append("q ${n(c[0])} ${n(c[1])} ${n(c[2])} RG ${n(penPoints(m, geo))} w 1 J 1 j\n")
        m.ink.forEach { stroke ->
            if (stroke.size < 2) return@forEach
            val (x0, y0) = geo.toPdf(stroke[0], stroke[1])
            ops.append("${n(x0)} ${n(y0)} m\n")
            if (stroke.size == 2) {
                // A dot: a tiny line so the round pen end shows
                ops.append("${n(x0 + 0.01f)} ${n(y0)} l\n")
            }
            var i = 2
            while (i + 1 < stroke.size) {
                val (x, y) = geo.toPdf(stroke[i], stroke[i + 1])
                ops.append("${n(x)} ${n(y)} l\n")
                i += 2
            }
            ops.append("S\n")
        }
        ops.append("Q\n")
        stream.cosObject.createOutputStream().use { it.write(ops.toString().toByteArray(Charsets.US_ASCII)) }
        return PDAppearanceDictionary().apply { setNormalAppearance(stream) }
    }

    private fun newAnnotation(subtype: String): PDAnnotation {
        val dict = COSDictionary()
        dict.setName(COSName.TYPE, "Annot")
        dict.setName(COSName.SUBTYPE, subtype)
        return PDAnnotation.createAnnotation(dict)
    }

    private fun n(v: Float) = String.format(Locale.US, "%.3f", v)

    /**
     * Typed text as a standard FreeText annotation. The words are stored as
     * text (so other apps can read and edit them), and how they look is stored
     * as a sharp picture drawn by Android, so Malayalam and every other script
     * shows correctly in any PDF app.
     */
    private fun freeText(doc: PDDocument, m: Mark, geo: PageGeometry): PDAnnotation {
        val a = newAnnotation("FreeText")
        val rect = pdfRect(m, geo)
        val box = m.boxes.first()
        val points = m.fontSize * geo.shownWidth
        val c = rgb(m.color)
        a.rectangle = rect
        a.contents = m.text
        a.cosObject.setString(COSName.DA, "/Helv ${n(points)} Tf ${n(c[0])} ${n(c[1])} ${n(c[2])} rg")
        a.cosObject.setItem(COSName.BS, COSDictionary().apply { setFloat(COSName.W, 0f) })
        // Remembered so PDF Reader can show and change the background later.
        // Other apps see it anyway, in the picture of the text box.
        if (m.fill >= 0) a.cosObject.setInt(COSName.getPDFName("NumioFill"), m.fill)
        a.isPrinted = true
        a.setModifiedDate(Calendar.getInstance())

        // Picture of the text at 4 pixels per point, kept within a sensible size
        val scale = 4f
        val wPx = (box.width() * geo.shownWidth * scale).toInt().coerceIn(1, 4096)
        val hPx = (box.height() * geo.shownHeight * scale).toInt().coerceIn(1, 4096)
        var bitmap = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        TextBox.draw(
            Canvas(bitmap), m.text, points * scale, 0xFF000000.toInt() or m.color, 0f, 0f,
            m.fill, wPx.toFloat(), hPx.toFloat(),
        )
        if (geo.turnDegrees != 0) {
            // The page is shown turned, so turn the picture back the other way
            val turned = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(-geo.turnDegrees.toFloat()) }, true,
            )
            bitmap.recycle()
            bitmap = turned
        }
        val image = LosslessFactory.createFromImage(doc, bitmap)
        bitmap.recycle()

        val stream = PDAppearanceStream(doc)
        stream.bBox = rect
        val resources = PDResources()
        val name = resources.add(image)
        stream.resources = resources
        val ops = "q ${n(rect.width)} 0 0 ${n(rect.height)} ${n(rect.lowerLeftX)} ${n(rect.lowerLeftY)} cm /${name.name} Do Q\n"
        stream.cosObject.createOutputStream().use { it.write(ops.toByteArray(Charsets.US_ASCII)) }
        a.appearance = PDAppearanceDictionary().apply { setNormalAppearance(stream) }
        return a
    }

    /** A sticky note: a standard Text annotation with a small note icon drawn in its colour. */
    private fun note(doc: PDDocument, m: Mark, geo: PageGeometry): PDAnnotation {
        val a = newAnnotation("Text")
        val rect = pdfRect(m, geo)
        a.rectangle = rect
        a.contents = m.text
        a.color = PDColor(rgb(m.color), PDDeviceRGB.INSTANCE)
        a.cosObject.setName(COSName.NAME, "Comment")
        a.isPrinted = true
        a.setModifiedDate(Calendar.getInstance())

        val c = rgb(m.color)
        val x = rect.lowerLeftX
        val y = rect.lowerLeftY
        val w = rect.width
        val h = rect.height
        val ops = StringBuilder()
        ops.append("q ${n(c[0])} ${n(c[1])} ${n(c[2])} rg 0.1 0.1 0.1 RG ${n(w * 0.04f)} w\n")
        ops.append("${n(x + w * 0.08f)} ${n(y + h * 0.08f)} ${n(w * 0.84f)} ${n(h * 0.84f)} re B\n")
        for (k in 0 until 3) {
            val ly = y + h * (0.68f - k * 0.18f)
            ops.append("${n(x + w * 0.25f)} ${n(ly)} m ${n(x + w * (if (k == 2) 0.55f else 0.75f))} ${n(ly)} l S\n")
        }
        ops.append("Q\n")
        val stream = PDAppearanceStream(doc)
        stream.bBox = rect
        stream.cosObject.createOutputStream().use { it.write(ops.toString().toByteArray(Charsets.US_ASCII)) }
        a.appearance = PDAppearanceDictionary().apply { setNormalAppearance(stream) }
        return a
    }
}
