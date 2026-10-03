package com.neti8754.stockcheck.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neti8754.stockcheck.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF10A37F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F3EA),
    onPrimaryContainer = Color(0xFF063D30),
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

private val DarkColors = darkColorScheme(
    primary = Color(0xFF49C9A8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0A4D3C),
    onPrimaryContainer = Color(0xFFC7F5E8),
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
fun StockCheckTheme(themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = StockTypography,
        shapes = StockShapes,
        content = content
    )
}

val StockTypography = androidx.compose.material3.Typography(
    headlineMedium = androidx.compose.ui.text.TextStyle(
        fontSize = androidx.compose.ui.unit.sp(24),
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
    ),
    headlineSmall = androidx.compose.ui.text.TextStyle(
        fontSize = androidx.compose.ui.unit.sp(20),
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontSize = androidx.compose.ui.unit.sp(18),
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontSize = androidx.compose.ui.unit.sp(16),
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontSize = androidx.compose.ui.unit.sp(16)
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontSize = androidx.compose.ui.unit.sp(14)
    ),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontSize = androidx.compose.ui.unit.sp(14),
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
    ),
    labelSmall = androidx.compose.ui.text.TextStyle(
        fontSize = androidx.compose.ui.unit.sp(12)
    )
)

val StockShapes = androidx.compose.material3.Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
)
