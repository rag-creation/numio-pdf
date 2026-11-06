package com.rr.numio.pdf.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import com.rr.numio.pdf.text.PageGeometry
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorN
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor
import com.tom_roush.pdfbox.contentstream.operator.state.Concatenate
import com.tom_roush.pdfbox.contentstream.operator.state.Restore
import com.tom_roush.pdfbox.contentstream.operator.state.Save
import com.tom_roush.pdfbox.contentstream.operator.state.SetGraphicsStateParameters
import com.tom_roush.pdfbox.contentstream.operator.text.BeginText
import com.tom_roush.pdfbox.contentstream.operator.text.EndText
import com.tom_roush.pdfbox.contentstream.operator.text.MoveText
import com.tom_roush.pdfbox.contentstream.operator.text.MoveTextSetLeading
import com.tom_roush.pdfbox.contentstream.operator.text.NextLine
import com.tom_roush.pdfbox.contentstream.operator.text.SetCharSpacing
import com.tom_roush.pdfbox.contentstream.operator.text.SetFontAndSize
import com.tom_roush.pdfbox.contentstream.operator.state.SetMatrix
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextHorizontalScaling
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextLeading
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextRenderingMode
import com.tom_roush.pdfbox.contentstream.operator.text.SetTextRise
import com.tom_roush.pdfbox.contentstream.operator.text.SetWordSpacing
import com.tom_roush.pdfbox.contentstream.operator.text.ShowText
import com.tom_roush.pdfbox.contentstream.operator.text.ShowTextAdjusted
import com.tom_roush.pdfbox.contentstream.operator.text.ShowTextLine
import com.tom_roush.pdfbox.contentstream.operator.text.ShowTextLineAndSpace
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import com.tom_roush.pdfbox.pdmodel.font.PDVectorFont
import kotlin.math.sqrt

/**
 * Really changes text on a page, like "Edit text" in desktop PDF editors:
 * the letters under a [MarkKind.REPLACE] mark are taken out of the page's
 * drawing instructions (everything else stays exactly where it was), and the
 * new words are written in their place, at the same size and colour, in the
 * closest standard font. With no new words it simply removes the text, which
 * is real redaction: the old letters are gone from the file.
 */
object TextReplacer {

