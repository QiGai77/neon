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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/**
 * The Neon orb: a lime sphere with two eyes. It is also the launcher icon.
 * Lit from the top left (#FBFFD0 → #E6FA62 → #BEDB2C → #8FA816); the eyes blink every few seconds.
 */
@Composable
fun NeonOrb(size: Dp, modifier: Modifier = Modifier, blink: Boolean = true, lively: Boolean = false) {
    val t = rememberInfiniteTransition(label = "orb")
    val amp by androidx.compose.animation.core.animateFloatAsState(
        if (lively) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 120f), label = "amp",
    )
    val lid by if (blink) t.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 4200
            1f at 0; 1f at 3900; 0.12f at 4000; 1f at 4120
            // a second, quicker blink only shows while thinking (mixed in below)
        }, RepeatMode.Restart), label = "blink",
    ) else t.animateFloat(1f, 1f, infiniteRepeatable(keyframes { durationMillis = 1000 }), label = "still")
    val lid2 by t.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 1700; 1f at 0; 1f at 1450; 0.1f at 1530; 1f at 1640 }, RepeatMode.Restart), label = "blink2",
    )
    // bounce, squash and a little sway: one 900ms beat
    val beat by t.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)), label = "beat")
    // eyes glance left, centre, right, centre
    val look by t.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes {
            durationMillis = 2600
            0f at 0; -1f at 300; -1f at 900; 0f at 1150; 1f at 1450; 1f at 2100; 0f at 2400
        }, RepeatMode.Restart), label = "look",
    )
    val bob = kotlin.math.sin(beat)
    val squash = kotlin.math.sin(beat * 2f)
    Canvas(
        modifier.size(size).semantics { contentDescription = if (lively) "Neon 正在思考" else "Neon" }
            .graphicsLayer {
                translationY = -kotlin.math.abs(bob) * this.size.height * 0.14f * amp
                scaleX = 1f + 0.07f * squash * amp
                scaleY = 1f - 0.07f * squash * amp
                rotationZ = 7f * kotlin.math.sin(beat * 0.5f) * amp
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }
    ) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFFBFFD0), 0.30f to Color(0xFFE6FA62), 0.70f to Color(0xFFBEDB2C), 1f to Color(0xFF8FA816),
                center = Offset(c.x - r * 0.32f, c.y - r * 0.40f), radius = r * 1.55f,
            ),
            radius = r, center = c,
        )
        val l = lid * (1f - amp + amp * lid2)
        val ew = r * 0.24f
        val eh = r * 0.40f * l
        val dx = r * 0.14f * look * amp
        val dy = -r * 0.04f * amp
        val ey = c.y - r * 0.16f + (r * 0.40f - eh) / 2f + dy
        val eye = Color(0xFF16180A)
        for (x in listOf(c.x - r * 0.34f, c.x + r * 0.10f)) {
            drawRoundRect(eye, Offset(x + dx, ey), Size(ew, eh), CornerRadius(ew / 2f, ew / 2f))
        }
    }
}
