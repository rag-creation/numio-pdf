package com.rr.numio.pdf.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rr.numio.pdf.ui.theme.Numio

/**
 * Asks for a locked PDF's password. Nothing is remembered: it's used to open the
 * PDF, kept in memory while it's open, and saving locks it again with the same one.
 */
@Composable
fun PasswordDialog(name: String, wrong: Boolean, onOpen: (String) -> Unit, onCancel: () -> Unit) {
    // A wrong try clears the box, ready for the next one
    var password by remember(wrong) { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(wrong) { runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Numio.Surface,
        icon = { Icon(PIcons.Lock, contentDescription = null, tint = Numio.AmberText) },
        title = { Text("This PDF is locked", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = Numio.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("Password") },
                    isError = wrong,
                    supportingText = if (wrong) {
                        { Text("That password isn't right. Try again.", color = Numio.Error) }
                    } else {
                        null
                    },
                    visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { if (password.isNotEmpty()) onOpen(password) }),
                    trailingIcon = {
                        IconButton(onClick = { shown = !shown }) {
                            Icon(
                                if (shown) PIcons.EyeOff else PIcons.Eye,
                                contentDescription = if (shown) "Hide password" else "Show password",
                                tint = Numio.TextDim,
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Numio.AmberText,
                        focusedLabelColor = Numio.AmberText,
                        cursorColor = Numio.AmberText,
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "The password isn't saved anywhere. When you save changes, the PDF stays locked with it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onOpen(password) }, enabled = password.isNotEmpty()) {
                Text("Open", color = if (password.isNotEmpty()) Numio.AmberText else Numio.Line)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel", color = Numio.TextDim) } },
    )
}
