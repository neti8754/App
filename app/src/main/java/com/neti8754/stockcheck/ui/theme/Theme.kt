package com.neti8754.stockcheck.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neti8754.stockcheck.AccentColor
import com.neti8754.stockcheck.ThemeMode

private data class ThemePalette(
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color
)

private fun paletteFor(accentColor: String) = when (accentColor) {
    AccentColor.BLUE -> ThemePalette(
        Color(0xFF0B57D0), Color.White, Color(0xFFD9E2FF), Color(0xFF001A41),
        Color(0xFFAFC6FF), Color(0xFF002D6A), Color(0xFF184B8F), Color(0xFFD9E2FF)
    )
    AccentColor.PURPLE -> ThemePalette(
        Color(0xFF7C4DFF), Color.White, Color(0xFFE9DDFF), Color(0xFF26005A),
        Color(0xFFD1BCFF), Color(0xFF3A147A), Color(0xFF542A96), Color(0xFFE9DDFF)
    )
    AccentColor.ORANGE -> ThemePalette(
        Color(0xFFC75B00), Color.White, Color(0xFFFFDCC4), Color(0xFF351000),
        Color(0xFFFFB782), Color(0xFF4D1C00), Color(0xFF733000), Color(0xFFFFDCC4)
    )
    AccentColor.PINK -> ThemePalette(
        Color(0xFFB3265E), Color.White, Color(0xFFFFD9E5), Color(0xFF3D0018),
        Color(0xFFFFB1C9), Color(0xFF5F1735), Color(0xFF84234D), Color(0xFFFFD9E5)
    )
    else -> ThemePalette(
        Color(0xFF10A37F), Color.White, Color(0xFFD6F3EA), Color(0xFF063D30),
        Color(0xFF49C9A8), Color(0xFF00382A), Color(0xFF0A4D3C), Color(0xFFC7F5E8)
    )
}

private val BaseLightColors = lightColorScheme(
    secondary = Color(0xFF5F6368),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF7F7F8),
    surfaceVariant = Color(0xFFF0F0F0),
    onBackground = Color(0xFF202123),
    onSurface = Color(0xFF202123),
    onSurfaceVariant = Color(0xFF6E6E80),
    outline = Color(0xFFE5E5E5),
    error = Color(0xFFB42318),
    errorContainer = Color(0xFFFDECEA)
)

private val BaseDarkColors = darkColorScheme(
    secondary = Color(0xFFB9BDC2),
    background = Color(0xFF171717),
    surface = Color(0xFF212121),
    surfaceVariant = Color(0xFF2A2A2A),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFFA7A7A7),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF5B1A18)
)

@Composable
fun StockCheckTheme(
    themeMode: String,
    accentColor: String,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val palette = paletteFor(accentColor)
    val colors = if (dark) {
        BaseDarkColors.copy(
            primary = palette.darkPrimary,
            onPrimary = palette.darkOnPrimary,
            primaryContainer = palette.darkContainer,
            onPrimaryContainer = palette.darkOnContainer
        )
    } else {
        BaseLightColors.copy(
            primary = palette.lightPrimary,
            onPrimary = palette.lightOnPrimary,
            primaryContainer = palette.lightContainer,
            onPrimaryContainer = palette.lightOnContainer
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = StockTypography,
        shapes = StockShapes,
        content = content
    )
}

val StockTypography = androidx.compose.material3.Typography(
    headlineMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 24.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
    ),
    headlineSmall = androidx.compose.ui.text.TextStyle(
        fontSize = 20.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 18.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 16.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 16.sp
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 14.sp
    ),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 14.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
    ),
    labelSmall = androidx.compose.ui.text.TextStyle(
        fontSize = 12.sp
    )
)

val StockShapes = androidx.compose.material3.Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
)
