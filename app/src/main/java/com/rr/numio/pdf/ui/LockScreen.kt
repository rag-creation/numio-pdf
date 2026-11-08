package com.rr.numio.pdf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rr.numio.pdf.ui.theme.Numio

/**
 * Covers everything while PDF Reader is locked. Asks for the phone's lock as
 * soon as it shows; the button asks again if that was cancelled.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit, onLeave: () -> Unit) {
    BackHandler(onBack = onLeave)
    LaunchedEffect(Unit) { onUnlock() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Numio.Bg)
            // Nothing underneath can be touched
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Numio.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PIcons.Lock, contentDescription = null, tint = Numio.AmberText, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("PDF Reader is locked", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "Use your fingerprint or screen lock to open it.",
                style = MaterialTheme.typography.bodyMedium,
                color = Numio.TextDim,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Numio.Amber)
                    .clickable(onClick = onUnlock)
                    .padding(horizontal = 32.dp, vertical = 13.dp),
            ) {
                Text("Unlock", style = MaterialTheme.typography.labelLarge, color = Numio.OnAmber)
            }
        }
    }
}
