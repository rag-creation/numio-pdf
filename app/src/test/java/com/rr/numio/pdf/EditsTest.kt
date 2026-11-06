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

    @Test
    fun changingASavedTextBoxReplacesIt() {
        val old = Mark(0, MarkKind.TEXT, box, 0x000000, "Hello", id = Mark.savedId(0, 4), ref = 4, fontSize = 0.02f)
        val new = old.copy(text = "Hello there")
        val edits = listOf(Edit.Change(old, new))
        assertEquals("Hello there", Edits.apply(listOf(old), edits, 0).single().text)
        val plan = Edits.plan(edits)
        assertEquals(listOf(4), plan.removed.map { it.ref })
        assertEquals("Hello there", plan.added.single().text)
        assertEquals(-1, plan.added.single().ref)
    }

    @Test
    fun changedThenDeletedSavedMarkIsJustRemoved() {
        val old = Mark(0, MarkKind.NOTE, box, MarkColors.YELLOW, "Call HR", id = Mark.savedId(0, 1), ref = 1)
        val changed = old.copy(text = "Call HR on Monday")
        val plan = Edits.plan(listOf(Edit.Change(old, changed), Edit.Remove(changed)))
        assertTrue(plan.added.isEmpty())
        assertEquals(1, plan.removed.size)
    }

    @Test
    fun changingANewNoteKeepsItNew() {
        val note = Mark(0, MarkKind.NOTE, box, MarkColors.YELLOW, "a", id = -3)
        val plan = Edits.plan(listOf(Edit.Add(note), Edit.Change(note, note.copy(text = "b"))))
        assertEquals("b", plan.added.single().text)
        assertTrue(plan.removed.isEmpty())
    }
}
