package com.rr.numio.pdf.edit

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.rr.numio.pdf.text.NBox

/**
 * Sizing and drawing typed text. Android's own text drawing is used both on
 * screen and when saving, so Malayalam (and any other script) is shaped
 * correctly everywhere.
 */
object TextBox {
    /** Space around the words, as a part of the text size. */
    private const val PAD = 0.35f
    private const val LINE = 1.3f

    private fun paint(sizePx: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = sizePx
        this.color = color
        typeface = Typeface.DEFAULT
    }

    /**
     * Where a text box goes when placed at ([x], [y]) on a page whose height is
     * [aspect] times its width. Moved back inside the page if it would hang off.
     */
    fun box(text: String, fontSize: Float, x: Float, y: Float, aspect: Float, centred: Boolean = false): NBox {
        val p = paint(1000f * fontSize, 0)
        val lines = text.split('\n')
        val w = (lines.maxOf { p.measureText(it) } / 1000f + 2 * PAD * fontSize).coerceAtMost(1f)
        val h = ((lines.size * LINE * fontSize + 2 * PAD * fontSize) / aspect).coerceAtMost(1f)
        // Centred: the middle of the text lands under the finger
        val left = (if (centred) x - w / 2f else x).coerceIn(0f, (1f - w).coerceAtLeast(0f))
        val top = (if (centred) y - h / 2f else y).coerceIn(0f, (1f - h).coerceAtLeast(0f))
        return NBox(left, top, left + w, top + h)
    }

    /** A sticky note's icon placed at ([x], [y]), kept inside the page. */
    fun noteBox(x: Float, y: Float, aspect: Float, centred: Boolean = false): NBox {
        val w = Mark.NOTE_SIZE
        val h = Mark.NOTE_SIZE / aspect
        val left = (if (centred) x - w / 2f else x).coerceIn(0f, 1f - w)
        val top = (if (centred) y - h / 2f else y).coerceIn(0f, 1f - h)
        return NBox(left, top, left + w, top + h)
    }

    /** Smallest and largest text, as fractions of the page width. */
    const val MIN_FONT = 0.008f
    const val MAX_FONT = 0.15f

    /**
     * [mark] moved by ([dx], [dy]) and its text scaled by [zoom] around its
     * middle (text boxes only), kept inside the page.
     */
    fun moved(mark: Mark, dx: Float, dy: Float, zoom: Float, aspect: Float): Mark {
        val b = mark.boxes.first()
        return when (mark.kind) {
            MarkKind.TEXT -> {
                val font = (mark.fontSize * zoom).coerceIn(MIN_FONT, MAX_FONT)
                val cx = (b.left + b.right) / 2f + dx
                val cy = (b.top + b.bottom) / 2f + dy
                mark.copy(fontSize = font, boxes = listOf(box(mark.text, font, cx, cy, aspect, centred = true)))
            }
            MarkKind.NOTE -> mark.copy(boxes = listOf(noteBox(b.left + dx, b.top + dy, aspect)))
            else -> mark
        }
    }

    /**
     * Draws [text] with its top-left at ([left], [top]); [fontPx] is the text size
     * in pixels. With a [fill] colour, the whole [width] × [height] box is painted first.
     */
    fun draw(
        canvas: Canvas,
        text: String,
        fontPx: Float,
        color: Int,
        left: Float,
        top: Float,
        fill: Int = -1,
        width: Float = 0f,
        height: Float = 0f,
    ) {
        if (fill >= 0 && width > 0f && height > 0f) {
            val bg = Paint().apply { this.color = 0xFF000000.toInt() or fill }
            canvas.drawRect(left, top, left + width, top + height, bg)
        }
        val p = paint(fontPx, color)
        val pad = PAD * fontPx
        val ascent = -p.fontMetrics.ascent
        text.split('\n').forEachIndexed { i, line ->
            canvas.drawText(line, left + pad, top + pad + ascent + i * LINE * fontPx, p)
        }
    }
}
