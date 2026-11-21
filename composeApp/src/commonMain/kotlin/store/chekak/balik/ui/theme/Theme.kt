package store.chekak.balik.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Палитра «Балика»: тёплый оранжевый акцент на спокойном фоне. */
private val CheKakOrange = Color(0xFFE8642A)
private val CheKakOrangeDark = Color(0xFF9C3F14)
private val CheKakCream = Color(0xFFFFF6F0)
private val CheKakInk = Color(0xFF221A16)
private val CheKakDarkSurface = Color(0xFF1A1512)

private val LightColors = lightColorScheme(
    primary = CheKakOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = CheKakOrangeDark,
    secondary = Color(0xFF74604B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB8),
    onSecondaryContainer = Color(0xFF5B422C),
    tertiary = Color(0xFF5C6BC0),
    background = CheKakCream,
    onBackground = CheKakInk,
    surface = Color(0xFFFFFBF8),
    onSurface = CheKakInk,
    surfaceVariant = Color(0xFFF4E3D8),
    onSurfaceVariant = Color(0xFF52443B),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB596),
    onPrimary = Color(0xFF542100),
    primaryContainer = CheKakOrangeDark,
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFFE3C0A5),
    onSecondary = Color(0xFF412D1A),
    secondaryContainer = Color(0xFF59422F),
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = Color(0xFFBAC3FF),
    background = CheKakDarkSurface,
    onBackground = Color(0xFFF1DFD6),
    surface = Color(0xFF221A16),
    onSurface = Color(0xFFF1DFD6),
    surfaceVariant = Color(0xFF52443B),
    onSurfaceVariant = Color(0xFFD7C2B5),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

/**
 * Тема приложения. На Android 12+ используются динамические цвета системы,
 * на iOS — фирменная палитра.
 */
@Composable
fun CheKakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val dynamic = dynamicColorScheme(darkTheme)
    val colors = dynamic ?: if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
