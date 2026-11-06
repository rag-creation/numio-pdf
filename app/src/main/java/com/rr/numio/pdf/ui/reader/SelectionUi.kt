package com.rr.numio.pdf.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rr.numio.pdf.text.NBox
import com.rr.numio.pdf.ui.PIcons
import com.rr.numio.pdf.ui.theme.Numio

/** A finger on a page: page number from 0, and position as fractions of the page. */
data class PageHit(val page: Int, val x: Float, val y: Float)

/** Amber band over the selected lines, with a handle at each end. */
@Composable
fun BoxScope.SelectionOverlay(boxes: List<NBox>, scale: () -> Float) {
    Canvas(Modifier.matchParentSize()) {
        val band = Numio.Amber.copy(alpha = 0.33f)
        boxes.forEach { b ->
            drawRect(
                color = band,
                topLeft = Offset(b.left * size.width, b.top * size.height),
                size = Size((b.right - b.left) * size.width, (b.bottom - b.top) * size.height),
            )
        }
        // Handles stay the same size on screen at any zoom
        val r = 7.dp.toPx() / scale().coerceAtLeast(1f)
        val stem = 2.dp.toPx() / scale().coerceAtLeast(1f)
        val first = boxes.first()
        val last = boxes.last()
        listOf(
            Triple(first.left * size.width, first.top * size.height, first.bottom * size.height),
            Triple(last.right * size.width, last.top * size.height, last.bottom * size.height),
        ).forEach { (x, top, bottom) ->
            drawRect(Numio.Amber, topLeft = Offset(x - stem / 2, top), size = Size(stem, bottom - top))
            drawCircle(Numio.Amber, radius = r, center = Offset(x, bottom + r * 0.9f))
        }
    }
}

/** Shown while text is selected, in place of the page strip. */
@Composable
fun SelectionBar(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSelectPage: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Numio.Surface)
            .border(1.dp, Numio.SurfaceHigh, RoundedCornerShape(26.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarAction(PIcons.Copy, "Copy", onCopy, highlight = true)
        BarAction(PIcons.Share, "Share", onShare)
        BarAction(PIcons.SelectAll, "Whole page", onSelectPage)
        BarAction(PIcons.Close, "Done", onClose)
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit, highlight: Boolean = false) {
    Column(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (highlight) Numio.Amber else Numio.Text, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (highlight) Numio.Amber else Numio.Text)
    }
}

fun copyText(context: Context, text: String) {
    if (text.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("PDF text", text))
    // Android 13 and newer show their own "Copied" message
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}

fun shareText(context: Context, text: String) {
    if (text.isEmpty()) return
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't share the text.", Toast.LENGTH_SHORT).show()
    }
}
