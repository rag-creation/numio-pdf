package com.rr.numio.pdf

import com.rr.numio.pdf.edit.Edit
import com.rr.numio.pdf.edit.Edits
import com.rr.numio.pdf.form.FieldKind
import com.rr.numio.pdf.form.FormField
import com.rr.numio.pdf.text.NBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormTest {
    private val box = NBox(0.1f, 0.1f, 0.2f, 0.12f)

    @Test
    fun checkboxTogglesBetweenItsOnValueAndOff() {
        val f = FormField("Male", "Male", FieldKind.CHECK, 0, box, "Off", onValue = "Yes")
        assertEquals("Yes", FormField.tapped(f, "Off"))
        assertEquals("Off", FormField.tapped(f, "Yes"))
        assertTrue(f.isOn("Yes"))
        assertFalse(f.isOn("Off"))
    }

    @Test
    fun radioAlwaysPicksItsOwnOption() {
        val a = FormField("Sex", "Sex", FieldKind.RADIO, 0, box, "F", onValue = "M")
        assertEquals("M", FormField.tapped(a, "F"))
        assertEquals("M", FormField.tapped(a, "M"))
    }

    @Test
    fun latestFillWinsAndUndoGoesBack() {
        val edits = listOf(Edit.Fill("Name", "As"), Edit.Fill("City", "RAK"), Edit.Fill("Name", "Asha"))
        assertEquals(mapOf("Name" to "Asha", "City" to "RAK"), Edits.fills(edits))
        assertEquals("As", Edits.fills(edits.dropLast(1))["Name"])
        assertEquals(mapOf("Name" to "Asha", "City" to "RAK"), Edits.plan(edits).fills)
        assertFalse(Edits.plan(edits).isEmpty)
    }
}
