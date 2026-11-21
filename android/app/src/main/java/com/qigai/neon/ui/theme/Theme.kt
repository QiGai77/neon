package com.qigai.neon.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.R

/** Design tokens — v0.3.0 native spec (warm paper light default, pure dark). */
@Immutable
data class NeonColors(
    val bgCanvas: Color,
    val bgSurface: Color,
    val bgSubtle: Color,
    val borderHairline: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val neonLime: Color,
    val danger: Color,
    val dangerSoft: Color,
    val warnAmber: Color,
    val warnSoft: Color,
    val terminalBg: Color,
    /** text/icon drawn on top of inkPrimary fills */
    val onInk: Color,
    val isDark: Boolean,
)

val LightNeonColors = NeonColors(
    bgCanvas = Color(0xFFF5F4F0),
    bgSurface = Color(0xFFFFFFFF),
    bgSubtle = Color(0xFFEFECE6),
    borderHairline = Color(0xFFE6E3DC),
    inkPrimary = Color(0xFF141416),
    inkSecondary = Color(0xFF6B6964),
    neonLime = Color(0xFF84CC16),
    danger = Color(0xFFDC2626),
    dangerSoft = Color(0xFFFEF2F2),
    warnAmber = Color(0xFFF59E0B),
    warnSoft = Color(0xFFFFFBEB),
    terminalBg = Color(0xFF111214),
    onInk = Color(0xFFF5F4F0),
    isDark = false,
)

val DarkNeonColors = NeonColors(
    bgCanvas = Color(0xFF121213),
    bgSurface = Color(0xFF1C1C1E),
    bgSubtle = Color(0xFF262629),
    borderHairline = Color(0xFF323236),
    inkPrimary = Color(0xFFEAEAE6),
    inkSecondary = Color(0xFF9A9891),
    neonLime = Color(0xFFA3E635),
    danger = Color(0xFFF87171),
    dangerSoft = Color(0xFF3A1B1B),
    warnAmber = Color(0xFFFBBF24),
    warnSoft = Color(0xFF332712),
    terminalBg = Color(0xFF0B0C0E),
    onInk = Color(0xFF121213),
    isDark = true,
)

/** Lime used on terminal (dark) surfaces in both themes. */
val TerminalLime = Color(0xFFA3E635)

object NeonShapes {
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val composer = RoundedCornerShape(24.dp)
    val card = RoundedCornerShape(20.dp)
    val item = RoundedCornerShape(14.dp)
    val capsule = CircleShape
    val userBubble = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
}

@Immutable
data class NeonFonts(
    val light: FontFamily,
    val regular: FontFamily,
    val medium: FontFamily,
    val code: FontFamily,
)

/**
 * Google Sans for Latin with Noto Sans SC as the CJK fallback.
 * API 29+: a real per-glyph fallback chain via Typeface.CustomFallbackBuilder.
 * API 26-28: Noto Sans SC alone (it also covers Latin).
 */
private fun buildFonts(context: Context): NeonFonts {
    val code = FontFamily(Font(R.font.googlesanscode_regular, FontWeight.Normal))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return try {
            fun chain(latin: Int, cjk: Int, weight: Int): FontFamily {
                val res = context.resources
                val latinFont = android.graphics.fonts.Font.Builder(res, latin).build()
                val cjkFont = android.graphics.fonts.Font.Builder(res, cjk).build()
                val tf = android.graphics.Typeface.CustomFallbackBuilder(
                    android.graphics.fonts.FontFamily.Builder(latinFont).build()
                ).addCustomFallback(android.graphics.fonts.FontFamily.Builder(cjkFont).build())
                    .setStyle(android.graphics.fonts.FontStyle(weight, android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT))
                    .build()
                return FontFamily(androidx.compose.ui.text.font.Typeface(tf))
            }
            NeonFonts(
                light = chain(R.font.googlesans_regular, R.font.notosanssc_light, 300),
                regular = chain(R.font.googlesans_regular, R.font.notosanssc_regular, 400),
                medium = chain(R.font.googlesans_medium, R.font.notosanssc_medium, 500),
                code = code,
            )
        } catch (_: Throwable) {
            notoOnly(code)
        }
    }
    return notoOnly(code)
}

private fun notoOnly(code: FontFamily) = NeonFonts(
    light = FontFamily(Font(R.font.notosanssc_light, FontWeight.Light)),
    regular = FontFamily(Font(R.font.notosanssc_regular, FontWeight.Normal)),
    medium = FontFamily(Font(R.font.notosanssc_medium, FontWeight.Medium)),
    code = code,
)

