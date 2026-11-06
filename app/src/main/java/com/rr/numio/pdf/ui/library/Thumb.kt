package com.rr.numio.pdf.ui.library

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rr.numio.pdf.pdf.Thumbnails
import com.rr.numio.pdf.ui.PIcons
import com.rr.numio.pdf.ui.theme.Numio

/** First page of a PDF, or a plain page icon if it can't be drawn. */
@Composable
fun Thumb(uri: String, width: Dp, height: Dp) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(Thumbnails.cached(uri), uri) {
        if (value == null) value = Thumbnails.load(context.applicationContext, uri)
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(width, height)
            .clip(shape)
            .background(if (bitmap != null) Numio.Paper else Numio.Surface)
            .border(1.dp, Numio.SurfaceHigh, shape),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.size(width, height),
            )
        } else {
            Icon(PIcons.Page, contentDescription = null, tint = Numio.Amber, modifier = Modifier.size(width * 0.45f))
        }
    }
}
