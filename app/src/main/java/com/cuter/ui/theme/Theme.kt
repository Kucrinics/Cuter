package com.cuter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF0B0B10)
val Panel = Color(0xFF14141C)
val PanelHigh = Color(0xFF1D1D28)
val Stroke = Color(0xFF2A2A38)
val Accent = Color(0xFF2AE0B0)
val AccentDeep = Color(0xFF0F9E7C)
val Pink = Color(0xFFFF4D8D)
val Amber = Color(0xFFFFC857)
val TextPrimary = Color(0xFFF2F3F7)
val TextSecondary = Color(0xFF9A9CAB)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00120D),
    secondary = Pink,
    onSecondary = Color.White,
    background = Ink,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = TextSecondary,
    outline = Stroke,
    error = Color(0xFFFF6B6B),
)

private val typo = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

@Composable
fun CuterTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, typography = typo, content = content)
}
