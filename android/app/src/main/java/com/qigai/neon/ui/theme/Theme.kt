package com.qigai.neon.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.R

/**
 * Neon v0.3 design tokens, sampled from the v2 concept boards (390×844 dp).
 *
 * Light: warm paper + ink. The "accent" in light IS ink — no colour shouts.
 * Dark : near-black + one lime (#DFF84B). Lime only marks what is live/active.
 */
@Immutable
data class NeonColors(
    /** screen background */
    val bgCanvas: Color,
    /** drawer / side sheet background */
    val bgDrawer: Color,
    /** cards, composer, sheets */
    val bgSurface: Color,
    /** pills, icon squircles, inactive chips, rails */
    val bgSubtle: Color,
    /** tiles that sit inside a surface card */
    val bgTile: Color,
    val userBubble: Color,
    /** chip / button outlines, rules */
    val borderHairline: Color,
    /** the 1dp edge of a card */
    val borderCard: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    /** active fill: ink in light, lime in dark */
    val accent: Color,
    /** text/icon on [accent] */
    val onAccent: Color,
    /** active chip fill (ink in light, 12% lime in dark) */
    val chipActive: Color,
    val onChipActive: Color,
    /** active chip edge (none in light, 40% lime in dark) */
    val chipActiveBorder: Color,
    /** the little "connected" dot */
    val live: Color,
    val danger: Color,
    val dangerSoft: Color,
    val warn: Color,
    val terminalBg: Color,
    val terminalInk: Color,
    val terminalDim: Color,
    val lime: Color,
    val scrim: Color,
    val shadow: Color,
    val isDark: Boolean,
) {
    // compatibility aliases used by older call sites (markdown)
    val onInk: Color get() = onAccent
    val neonLime: Color get() = live
}

val LightNeonColors = NeonColors(
    bgCanvas = Color(0xFFF4F3EE),
    bgDrawer = Color(0xFFEFEEE8),
    bgSurface = Color(0xFFFFFFFF),
    bgSubtle = Color(0xFFECEAE3),
    bgTile = Color(0xFFF4F3EE),
    userBubble = Color(0xFFE9E7E0),
    borderHairline = Color(0xFFD6D3CA),
    borderCard = Color(0xFFE4E2DB),
    inkPrimary = Color(0xFF101113),
    inkSecondary = Color(0xFF6B6F76),
    inkTertiary = Color(0xFFA3A5AA),
    accent = Color(0xFF101113),
    onAccent = Color(0xFFFFFFFF),
    chipActive = Color(0xFF101113),
    onChipActive = Color(0xFFFFFFFF),
    chipActiveBorder = Color(0x00000000),
    live = Color(0xFF2E9E5B),
    danger = Color(0xFFE5484D),
    dangerSoft = Color(0xFFFCEDEC),
    warn = Color(0xFFF08A24),
    terminalBg = Color(0xFF141415),
    terminalInk = Color(0xFFE9E8E3),
    terminalDim = Color(0xFF7D7C79),
    lime = Color(0xFFDFF84B),
    scrim = Color(0x66101113),
    shadow = Color(0xFF3B3523),
    isDark = false,
)

val DarkNeonColors = NeonColors(
    bgCanvas = Color(0xFF0B0B0C),
    bgDrawer = Color(0xFF0D0D16),
    bgSurface = Color(0xFF161616),
    bgSubtle = Color(0xFF222224),
    bgTile = Color(0xFF222224),
    userBubble = Color(0xFF1F1F21),
    borderHairline = Color(0xFF2E2E31),
    borderCard = Color(0xFF232325),
    inkPrimary = Color(0xFFF2F1EC),
    inkSecondary = Color(0xFF8A8A8E),
    inkTertiary = Color(0xFF5E5E63),
    accent = Color(0xFFDFF84B),
    onAccent = Color(0xFF0B0B0C),
    chipActive = Color(0xFF2B2D1C),
    onChipActive = Color(0xFFDFF84B),
    chipActiveBorder = Color(0x80B7CC3E),
    live = Color(0xFFDFF84B),
    danger = Color(0xFFFF6B6B),
    dangerSoft = Color(0xFF2A1616),
    warn = Color(0xFFF59A3A),
    terminalBg = Color(0xFF111112),
    terminalInk = Color(0xFFE9E8E3),
    terminalDim = Color(0xFF6E6D6A),
    lime = Color(0xFFDFF84B),
    scrim = Color(0xCC000000),
    shadow = Color(0xFF000000),
    isDark = true,
)

