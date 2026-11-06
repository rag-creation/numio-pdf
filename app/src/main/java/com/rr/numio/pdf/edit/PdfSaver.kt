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

    suspend fun save(context: Context, source: Uri, target: Uri, plan: SavePlan) = withContext(Dispatchers.IO) {
        val ctx = context.applicationContext
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
                        val annotations: MutableList<PDAnnotation> = page.annotations

                        // New colours for marks already in the file
                        plan.recolored.filter { m -> m.page == index }.forEach { m ->
                            val a = annotations.getOrNull(m.ref) as? PDAnnotationTextMarkup ?: return@forEach
                            a.color = PDColor(rgb(m.color), PDDeviceRGB.INSTANCE)
                            a.appearance = null
                            a.setModifiedDate(Calendar.getInstance())
                            runCatching { a.constructAppearances() }
                        }
                        // Deleted marks, from the back so earlier positions stay right
                        plan.removed.filter { m -> m.page == index }.map { m -> m.ref }.sortedDescending().forEach { ref ->
                            if (annotations.getOrNull(ref) is PDAnnotationTextMarkup) annotations.removeAt(ref)
                        }
                        plan.added.filter { m -> m.page == index }.forEach { m -> annotations.add(annotation(m, geo)) }
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
    }

    private fun rgb(color: Int) = floatArrayOf(
        ((color shr 16) and 0xFF) / 255f,
        ((color shr 8) and 0xFF) / 255f,
        (color and 0xFF) / 255f,
    )

    private fun annotation(m: Mark, geo: com.rr.numio.pdf.text.PageGeometry): PDAnnotationTextMarkup {
        val subtype = when (m.kind) {
            MarkKind.HIGHLIGHT -> PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
            MarkKind.UNDERLINE -> PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
            MarkKind.STRIKEOUT -> PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT
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
}
