package com.rr.numio.pdf

import com.rr.numio.pdf.edit.Signature
import com.rr.numio.pdf.edit.SignatureCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureTest {
    @Test
    fun padStrokesAreCroppedToTheInk() {
        // A line across the middle of a 300 x 100 pad, from x=60 to x=240 at y=50
        val s = Signature.fromPad(listOf(floatArrayOf(0.2f, 0.5f, 0.8f, 0.5f)), 300f, 100f, 0x111111, 5f)!!
        // 180 px of ink + 5 px each side = 190 wide, 10 high
        assertEquals(19f, s.aspect, 0.01f)
        assertArrayEquals(floatArrayOf(5f / 190f, 0.5f, 185f / 190f, 0.5f), s.strokes.single(), 0.0001f)
        assertEquals(5f / 190f, s.penWidth, 0.0001f)
    }

    @Test
    fun emptyPadGivesNoSignature() {
        assertNull(Signature.fromPad(emptyList(), 300f, 100f, 0, 4f))
    }

    @Test
    fun codecKeepsDrawnAndTypedSignatures() {
        val drawn = Signature(strokes = listOf(floatArrayOf(0f, 0.5f, 1f, 0.4f), floatArrayOf(0.3f, 0.1f)), color = 0x1D4ED8, aspect = 2.5f, penWidth = 0.02f)
        val typed = Signature(text = "Asha\tK. ക്ഷ", font = "serif", aspect = 4f)
        val back = SignatureCodec.decode(SignatureCodec.encode(listOf(drawn, typed)))
        assertEquals(2, back.size)
        assertTrue(back[0].drawn)
        assertArrayEquals(drawn.strokes[1], back[0].strokes[1], 0f)
        assertEquals(0x1D4ED8, back[0].color)
        assertEquals("Asha\tK. ക്ഷ", back[1].text)
        assertEquals("serif", back[1].font)
        assertEquals(4f, back[1].aspect, 0f)
    }
}
