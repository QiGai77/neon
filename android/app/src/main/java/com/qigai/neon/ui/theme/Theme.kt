package com.qigai.neon.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NeonPalette(
    val bgCanvas: Color,
    val bgSurface: Color,
    val bgSubtle: Color,
    val borderHairline: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val neonLime: Color,
    val neonLimeSoft: Color,
    val dangerRed: Color,
    val dangerSoft: Color,
    val warnAmber: Color,
    val warnSoft: Color,
    val terminalBg: Color,
    val terminalFg: Color,
    val isDark: Boolean,
)

val LightNeonPalette = NeonPalette(
    bgCanvas = Color(0xFFF5F4F0),
    bgSurface = Color(0xFFFFFFFF),
    bgSubtle = Color(0xFFEFECE6),
    borderHairline = Color(0xFFE6E3DC),
    inkPrimary = Color(0xFF141416),
    inkSecondary = Color(0xFF6B6964),
    inkTertiary = Color(0xFF9C9992),
    neonLime = Color(0xFF84CC16),
    neonLimeSoft = Color(0xFFECFCCB),
    dangerRed = Color(0xFFDC2626),
    dangerSoft = Color(0xFFFEF2F2),
    warnAmber = Color(0xFFF59E0B),
    warnSoft = Color(0xFFFFFBEB),
    terminalBg = Color(0xFF111214),
    terminalFg = Color(0xFFE5E7EB),
    isDark = false,
)

val DarkNeonPalette = NeonPalette(
    bgCanvas = Color(0xFF121213),
    bgSurface = Color(0xFF1C1C1E),
    bgSubtle = Color(0xFF262629),
    borderHairline = Color(0xFF323236),
    inkPrimary = Color(0xFFEAEAE6),
    inkSecondary = Color(0xFF9A9891),
    inkTertiary = Color(0xFF6E6D68),
    neonLime = Color(0xFFA3E635),
    neonLimeSoft = Color(0xFF1E2D12),
    dangerRed = Color(0xFFF87171),
    dangerSoft = Color(0xFF3A1B1B),
    warnAmber = Color(0xFFFBBF24),
    warnSoft = Color(0xFF332712),
    terminalBg = Color(0xFF0B0C0E),
    terminalFg = Color(0xFFE5E7EB),
    isDark = true,
)

val LocalNeonPalette = staticCompositionLocalOf { LightNeonPalette }

object NeonShapes {
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Composer = RoundedCornerShape(24.dp)
    val Card = RoundedCornerShape(20.dp)
    val Control = RoundedCornerShape(14.dp)
    val Pill = RoundedCornerShape(9999.dp)
}

private val NeonTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = (-0.1).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        fontFeatureSettings = "tnum",
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        fontFeatureSettings = "tnum",
    ),
)

private val M3Shapes = Shapes(
    small = NeonShapes.Control,
    medium = NeonShapes.Card,
    large = NeonShapes.Composer,
    extraLarge = NeonShapes.BottomSheet,
)

@Composable
fun NeonTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkNeonPalette else LightNeonPalette
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.inkPrimary,
            onPrimary = palette.bgCanvas,
            secondary = palette.neonLime,
            onSecondary = Color(0xFF141416),
            background = palette.bgCanvas,
            onBackground = palette.inkPrimary,
            surface = palette.bgSurface,
            onSurface = palette.inkPrimary,
            surfaceVariant = palette.bgSubtle,
            onSurfaceVariant = palette.inkSecondary,
            outline = palette.borderHairline,
            error = palette.dangerRed,
        )
    } else {
        lightColorScheme(
            primary = palette.inkPrimary,
            onPrimary = palette.bgSurface,
            secondary = palette.neonLime,
            onSecondary = palette.inkPrimary,
            background = palette.bgCanvas,
            onBackground = palette.inkPrimary,
            surface = palette.bgSurface,
            onSurface = palette.inkPrimary,
            surfaceVariant = palette.bgSubtle,
            onSurfaceVariant = palette.inkSecondary,
            outline = palette.borderHairline,
            error = palette.dangerRed,
        )
    }

    CompositionLocalProvider(LocalNeonPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NeonTypography,
            shapes = M3Shapes,
            content = content,
        )
    }
}
