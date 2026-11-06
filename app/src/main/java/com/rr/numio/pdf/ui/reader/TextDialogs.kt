package com.rr.numio.pdf.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rr.numio.pdf.edit.MarkColors
import com.rr.numio.pdf.ui.theme.Numio

/**
 * Writing or changing a text box ([sizes] given) or a sticky note (no sizes).
 * [onDelete] is set when changing one that already exists.
 */
@Composable
fun WriteDialog(
    title: String,
    initialText: String,
    initialColor: Int,
    initialSize: Int?,
    isNew: Boolean,
    onDone: (text: String, color: Int, size: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var color by remember { mutableIntStateOf(initialColor) }
    var size by remember { mutableIntStateOf(initialSize ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Numio.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = if (initialSize == null) 4 else 2,
                    maxLines = 8,
                    placeholder = { Text(if (initialSize == null) "Write a note" else "Type here") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Numio.Amber,
                        cursorColor = Numio.Amber,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MarkColors.all.forEach { c ->
                        val selected = c == color
                        Box(
                            Modifier
                                .size(28.dp)
                                .then(if (selected) Modifier.border(2.dp, Numio.Text, CircleShape) else Modifier)
                                .padding(if (selected) 4.dp else 2.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF000000.toInt() or c))
                                .border(1.dp, Numio.Line, CircleShape)
                                .clickable(onClickLabel = MarkColors.name(c), role = Role.RadioButton) { color = c },
                        )
                    }
                }
                if (initialSize != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Small", "Medium", "Large").forEachIndexed { i, label ->
                            val selected = i == size
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .then(
                                        if (selected) Modifier.background(Numio.Text)
                                        else Modifier.border(1.dp, Numio.Line, RoundedCornerShape(16.dp)),
                                    )
                                    .clickable(role = Role.RadioButton) { size = i }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) Numio.Bg else Numio.Text,
                                )
                            }
                        }
                    }
                }
                if (onDelete != null) {
                    Text(
                        "Delete",
                        style = MaterialTheme.typography.labelLarge,
                        color = Numio.Error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClick = onDelete)
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(text.trimEnd(), color, size) }, enabled = text.isNotBlank()) {
                Text(if (isNew) "Add" else "Save", color = if (text.isNotBlank()) Numio.Amber else Numio.TextDim)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Numio.TextDim) }
        },
    )
}