object NeonShapes {
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val composer = RoundedCornerShape(28.dp)
    val card = RoundedCornerShape(20.dp)
    val tile = RoundedCornerShape(16.dp)
    val item = RoundedCornerShape(12.dp)
    val squircle = RoundedCornerShape(14.dp)
    val small = RoundedCornerShape(10.dp)
    val capsule = CircleShape
    val userBubble = RoundedCornerShape(22.dp)
    val terminal = RoundedCornerShape(18.dp)
}

/** Motion riffs (concept sheet "Neon · Motion Riffs"): springs as (dampingRatio, stiffness). */
object NeonMotion {
    fun <T> thinking(): FiniteAnimationSpec<T> = spring(0.82f, 340f)
    fun <T> composer(): FiniteAnimationSpec<T> = spring(0.78f, 420f)
    fun <T> palette(): FiniteAnimationSpec<T> = spring(0.82f, 400f)
    fun <T> sheet(): FiniteAnimationSpec<T> = spring(0.85f, 380f)
    fun <T> terminal(): FiniteAnimationSpec<T> = spring(0.85f, 360f)
    fun <T> receipt(): FiniteAnimationSpec<T> = spring(0.76f, 380f)
    const val STAGGER_MS = 30
    const val RING_MS = 520
    const val RING_STAGGER_MS = 60
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

/**
 * Type scale (sp), measured off the 2× concept boards.
 * Chinese reads best a touch lighter and roomier than Latin, so body sits at 16/26.
 */
object NeonType {
    private fun s(f: FontFamily, size: TextUnit, line: TextUnit, color: Color) =
        TextStyle(fontFamily = f, fontSize = size, lineHeight = line, color = color)

    /** drawer brand "Neon" */
    @Composable fun brand() = s(Neon.fonts.medium, 22.sp, 28.sp, Neon.colors.inkPrimary).copy(letterSpacing = (-0.2).sp)
    /** sheet / page headline */
    @Composable fun headline() = s(Neon.fonts.medium, 20.sp, 28.sp, Neon.colors.inkPrimary)
    /** top bar title */
    @Composable fun title() = s(Neon.fonts.regular, 17.sp, 24.sp, Neon.colors.inkPrimary)
    /** card title */
    @Composable fun cardTitle() = s(Neon.fonts.regular, 15.5.sp, 22.sp, Neon.colors.inkPrimary)
    @Composable fun body() = s(Neon.fonts.regular, 16.sp, 26.sp, Neon.colors.inkPrimary)
    @Composable fun bodyMedium() = s(Neon.fonts.medium, 16.sp, 24.sp, Neon.colors.inkPrimary)
    /** list item primary */
    @Composable fun item() = s(Neon.fonts.regular, 15.sp, 21.sp, Neon.colors.inkPrimary)
    @Composable fun label() = s(Neon.fonts.regular, 14.sp, 20.sp, Neon.colors.inkPrimary)
    /** thinking steps, secondary prose */
    @Composable fun secondary() = s(Neon.fonts.regular, 14.sp, 22.sp, Neon.colors.inkSecondary)
    @Composable fun chip() = s(Neon.fonts.regular, 13.sp, 18.sp, Neon.colors.inkPrimary)
    @Composable fun caption() = s(Neon.fonts.regular, 12.sp, 16.sp, Neon.colors.inkSecondary)
    /** quiet section labels: "命中技能", "规划步骤" */
    @Composable fun overline() = s(Neon.fonts.regular, 12.sp, 16.sp, Neon.colors.inkTertiary)
    @Composable fun code() = s(Neon.fonts.code, 13.sp, 20.sp, Neon.colors.inkPrimary)
    @Composable fun mono() = s(Neon.fonts.code, 12.sp, 16.sp, Neon.colors.inkSecondary)
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
    NeonTheme(dark, content)
}

@Composable
fun NeonTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkNeonColors else LightNeonColors
    val ctx = LocalContext.current
    val fonts = remember { buildFonts(ctx.applicationContext) }
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.bgCanvas, onBackground = c.inkPrimary,
            surface = c.bgSurface, onSurface = c.inkPrimary, surfaceVariant = c.bgSubtle, onSurfaceVariant = c.inkSecondary,
            outline = c.borderHairline, outlineVariant = c.borderCard, error = c.danger, secondary = c.inkSecondary,
            surfaceContainer = c.bgSurface, surfaceContainerLow = c.bgSurface, surfaceContainerHigh = c.bgSurface,
            surfaceContainerHighest = c.bgSubtle, surfaceContainerLowest = c.bgCanvas,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, background = c.bgCanvas, onBackground = c.inkPrimary,
            surface = c.bgSurface, onSurface = c.inkPrimary, surfaceVariant = c.bgSubtle, onSurfaceVariant = c.inkSecondary,
            outline = c.borderHairline, outlineVariant = c.borderCard, error = c.danger, secondary = c.inkSecondary,
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
