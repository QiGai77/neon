package com.qigai.neon.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/**
 * The Neon orb: a lime sphere with two eyes. It is also the launcher icon.
 * Lit from the top left (#FBFFD0 → #E6FA62 → #BEDB2C → #8FA816); the eyes blink every few seconds.
 */
@Composable
fun NeonOrb(size: Dp, modifier: Modifier = Modifier, blink: Boolean = true) {
    val t = rememberInfiniteTransition(label = "orb")
    val lid by if (blink) t.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 4200
            1f at 0; 1f at 3900; 0.12f at 4000; 1f at 4120
        }, RepeatMode.Restart), label = "blink",
    ) else t.animateFloat(1f, 1f, infiniteRepeatable(keyframes { durationMillis = 1000 }), label = "still")
    Canvas(modifier.size(size).semantics { contentDescription = "Neon" }) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFFBFFD0), 0.30f to Color(0xFFE6FA62), 0.70f to Color(0xFFBEDB2C), 1f to Color(0xFF8FA816),
                center = Offset(c.x - r * 0.32f, c.y - r * 0.40f), radius = r * 1.55f,
            ),
            radius = r, center = c,
        )
        val ew = r * 0.24f
        val eh = r * 0.40f * lid
        val ey = c.y - r * 0.16f + (r * 0.40f - eh) / 2f
        val eye = Color(0xFF16180A)
        for (x in listOf(c.x - r * 0.34f, c.x + r * 0.10f)) {
            drawRoundRect(eye, Offset(x, ey), Size(ew, eh), CornerRadius(ew / 2f, ew / 2f))
        }
    }
}
