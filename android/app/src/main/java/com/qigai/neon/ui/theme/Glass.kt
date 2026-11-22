package com.qigai.neon.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * v0.4 liquid glass, see docs/design/v0.4/DESIGN.md.
 *
 * Every screen sits on [neonBackdrop]: the canvas plus four soft colour fields.
 * Cards are a translucent fill, a 1dp edge that is bright at the top left and the
 * bottom right and clear in between, and a faint sheen from the top. Floating chrome
 * (the tab bar, sheets) adds a real backdrop blur through Haze on Android 12+.
 */
@Immutable
data class GlassTokens(
    val fill: Color,
    val fillStrong: Color,
    val tile: Color,
    val edgeHi: Color,
    val edgeLo: Color,
    val sheen: Color,
    val blobs: List<Blob>,
)

@Immutable
data class Blob(val color: Color, val alpha: Float, val radiusFrac: Float, val cx: Float, val cy: Float)

val DarkGlass = GlassTokens(
    fill = Color.White.copy(alpha = 0.06f),
    fillStrong = Color(0xFF16161A).copy(alpha = 0.72f),
    tile = Color.White.copy(alpha = 0.07f),
    edgeHi = Color.White.copy(alpha = 0.34f),
    edgeLo = Color.White.copy(alpha = 0.12f),
    sheen = Color.White.copy(alpha = 0.07f),
    blobs = listOf(
        Blob(Color(0xFFDFF84B), 0.22f, 0.62f, 0.92f, 0.08f),
        Blob(Color(0xFF6A5CFF), 0.30f, 0.72f, 0.02f, 0.52f),
        Blob(Color(0xFF16C2A0), 0.18f, 0.58f, 0.90f, 0.86f),
        Blob(Color(0xFFFF6A3D), 0.10f, 0.46f, 0.10f, 1.00f),
    ),
)

val LightGlass = GlassTokens(
    fill = Color.White.copy(alpha = 0.52f),
    fillStrong = Color.White.copy(alpha = 0.78f),
    tile = Color.White.copy(alpha = 0.55f),
    edgeHi = Color.White.copy(alpha = 0.95f),
    edgeLo = Color.White.copy(alpha = 0.50f),
    sheen = Color.White.copy(alpha = 0.40f),
    blobs = listOf(
        Blob(Color(0xFFE4FF5C), 0.50f, 0.62f, 0.92f, 0.08f),
        Blob(Color(0xFFB4ACFF), 0.48f, 0.72f, 0.02f, 0.50f),
        Blob(Color(0xFF9FF0DA), 0.40f, 0.58f, 0.90f, 0.84f),
        Blob(Color(0xFFFFCFA8), 0.45f, 0.48f, 0.08f, 1.00f),
    ),
)

object Glass {
    val tokens: GlassTokens @Composable get() = if (Neon.colors.isDark) DarkGlass else LightGlass
}

/** Canvas colour plus the soft colour fields the glass picks up. */
@Composable
fun Modifier.neonBackdrop(): Modifier {
    val c = Neon.colors
    val g = Glass.tokens
    return this.background(c.bgCanvas).drawBehind {
        val base = max(size.width, size.height * 0.55f)
        for (b in g.blobs) {
            val center = Offset(size.width * b.cx, size.height * b.cy)
            val r = base * b.radiusFrac
            drawCircle(
                Brush.radialGradient(
                    0f to b.color.copy(alpha = b.alpha),
                    0.55f to b.color.copy(alpha = b.alpha * 0.45f),
                    1f to Color.Transparent,
                    center = center, radius = r,
                ),
                radius = r, center = center,
            )
        }
    }
}

/** The liquid edge: bright at top left and bottom right, clear in between. */
@Composable
fun glassEdge(): Brush {
    val g = Glass.tokens
    return Brush.linearGradient(
        0f to g.edgeHi, 0.32f to Color.Transparent, 0.68f to Color.Transparent, 1f to g.edgeLo,
        start = Offset.Zero, end = Offset.Infinite,
    )
}

/** Translucent glass surface. [strong] is for sheets and menus where text must stay crisp. */
@Composable
fun Modifier.glass(shape: Shape, strong: Boolean = false, fill: Color? = null): Modifier {
    val g = Glass.tokens
    val sheen = g.sheen
    return this
        .clip(shape)
        .background(fill ?: if (strong) g.fillStrong else g.fill)
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(sheen, Color.Transparent),
                    center = Offset(size.width * 0.15f, -size.height * 0.1f),
                    radius = max(size.width, size.height) * 0.75f,
                ),
            )
        }
        .border(1.dp, glassEdge(), shape)
}
