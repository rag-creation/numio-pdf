package com.rr.numio.pdf.ui.reader

import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Build
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rr.numio.pdf.form.FieldKind
import com.rr.numio.pdf.form.FormField
import com.rr.numio.pdf.ui.theme.Numio

/** Pale blue over fillable boxes, so it's clear what can be filled. */
private val FieldTint = Color(0x2E4D8DFF)
private val Ink = Color(0xFF111111)

/**
 * Draws a form's boxes and what's in them. Android's renderer leaves form
 * fields out, so without this, values typed in any app would be invisible.
 * [fills] are values changed since the last save, by field name.
 */
@Composable
fun BoxScope.FormOverlay(fields: List<FormField>, fills: Map<String, String>, page: Bitmap?) {
    Canvas(Modifier.matchParentSize()) {
        val canvas = drawContext.canvas.nativeCanvas
        fields.forEach { f ->
            val l = f.box.left * size.width
            val t = f.box.top * size.height
            val w = f.box.width() * size.width
            val h = f.box.height() * size.height
            val pending = fills[f.name]
            if (SYSTEM_DRAWS_FORMS) {
                // Android 15+ draws saved values itself; only a changed one needs
                // the old value hidden (in the box's own colour) and the new one drawn
                if (!f.readOnly) drawRect(FieldTint, Offset(l, t), Size(w, h))
                if (pending == null) return@forEach
                val inset = if (f.kind == FieldKind.CHECK || f.kind == FieldKind.RADIO) minOf(w, h) * 0.14f else 1f
                drawRect(fieldBackground(page, f), Offset(l + inset, t + inset), Size(w - inset * 2, h - inset * 2))
            } else if (!f.readOnly) {
                drawRect(FieldTint, Offset(l, t), Size(w, h))
            }
            val value = pending ?: f.value
            when (f.kind) {
                FieldKind.CHECK -> if (f.isOn(value)) {
                    val s = minOf(w, h)
                    val cx = l + w / 2f
                    val cy = t + h / 2f
                    val tick = Path().apply {
                        moveTo(cx - s * 0.32f, cy + s * 0.02f)
                        lineTo(cx - s * 0.08f, cy + s * 0.26f)
                        lineTo(cx + s * 0.34f, cy - s * 0.26f)
                    }
                    drawPath(tick, Ink, style = Stroke(s * 0.14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                FieldKind.RADIO -> if (f.isOn(value)) {
                    drawCircle(Ink, radius = minOf(w, h) * 0.25f, center = Offset(l + w / 2f, t + h / 2f))
                }
                FieldKind.TEXT, FieldKind.CHOICE -> if (value.isNotEmpty()) {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF111111.toInt()
                        typeface = Typeface.SANS_SERIF
                    }
                    val pad = minOf(h * 0.12f, 2.dp.toPx())
                    canvas.save()
                    canvas.clipRect(l, t, l + w, t + h)
                    if (f.multiline) {
                        paint.textSize = if (f.fontSize > 0f) f.fontSize * size.width else 0.017f * size.width
                        var y = t + pad - paint.fontMetrics.ascent
                        value.split('\n').forEach { para ->
                            var rest = para
                            do {
                                val n = paint.breakText(rest, true, w - pad * 2, null).coerceAtLeast(1)
                                canvas.drawText(rest.substring(0, minOf(n, rest.length)), l + pad, y, paint)
                                rest = rest.substring(minOf(n, rest.length))
                                y += paint.fontSpacing
                            } while (rest.isNotEmpty() && y < t + h + paint.textSize)
                        }
                    } else {
                        // Fit the text to the box height when the form doesn't set a size
                        paint.textSize = if (f.fontSize > 0f) f.fontSize * size.width else h * 0.62f
                        val fm = paint.fontMetrics
                        val baseline = t + h / 2f - (fm.ascent + fm.descent) / 2f
                        canvas.drawText(value, l + pad, baseline, paint)
                    }
                    canvas.restore()
                }
            }
        }
    }
}

/**
 * From Android 15 the system's PDF renderer draws form fields itself (it
 * didn't before), so PDF Reader only draws values that aren't saved yet.
 */
private val SYSTEM_DRAWS_FORMS = Build.VERSION.SDK_INT >= 35

/** A field's own background colour, read from the drawn page just inside its top edge. */
private fun fieldBackground(page: Bitmap?, f: FormField): Color {
    if (page == null || page.isRecycled) return Color.White
    val y = ((f.box.top + f.box.height() * 0.1f) * page.height).toInt().coerceIn(0, page.height - 1)
    val samples = listOf(0.06f, 0.25f, 0.5f, 0.75f, 0.94f).map { k ->
        val x = ((f.box.left + f.box.width() * k) * page.width).toInt().coerceIn(0, page.width - 1)
        page.getPixel(x, y) or 0xFF000000.toInt()
    }
    // The most common of the samples (ignores one that happens to land on a letter)
    val pick = samples.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: samples[2]
    return Color(pick)
}

/** Typing into a text field. */
@Composable
fun FieldTextDialog(field: FormField, current: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Numio.Surface,
        title = { Text(field.label, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        val one = if (field.multiline) v else v.replace('\n', ' ')
                        text = if (field.maxLength > 0) one.take(field.maxLength) else one
                    },
                    singleLine = !field.multiline,
                    minLines = if (field.multiline) 3 else 1,
                    keyboardOptions = KeyboardOptions(imeAction = if (field.multiline) ImeAction.Default else ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Numio.Amber,
                        cursorColor = Numio.Amber,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (field.maxLength > 0) {
                    Text("${text.length} / ${field.maxLength}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(text) }) { Text("Done", color = Numio.Amber) }
        },
        dismissButton = {
            Row {
                if (current.isNotEmpty()) {
                    TextButton(onClick = { onDone("") }) { Text("Clear", color = Numio.TextDim) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = Numio.TextDim) }
            }
        },
    )
}

/** Picking one option of a dropdown or list field. */
@Composable
fun FieldChoiceDialog(field: FormField, current: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Numio.Surface,
        title = { Text(field.label, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                field.options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.RadioButton) { onDone(option) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == current,
                            onClick = { onDone(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = Numio.Amber),
                        )
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Numio.TextDim) }
        },
    )
}
