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
    fun box(text: String, fontSize: Float, x: Float, y: Float, aspect: Float): NBox {
        val p = paint(1000f * fontSize, 0)
        val lines = text.split('\n')
        val w = (lines.maxOf { p.measureText(it) } / 1000f + 2 * PAD * fontSize).coerceAtMost(1f)
        val h = ((lines.size * LINE * fontSize + 2 * PAD * fontSize) / aspect).coerceAtMost(1f)
        val left = if (x + w > 1f) (1f - w).coerceAtLeast(0f) else x
        val top = if (y + h > 1f) (1f - h).coerceAtLeast(0f) else y
        return NBox(left, top, left + w, top + h)
    }

    /** A sticky note's icon placed at ([x], [y]), kept inside the page. */
    fun noteBox(x: Float, y: Float, aspect: Float): NBox {
        val w = Mark.NOTE_SIZE
        val h = Mark.NOTE_SIZE / aspect
        val left = x.coerceIn(0f, 1f - w)
        val top = y.coerceIn(0f, 1f - h)
        return NBox(left, top, left + w, top + h)
    }

    /** Draws [text] with its top-left at ([left], [top]); [fontPx] is the text size in pixels. */
    fun draw(canvas: Canvas, text: String, fontPx: Float, color: Int, left: Float, top: Float) {
        val p = paint(fontPx, color)
        val pad = PAD * fontPx
        val ascent = -p.fontMetrics.ascent
        text.split('\n').forEachIndexed { i, line ->
            canvas.drawText(line, left + pad, top + pad + ascent + i * LINE * fontPx, p)
        }
    }
}
