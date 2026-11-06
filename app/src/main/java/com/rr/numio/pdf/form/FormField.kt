package com.rr.numio.pdf.form

import com.rr.numio.pdf.text.NBox

enum class FieldKind { TEXT, CHECK, RADIO, CHOICE }

/**
 * One box of a fillable PDF form (a field's "widget") on a page.
 * A field can have several boxes, e.g. one per radio option; they share [name].
 */
data class FormField(
    /** The field's full name in the form, used to save its value. */
    val name: String,
    /** What to call it on screen: the form's own tooltip text if it has one. */
    val label: String,
    val kind: FieldKind,
    val page: Int,
    val box: NBox,
    /** Current value: the text, the chosen option, or the "on" name of the ticked box ("Off" for none). */
    val value: String,
    /** Checkbox / radio: the value this box stands for when ticked. */
    val onValue: String = "",
    val options: List<String> = emptyList(),
    val multiline: Boolean = false,
    val readOnly: Boolean = false,
    /** Text size as a fraction of the page width; 0 means fit the box. */
    val fontSize: Float = 0f,
    /** Most characters allowed; 0 for no limit. */
    val maxLength: Int = 0,
) {
    /** Ticked, for a checkbox or radio box, given the field's current [value]. */
    fun isOn(value: String): Boolean = value.isNotEmpty() && value != "Off" && value == onValue

    fun contains(x: Float, y: Float): Boolean =
        x >= box.left && x <= box.right && y >= box.top && y <= box.bottom

    companion object {
        /** The value after tapping a checkbox (on ↔ Off) or a radio box (always on). */
        fun tapped(field: FormField, current: String): String = when (field.kind) {
            FieldKind.CHECK -> if (field.isOn(current)) "Off" else field.onValue
            FieldKind.RADIO -> field.onValue
            else -> current
        }
    }
}
