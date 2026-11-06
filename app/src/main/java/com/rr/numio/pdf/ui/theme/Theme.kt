package com.rr.numio.pdf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rr.numio.pdf.R

// Inter, SIL OFL 1.1 (see licenses/). Malayalam, Arabic and emoji in file
// names fall back to the phone's own system fonts.
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Official Numio palette (getnumio.org), dark theme. Never a generic mustard. */
object Numio {
    val Bg = Color(0xFF0F0F0E)
    val Surface = Color(0xFF1B1A18)
    val SurfaceHigh = Color(0xFF2A2925)
    val Line = Color(0xFF34322D)
    val Text = Color(0xFFF1EEE6)
    val TextDim = Color(0xFFA9A59B) // 7.6:1 on Bg
    val Amber = Color(0xFFFFC21A)
    val OnAmber = Color(0xFF1A1400)
    val Paper = Color(0xFFF6F4EE)
    val Error = Color(0xFFF2B8B5)
}

private val colors = darkColorScheme(
    primary = Numio.Amber,
    onPrimary = Numio.OnAmber,
    background = Numio.Bg,
    onBackground = Numio.Text,
    surface = Numio.Surface,
    onSurface = Numio.Text,
    surfaceVariant = Numio.SurfaceHigh,
    onSurfaceVariant = Numio.TextDim,
    surfaceContainer = Numio.Surface,
    surfaceContainerHigh = Numio.SurfaceHigh,
    outline = Numio.Line,
    error = Numio.Error,
)

private val base = TextStyle(fontFamily = Inter, color = Numio.Text)

private val type = Typography(
    headlineMedium = base.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    titleLarge = base.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    titleSmall = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
    bodyLarge = base.copy(fontSize = 15.sp),
    bodyMedium = base.copy(fontSize = 14.sp),
    bodySmall = base.copy(fontSize = 13.sp, color = Numio.TextDim),
    labelLarge = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
    labelMedium = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
    labelSmall = base.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp),
)

@Composable
fun PdfReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
