package com.opencode.remote.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.*
import androidx.compose.ui.platform.LocalContext
import android.content.res.Configuration

// ===== 色彩方案 =====

// 淺色主題
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006E54),
    primaryContainer = Color(0xFF84F0D0),
    secondary = Color(0xFF4A635B),
    secondaryContainer = Color(0xFFCDE8DD),
    tertiary = Color(0xFF38666C),
    tertiaryContainer = Color(0xFFBBE9EF),
    surface = Color(0xFFFAFDFB),
    surfaceVariant = Color(0xFFDBE5E1),
    background = Color(0xFFFAFDFB),
    error = Color(0xFFBA1A1A),
    onPrimary = White,
    onPrimaryContainer = Color(0xFF00211A),
    onSecondary = White,
    onSecondaryContainer = Color(0xFF051F19),
    onTertiary = White,
    onTertiaryContainer = Color(0xFF001F22),
    onSurface = Color(0xFF191D1C),
    onSurfaceVariant = Color(0xFF424846),
    onBackground = Color(0xFF191D1C),
    onError = White,
    outline = Color(0xFF737977),
    outlineVariant = Color(0xFFC2C8C5),
    inverseSurface = Color(0xFF2E3130),
    inversePrimary = Color(0xFF66D8BA),
)

// 深色主題
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF66D8BA),
    primaryContainer = Color(0xFF00553E),
    secondary = Color(0xFFB1CCC3),
    secondaryContainer = Color(0xFF324B43),
    tertiary = Color(0xFF9FCDC3),
    tertiaryContainer = Color(0xFF1E4E53),
    surface = Color(0xFF1B1F1E),
    surfaceVariant = Color(0xFF424846),
    background = Color(0xFF1B1F1E),
    error = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF00382A),
    onPrimaryContainer = Color(0xFF84F0D0),
    onSecondary = Color(0xFF1B332C),
    onSecondaryContainer = Color(0xFFCDE8DD),
    onTertiary = Color(0xFF00353A),
    onTertiaryContainer = Color(0xFFBBE9EF),
    onSurface = Color(0xFFE1E3E1),
    onSurfaceVariant = Color(0xFFC2C8C5),
    onBackground = Color(0xFFE1E3E1),
    onError = Color(0xFF690005),
    outline = Color(0xFF8C9290),
    outlineVariant = Color(0xFF424846),
    inverseSurface = Color(0xFFE1E3E1),
    inversePrimary = Color(0xFF006E54),
)

// ===== 主題函式 =====

@Composable
fun OpenCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalContext.current
    val isSystemDark = (view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

// ===== 字型排版 =====

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = -0.25.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

// ===== 形狀 =====
val Shapes = Shapes(
    extraSmall = RoundedCornerSize(4.dp),
    small = RoundedCornerSize(8.dp),
    medium = RoundedCornerSize(12.dp),
    large = RoundedCornerSize(16.dp),
    extraLarge = RoundedCornerSize(24.dp),
)

// ===== 終端機專用色彩 =====
object TerminalColors {
    val Background = Color(0xFF1E1E1E)
    val Foreground = Color(0xFFD4D4D4)
    val Cursor = Color(0xFFFFFFFF)
    val Selection = Color(0xFF264F78)
    
    // ANSI 16 色
    val AnsiBlack = Color(0xFF000000)
    val AnsiRed = Color(0xFFCD3131)
    val AnsiGreen = Color(0xFF0DBC79)
    val AnsiYellow = Color(0xFFE5E510)
    val AnsiBlue = Color(0xFF2472C8)
    val AnsiMagenta = Color(0xFFBC3FBC)
    val AnsiCyan = Color(0xFF11A8CD)
    val AnsiWhite = Color(0xFFE5E5E5)
    
    val AnsiBrightBlack = Color(0xFF666666)
    val AnsiBrightRed = Color(0xFFF14C4C)
    val AnsiBrightGreen = Color(0xFF23D18B)
    val AnsiBrightYellow = Color(0xFFF5F543)
    val AnsiBrightBlue = Color(0xFF3B8EEA)
    val AnsiBrightMagenta = Color(0xFFD670D6)
    val AnsiBrightCyan = Color(0xFF29B8DB)
    val AnsiBrightWhite = Color(0xFFFFFFFF)
}