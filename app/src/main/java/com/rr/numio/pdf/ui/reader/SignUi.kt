package com.rr.numio.pdf.ui.reader

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rr.numio.pdf.data.SignatureStore
import com.rr.numio.pdf.edit.Signature
import com.rr.numio.pdf.edit.TextBox
import com.rr.numio.pdf.ui.PIcons
import com.rr.numio.pdf.ui.theme.Numio

private val Paper = Color(0xFFF6F4EE)
private val inks = listOf(0x111111, 0x1D4ED8)
private val styles = listOf("cursive" to "Handwriting", "serif" to "Classic", "sans" to "Plain")

/**
 * Making or picking a signature: draw it, type it, or use a saved one.
 * Saved signatures stay in the app's private storage and open only after
 * the phone's fingerprint / screen lock.
 */
@Composable
fun SignDialog(
    store: SignatureStore,
    unlocked: Boolean,
    onUnlocked: () -> Unit,
    onUse: (Signature) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var keep by remember { mutableStateOf(true) }
    var ink by remember { mutableIntStateOf(inks[0]) }
    val strokes = remember { mutableStateListOf<FloatArray>() }
    var live by remember { mutableStateOf<List<Float>>(emptyList()) }
    var padSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var name by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(styles[0].first) }
    var saved by remember { mutableStateOf(store.load()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .navigationBarsPadding()
                .clip(RoundedCornerShape(28.dp))
                .background(Numio.Surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your signature", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(PIcons.Close, contentDescription = "Close", tint = Numio.TextDim) }
            }

            // Draw · Type · Saved
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Numio.Bg).padding(4.dp),
            ) {
                listOf("Draw", "Type", "Saved").forEachIndexed { i, label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(if (i == tab) Numio.SurfaceHigh else Color.Transparent)
                            .clickable(role = Role.Tab) { tab = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (i == tab) Numio.Text else Numio.TextDim)
                    }
                }
            }

            when (tab) {
                0 -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2.6f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Paper),
                    ) {
                        Canvas(
                            Modifier
                                .matchParentSize()
                                .pointerInput(Unit) {
                                    padSize = size
                                    detectDragGestures(
                                        onDragStart = { o -> live = listOf(o.x / size.width, o.y / size.height) },
                                        onDragEnd = {
                                            if (live.size >= 2) strokes.add(live.toFloatArray())
                                            live = emptyList()
                                        },
                                        onDragCancel = { live = emptyList() },
                                    ) { change, _ ->
                                        live = live + listOf(change.position.x / size.width, change.position.y / size.height)
                                    }
                                }
                                .pointerInput(Unit) {
                                    // A tap makes a dot, e.g. over an "i"
                                    detectTapGestures { o -> strokes.add(floatArrayOf(o.x / size.width, o.y / size.height)) }
                                },
                        ) {
                            val pen = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            val c = Color(0xFF000000.toInt() or ink)
                            (strokes + listOf(live.toFloatArray())).forEach { s ->
                                if (s.size == 2) drawCircle(c, pen.width / 2f, Offset(s[0] * size.width, s[1] * size.height))
                                if (s.size < 4) return@forEach
                                val path = Path().apply {
                                    moveTo(s[0] * size.width, s[1] * size.height)
                                    var i = 2
                                    while (i + 1 < s.size) {
                                        lineTo(s[i] * size.width, s[i + 1] * size.height)
                                        i += 2
                                    }
                                }
                                drawPath(path, c, style = pen)
                            }
                            // The line to sign on
                            drawLine(
                                Color(0x55000000), Offset(size.width * 0.06f, size.height * 0.78f),
                                Offset(size.width * 0.94f, size.height * 0.78f), strokeWidth = 1.dp.toPx(),
                            )
                        }
                        if (strokes.isEmpty() && live.isEmpty()) {
                            Text("Sign here", style = MaterialTheme.typography.bodyMedium, color = Color(0x88000000), modifier = Modifier.align(Alignment.Center))
                        } else {
                            Text(
                                "Clear",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF191815),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFE4E0D6))
                                    .clickable { strokes.clear() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Ink", style = MaterialTheme.typography.bodySmall)
                        inks.forEach { c ->
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .then(if (c == ink) Modifier.border(2.dp, Numio.Text, CircleShape) else Modifier)
                                    .padding(if (c == ink) 4.dp else 2.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF000000.toInt() or c))
                                    .border(1.dp, Numio.Line, CircleShape)
                                    .clickable(onClickLabel = if (c == inks[0]) "Black ink" else "Blue ink", role = Role.RadioButton) { ink = c },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        KeepBox(keep) { keep = it }
                    }
                }
                1 -> {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.replace('\n', ' ') },
                        singleLine = true,
                        placeholder = { Text("Type your name") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Numio.AmberText, cursorColor = Numio.AmberText),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    styles.forEach { (key, label) ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Paper)
                                .then(if (key == style) Modifier.border(2.dp, Numio.AmberText, RoundedCornerShape(16.dp)) else Modifier)
                                .clickable(onClickLabel = label, role = Role.RadioButton) { style = key },
                        ) {
                            Canvas(Modifier.matchParentSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
                                TextBox.drawSignature(
                                    drawContext.canvas.nativeCanvas, name.ifBlank { "Your name" }, key,
                                    0xFF000000.toInt() or ink, 0f, 0f, size.width, size.height,
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Malayalam names work too.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        KeepBox(keep) { keep = it }
                    }
                }
                else -> {
                    LaunchedEffect(Unit) {
                        if (!unlocked) unlock(context, onUnlocked)
                    }
                    when {
                        !unlocked -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Icon(PIcons.Lock, contentDescription = null, tint = Numio.AmberText, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Saved signatures are locked", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { unlock(context, onUnlocked) }) { Text("Unlock", color = Numio.AmberText) }
                        }
                        saved.isEmpty() -> Text(
                            "No saved signatures yet. Draw or type one with “Save for next time” ticked.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Numio.TextDim,
                        )
                        else -> saved.forEachIndexed { i, sig ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(70.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Paper)
                                        .clickable(onClickLabel = "Use this signature") { onUse(sig) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    SignaturePreview(sig)
                                }
                                IconButton(onClick = {
                                    store.remove(i)
                                    saved = store.load()
                                }) { Icon(PIcons.Bin, contentDescription = "Delete saved signature", tint = Numio.Error) }
                            }
                        }
                    }
                    Text("Kept only on this phone, behind your screen lock.", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (tab != 2) {
                val sig = when (tab) {
                    0 -> Signature.fromPad(strokes.toList(), padSize.width.toFloat().coerceAtLeast(1f), padSize.height.toFloat().coerceAtLeast(1f), ink, 3f * context.resources.displayMetrics.density)
                    else -> name.trim().takeIf { it.isNotEmpty() }?.let { n ->
                        // Typed signatures: about four times wider than tall, more for long names
                        Signature(text = n, font = style, color = ink, aspect = (n.length * 0.55f).coerceIn(2.5f, 9f))
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(if (sig != null) Numio.Amber else Numio.SurfaceHigh)
                        .clickable(enabled = sig != null, role = Role.Button) {
                            if (sig != null) {
                                if (keep) store.add(sig)
                                onUse(sig)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Place on page",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (sig != null) Numio.OnAmber else Numio.TextDim,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepBox(keep: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Checkbox) { onChange(!keep) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = keep,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = Numio.Amber, checkmarkColor = Numio.OnAmber),
        )
        Text("Save for next time", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun SignaturePreview(sig: Signature) {
    Canvas(Modifier.fillMaxWidth().height(54.dp)) {
        val boxH = size.height
        val boxW = minOf(size.width, boxH * sig.aspect)
        val c = Color(0xFF000000.toInt() or sig.color)
        if (sig.drawn) {
            val pen = Stroke((sig.penWidth * boxW).coerceAtLeast(1.5f), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val h = boxW / sig.aspect
            val top = (size.height - h) / 2f
            sig.strokes.forEach { s ->
                if (s.size < 4) return@forEach
                val path = Path().apply {
                    moveTo(s[0] * boxW, top + s[1] * h)
                    var i = 2
                    while (i + 1 < s.size) {
                        lineTo(s[i] * boxW, top + s[i + 1] * h)
                        i += 2
                    }
                }
                drawPath(path, c, style = pen)
            }
        } else {
            TextBox.drawSignature(drawContext.canvas.nativeCanvas, sig.text, sig.font, 0xFF000000.toInt() or sig.color, 0f, 0f, boxW, boxH)
        }
    }
}

/**
 * Asks for the phone's fingerprint, face or screen lock before showing saved
 * signatures. Phones with no screen lock (or Android older than 9) open
 * straight away, since there's nothing to check against.
 */
private fun unlock(context: Context, onOk: () -> Unit) {
    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || keyguard?.isDeviceSecure != true) {
        onOk()
        return
    }
    val builder = BiometricPrompt.Builder(context)
        .setTitle("Saved signatures")
        .setSubtitle("Unlock to use your saved signatures")
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        else -> builder.setNegativeButton("Cancel", context.mainExecutor) { _, _ -> }
    }
    try {
        builder.build().authenticate(
            CancellationSignal(),
            context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onOk()
            },
        )
    } catch (e: Exception) {
        // No fingerprint set up on an Android 9 phone with only a PIN: nothing to prompt with
        onOk()
    }
}