    /** Returns how many of [marks] found text to change on the page. */
    fun apply(doc: PDDocument, page: PDPage, geo: PageGeometry, marks: List<Mark>): Int {
        if (marks.isEmpty()) return 0
        val finder = Finder(geo, marks)
        finder.processPage(page)
        val edited = finder.pieces.filter { (_, list) ->
            list.any { p -> p.usable && p.glyphs.any { it.markId != null } }
        }
        if (edited.isEmpty()) return 0

        // Rewrite the page's drawing instructions without the chosen letters
        val parser = PDFStreamParser(page)
        parser.parse()
        val out = ArrayList<Any>()
        val operands = ArrayList<Any>()
        var index = -1
        for (token in parser.tokens) {
            if (token is Operator) {
                index++
                val pieces = edited[index]
                if (pieces != null) out.addAll(rebuild(token, operands, pieces)) else {
                    out.addAll(operands)
                    out.add(token)
                }
                operands.clear()
            } else {
                operands.add(token)
            }
        }
        out.addAll(operands)
        val stream = PDStream(doc)
        stream.createOutputStream(COSName.FLATE_DECODE).use { ContentStreamWriter(it).writeTokens(out) }
        page.setContents(stream)

        // Write the new words where the old ones started
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            marks.forEach { m ->
                val first = finder.firsts[m.id] ?: return@forEach
                if (m.text.isNotEmpty()) writeText(doc, cs, m, first)
            }
        }
        return finder.firsts.size
    }

    private fun writeText(doc: PDDocument, cs: PDPageContentStream, m: Mark, first: First) {
        val t = first.matrix
        val a = t.getValue(0, 0)
        val b = t.getValue(0, 1)
        val c = t.getValue(1, 0)
        val d = t.getValue(1, 1)
        val sx = sqrt(a * a + b * b).takeIf { it > 0f } ?: 1f
        val sy = sqrt(c * c + d * d).takeIf { it > 0f } ?: 1f
        // The PDF's own font when it has every letter needed (it often does for
        // dates and numbers), otherwise the closest standard one
        val font = if (hasAllLetters(first.font, m.text)) first.font else standardFont(first.fontName)
        // The colour the PDF drew the old text in; if it can't be read, the one seen on screen
        val ink = first.color ?: m.color
        val r = ((ink shr 16) and 0xFF) / 255f
        val g = ((ink shr 8) and 0xFF) / 255f
        val bl = (ink and 0xFF) / 255f

        if (encodable(font, m.text)) {
            cs.beginText()
            cs.setFont(font, sy)
            cs.setNonStrokingColor(r, g, bl)
            cs.setTextMatrix(Matrix(a / sx, b / sx, c / sy, d / sy, t.translateX, t.translateY))
            cs.showText(m.text)
            cs.endText()
        } else {
            // Letters the standard fonts don't have (Malayalam and others):
            // drawn by Android as a sharp picture sitting on the same line
            val scale = 4f
            val box = TextBox.box(m.text, sy / 1000f, 0f, 0f, 1f)
            val wPt = box.width() * 1000f
            val hPt = box.height() * 1000f
            val bitmap = Bitmap.createBitmap(
                (wPt * scale).toInt().coerceIn(1, 4096), (hPt * scale).toInt().coerceIn(1, 4096), Bitmap.Config.ARGB_8888,
            )
            TextBox.draw(Canvas(bitmap), m.text, sy * scale, 0xFF000000.toInt() or ink, 0f, 0f)
            val image = LosslessFactory.createFromImage(doc, bitmap)
            bitmap.recycle()
            cs.drawImage(image, t.translateX, t.translateY - hPt * 0.7f, wPt, hPt)
        }
    }

    private fun hasAllLetters(font: PDFont, text: String): Boolean {
        if (text.contains('\n')) return false
        val vector = font as? PDVectorFont ?: return false
        return try {
            val input = ByteArrayInputStream(font.encode(text))
            while (input.available() > 0) {
                val code = font.readCode(input)
                if (!vector.hasGlyph(code)) return false
                // A cut-down font may have a letter's shape but no width for it,
                // which would print the letters on top of each other
                val width = font.getWidth(code)
                if (width <= 0f) return false
                val drawn = runCatching { font.getWidthFromFont(code) }.getOrNull()
                if (drawn != null && drawn > 0f && kotlin.math.abs(width - drawn) > drawn * 0.15f + 1f) return false
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun encodable(font: PDFont, text: String): Boolean = try {
        text.split('\n').forEach { font.encode(it) }
        !text.contains('\n')
    } catch (e: Exception) {
        false
    }

    /** The standard PDF font closest to the one the text used. */
    private fun standardFont(name: String): PDFont {
        val n = name.lowercase()
        val bold = "bold" in n || "black" in n || "heavy" in n
        val italic = "italic" in n || "oblique" in n
        return when {
            "courier" in n || "mono" in n -> when {
                bold && italic -> PDType1Font.COURIER_BOLD_OBLIQUE
                bold -> PDType1Font.COURIER_BOLD
                italic -> PDType1Font.COURIER_OBLIQUE
                else -> PDType1Font.COURIER
            }
            "times" in n || "serif" in n && "sans" !in n || "georgia" in n || "garamond" in n -> when {
                bold && italic -> PDType1Font.TIMES_BOLD_ITALIC
                bold -> PDType1Font.TIMES_BOLD
                italic -> PDType1Font.TIMES_ITALIC
                else -> PDType1Font.TIMES_ROMAN
            }
            else -> when {
                bold && italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                bold -> PDType1Font.HELVETICA_BOLD
                italic -> PDType1Font.HELVETICA_OBLIQUE
                else -> PDType1Font.HELVETICA
            }
        }
    }

    /** The new version of one text-drawing instruction, with the chosen letters left out. */
    private fun rebuild(op: Operator, operands: List<Any>, pieces: List<Piece>): List<Any> {
        val array = COSArray()
        var next = 0
        fun addPiece() {
            val p = pieces.getOrNull(next++) ?: return
            pieceTokens(p).forEach { array.add(it) }
        }
        return when (op.name) {
            "Tj" -> {
                addPiece()
                listOf(array, Operator.getOperator("TJ"))
            }
            "TJ" -> {
                val original = operands.lastOrNull() as? COSArray ?: return operands + op
                for (i in 0 until original.size()) {
                    when (val e = original.getObject(i)) {
                        is COSString -> addPiece()
                        is COSNumber -> array.add(e)
                        else -> Unit
                    }
                }
                listOf(array, Operator.getOperator("TJ"))
            }
            "'" -> {
                addPiece()
                listOf(Operator.getOperator("T*"), array, Operator.getOperator("TJ"))
            }
            "\"" -> {
                addPiece()
                val aw = operands.getOrNull(0) ?: return operands + op
                val ac = operands.getOrNull(1) ?: return operands + op
                listOf(
                    aw, Operator.getOperator("Tw"), ac, Operator.getOperator("Tc"),
                    Operator.getOperator("T*"), array, Operator.getOperator("TJ"),
                )
            }
            else -> operands + op
        }
    }

    /** Kept letters as strings; each left-out letter becomes a gap of exactly its width. */
    private fun pieceTokens(p: Piece): List<COSBase> {
        if (!p.usable) return listOf(COSString(p.bytes))
        val out = ArrayList<COSBase>()
        val buf = ByteArrayOutputStream()
        var gap = 0f
        p.glyphs.forEachIndexed { i, g ->
            val bytes = p.bytes.copyOfRange(i * p.bytesPerCode, (i + 1) * p.bytesPerCode)
            if (g.markId != null) {
                if (buf.size() > 0) {
                    out.add(COSString(buf.toByteArray()))
                    buf.reset()
                }
                gap += g.gap
            } else {
                if (gap != 0f) {
                    out.add(COSFloat(gap))
                    gap = 0f
                }
                buf.write(bytes)
            }
        }
        if (buf.size() > 0) out.add(COSString(buf.toByteArray()))
        if (gap != 0f) out.add(COSFloat(gap))
        return out
    }

    /** Where the first replaced letter of a mark was drawn, and how. */
    class First(val matrix: Matrix, val font: PDFont, val fontName: String, val color: Int?)

    class Glyph(val markId: Long?, val gap: Float)

    /** One string handed to a text-drawing instruction, and its letters. */
    class Piece(val bytes: ByteArray) {
        val glyphs = ArrayList<Glyph>()
        val bytesPerCode: Int get() = if (glyphs.isNotEmpty() && bytes.size == glyphs.size * 2) 2 else 1

        /** Only edited when every letter's bytes can be told apart (true for almost every PDF). */
        val usable: Boolean get() = glyphs.isNotEmpty() && bytes.size == glyphs.size * bytesPerCode
    }

    /** Walks the page's drawing instructions noting every letter and whether it's under a mark. */
    private class Finder(val geo: PageGeometry, val marks: List<Mark>) : PDFStreamEngine() {
        val pieces = HashMap<Int, MutableList<Piece>>()
        val firsts = HashMap<Long, First>()
        private var index = -1
        private var depth = 0
        private var current: Piece? = null

        init {
            listOf(
                BeginText(), EndText(), MoveText(), MoveTextSetLeading(), NextLine(), SetCharSpacing(),
                SetFontAndSize(), SetMatrix(), SetTextHorizontalScaling(), SetTextLeading(),
                SetTextRenderingMode(), SetTextRise(), SetWordSpacing(), ShowText(), ShowTextAdjusted(),
                ShowTextLine(), ShowTextLineAndSpace(), Concatenate(), Restore(), Save(),
                SetGraphicsStateParameters(), SetNonStrokingColor(), SetNonStrokingColorN(),
                SetNonStrokingColorSpace(), SetNonStrokingDeviceCMYKColor(), SetNonStrokingDeviceGrayColor(),
                SetNonStrokingDeviceRGBColor(),
            ).forEach { addOperator(it) }
        }

        override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
            // Some instructions run others inside them (' runs T* and Tj); count only the outer one
            if (depth == 0) index++
            depth++
            try {
                super.processOperator(operator, operands)
            } finally {
                depth--
            }
        }

        override fun showText(string: ByteArray) {
            val piece = Piece(string)
            pieces.getOrPut(index) { ArrayList() }.add(piece)
            current = piece
            try {
                super.showText(string)
            } finally {
                current = null
            }
        }

        override fun showGlyph(
            textRenderingMatrix: Matrix,
            font: PDFont,
            code: Int,
            unicode: String?,
            displacement: Vector,
        ) {
            val state = graphicsState.textState
            val size = state.fontSize.takeIf { it != 0f } ?: 1f
            val space = if (code == 32 && font !is PDType0Font) state.wordSpacing else 0f
            // A gap in a TJ list moves the pen by -gap/1000 of the font size
            val gap = -(displacement.x * 1000f + (state.characterSpacing + space) * 1000f / size)

            // Middle of the letter, in shown page fractions
            val advance = displacement.x * textRenderingMatrix.scalingFactorX
            val height = textRenderingMatrix.scalingFactorY
            val (u, v) = geo.toShown(
                textRenderingMatrix.translateX + advance / 2f,
                textRenderingMatrix.translateY + height * 0.3f,
            )
            val mark = marks.firstOrNull { m ->
                m.boxes.any { b -> u >= b.left - 0.002f && u <= b.right + 0.002f && v >= b.top && v <= b.bottom }
            }
            current?.glyphs?.add(Glyph(mark?.id, gap))
            if (mark != null && mark.id !in firsts) {
                val color = runCatching { graphicsState.nonStrokingColor.toRGB() }.getOrNull()
                firsts[mark.id] = First(textRenderingMatrix.clone(), font, font.name ?: "", color?.and(0xFFFFFF))
            }
        }
    }
}
