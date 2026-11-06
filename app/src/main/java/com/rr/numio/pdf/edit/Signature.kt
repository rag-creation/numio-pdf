package com.rr.numio.pdf.edit

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A signature ready to place: either hand-drawn [strokes] or a typed [text]
 * in a signature [font]. Strokes are fractions of the signature's own box
 * (0..1 across and down), so it can be placed at any size.
 */
data class Signature(
    val strokes: List<FloatArray> = emptyList(),
    val text: String = "",
    /** "cursive", "serif" or "sans" for typed signatures. */
    val font: String = "cursive",
    val color: Int = 0x111111,
    /** Width divided by height. */
    val aspect: Float = 3f,
    /** Pen width as a fraction of the signature's width. */
    val penWidth: Float = 0.012f,
) {
    val drawn: Boolean get() = strokes.isNotEmpty()

    companion object {
        /**
         * Strokes drawn on a pad [padW] × [padH] pixels (as fractions of the pad),
         * cropped to just the ink with a little margin.
         */
        fun fromPad(strokes: List<FloatArray>, padW: Float, padH: Float, color: Int, penPx: Float): Signature? {
            val xs = strokes.flatMap { s -> s.indices.filter { it % 2 == 0 }.map { s[it] * padW } }
            val ys = strokes.flatMap { s -> s.indices.filter { it % 2 == 1 }.map { s[it] * padH } }
            if (xs.isEmpty()) return null
            val m = penPx
            val left = xs.min() - m
            val top = ys.min() - m
            val w = (xs.max() + m - left).coerceAtLeast(1f)
            val h = (ys.max() + m - top).coerceAtLeast(1f)
            val cropped = strokes.map { s ->
                FloatArray(s.size) { i -> if (i % 2 == 0) (s[i] * padW - left) / w else (s[i] * padH - top) / h }
            }
            return Signature(strokes = cropped, color = color, aspect = w / h, penWidth = penPx / w)
        }
    }
}

/** Saved signatures as plain text, one per line. Pure Kotlin, unit-tested. */
object SignatureCodec {
    private const val UTF8 = "UTF-8"

    fun encode(list: List<Signature>): String = list.joinToString("\n") { s ->
        listOf(
            if (s.drawn) "d" else "t",
            s.color.toString(),
            s.aspect.toString(),
            s.penWidth.toString(),
            URLEncoder.encode(s.font, UTF8),
            URLEncoder.encode(s.text, UTF8),
            s.strokes.joinToString(";") { st -> st.joinToString(",") },
        ).joinToString("\t")
    }

    fun decode(text: String): List<Signature> = text.lineSequence().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 7) return@mapNotNull null
        try {
            val strokes = if (p[6].isEmpty()) emptyList() else p[6].split(';').map { st ->
                st.split(',').map { it.toFloat() }.toFloatArray()
            }
            Signature(
                strokes = if (p[0] == "d") strokes else emptyList(),
                text = URLDecoder.decode(p[5], UTF8),
                font = URLDecoder.decode(p[4], UTF8),
                color = p[1].toInt(),
                aspect = p[2].toFloat(),
                penWidth = p[3].toFloat(),
            )
        } catch (e: IllegalArgumentException) {
            null
        }
    }.toList()
}
