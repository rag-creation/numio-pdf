package com.rr.numio.pdf

import com.rr.numio.pdf.edit.Edit
import com.rr.numio.pdf.edit.Edits
import com.rr.numio.pdf.edit.Mark
import com.rr.numio.pdf.edit.MarkColors
import com.rr.numio.pdf.edit.MarkKind
import com.rr.numio.pdf.text.NBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditsTest {
    private val box = listOf(NBox(0.1f, 0.1f, 0.5f, 0.13f))

    private fun saved(ref: Int, page: Int = 0) =
        Mark(page, MarkKind.HIGHLIGHT, box, MarkColors.YELLOW, id = Mark.savedId(page, ref), ref = ref)

    private fun new(id: Long, page: Int = 0) = Mark(page, MarkKind.UNDERLINE, box, MarkColors.BLUE, id = id)

    @Test
    fun newMarksShowOnTheirPageOnly() {
        val edits = listOf(Edit.Add(new(-1, page = 0)), Edit.Add(new(-2, page = 3)))
        assertEquals(listOf(-1L), Edits.apply(emptyList(), edits, 0).map { it.id })
        assertEquals(listOf(-2L), Edits.apply(emptyList(), edits, 3).map { it.id })
    }

    @Test
    fun deletingAndRecolouringSavedMarks() {
        val a = saved(0)
        val b = saved(1)
        val edits = listOf(Edit.Recolor(a, MarkColors.GREEN), Edit.Remove(b))
        val shown = Edits.apply(listOf(a, b), edits, 0)
        assertEquals(listOf(a.id), shown.map { it.id })
        assertEquals(MarkColors.GREEN, shown.single().color)

        val plan = Edits.plan(edits)
        assertEquals(listOf(b.id), plan.removed.map { it.id })
        assertEquals(MarkColors.GREEN, plan.recolored.single().color)
        assertTrue(plan.added.isEmpty())
    }

    @Test
    fun newMarkDeletedBeforeSavingLeavesNothingToSave() {
        val m = new(-5)
        val plan = Edits.plan(listOf(Edit.Add(m), Edit.Recolor(m, MarkColors.PINK), Edit.Remove(m)))
        assertTrue(plan.isEmpty)
    }

    @Test
    fun recolouringANewMarkSavesItInTheNewColour() {
        val m = new(-7)
        val plan = Edits.plan(listOf(Edit.Add(m), Edit.Recolor(m, MarkColors.RED)))
        assertEquals(MarkColors.RED, plan.added.single().color)
    }

    @Test
    fun deletingASavedMarkCancelsItsRecolour() {
        val a = saved(2)
        val plan = Edits.plan(listOf(Edit.Recolor(a, MarkColors.PINK), Edit.Remove(a)))
        assertTrue(plan.recolored.isEmpty())
        assertEquals(1, plan.removed.size)
    }

    @Test
    fun undoIsDroppingTheLastEdit() {
        val a = saved(0)
        val edits = listOf(Edit.Remove(a))
        assertEquals(1, Edits.apply(listOf(a), edits.dropLast(1), 0).size)
    }

    @Test
    fun fingerOnMark() {
        val m = new(-1)
        assertTrue(m.contains(0.3f, 0.115f))
        assertTrue(!m.contains(0.8f, 0.5f))
    }
}