val LocalNeonColors = staticCompositionLocalOf { LightNeonColors }
val LocalNeonFonts = staticCompositionLocalOf<NeonFonts> { error("NeonFonts not provided") }

object Neon {
    val colors: NeonColors @Composable get() = LocalNeonColors.current
    val fonts: NeonFonts @Composable get() = LocalNeonFonts.current
}

/** Text styles. Numbers that count (time / quota / counters) use [tnum]. */
object NeonType {
    @Composable fun title() = TextStyle(fontFamily = Neon.fonts.medium, fontSize = 18.sp, color = Neon.colors.inkPrimary)
    @Composable fun headline() = TextStyle(fontFamily = Neon.fonts.medium, fontSize = 22.sp, color = Neon.colors.inkPrimary)
    @Composable fun body() = TextStyle(fontFamily = Neon.fonts.regular, fontSize = 16.sp, lineHeight = 26.sp, color = Neon.colors.inkPrimary)
    @Composable fun bodyMedium() = TextStyle(fontFamily = Neon.fonts.medium, fontSize = 16.sp, lineHeight = 24.sp, color = Neon.colors.inkPrimary)
    @Composable fun label() = TextStyle(fontFamily = Neon.fonts.regular, fontSize = 14.sp, lineHeight = 20.sp, color = Neon.colors.inkPrimary)
    @Composable fun secondary() = TextStyle(fontFamily = Neon.fonts.light, fontSize = 14.sp, lineHeight = 20.sp, color = Neon.colors.inkSecondary)
    @Composable fun caption() = TextStyle(fontFamily = Neon.fonts.regular, fontSize = 12.sp, lineHeight = 16.sp, color = Neon.colors.inkSecondary)
    @Composable fun code() = TextStyle(fontFamily = Neon.fonts.code, fontSize = 13.sp, lineHeight = 20.sp, color = Neon.colors.inkPrimary)
    @Composable fun tnum(base: TextStyle = caption()) = base.copy(fontFeatureSettings = "tnum")
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Composable
fun NeonTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val c = if (dark) DarkNeonColors else LightNeonColors
    val ctx = LocalContext.current
    val fonts = remember { buildFonts(ctx.applicationContext) }
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.inkPrimary, onPrimary = c.onInk, background = c.bgCanvas, onBackground = c.inkPrimary,
            surface = c.bgSurface, onSurface = c.inkPrimary, surfaceVariant = c.bgSubtle, onSurfaceVariant = c.inkSecondary,
            outline = c.borderHairline, outlineVariant = c.borderHairline, error = c.danger, secondary = c.inkSecondary,
            surfaceContainer = c.bgSurface, surfaceContainerLow = c.bgSurface, surfaceContainerHigh = c.bgSurface,
            surfaceContainerHighest = c.bgSubtle, surfaceContainerLowest = c.bgCanvas,
        )
    } else {
        lightColorScheme(
            primary = c.inkPrimary, onPrimary = c.onInk, background = c.bgCanvas, onBackground = c.inkPrimary,
            surface = c.bgSurface, onSurface = c.inkPrimary, surfaceVariant = c.bgSubtle, onSurfaceVariant = c.inkSecondary,
            outline = c.borderHairline, outlineVariant = c.borderHairline, error = c.danger, secondary = c.inkSecondary,
            surfaceContainer = c.bgSurface, surfaceContainerLow = c.bgSurface, surfaceContainerHigh = c.bgSurface,
            surfaceContainerHighest = c.bgSubtle, surfaceContainerLowest = c.bgCanvas,
        )
    }
    CompositionLocalProvider(LocalNeonColors provides c, LocalNeonFonts provides fonts) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography.let { t ->
                t.copy(
                    bodyLarge = t.bodyLarge.copy(fontFamily = fonts.regular),
                    bodyMedium = t.bodyMedium.copy(fontFamily = fonts.regular),
                    bodySmall = t.bodySmall.copy(fontFamily = fonts.regular),
                    titleLarge = t.titleLarge.copy(fontFamily = fonts.medium),
                    titleMedium = t.titleMedium.copy(fontFamily = fonts.medium),
                    titleSmall = t.titleSmall.copy(fontFamily = fonts.medium),
                    labelLarge = t.labelLarge.copy(fontFamily = fonts.medium),
                    labelMedium = t.labelMedium.copy(fontFamily = fonts.regular),
                    labelSmall = t.labelSmall.copy(fontFamily = fonts.regular),
                )
            },
            content = content,
        )
    }
}
