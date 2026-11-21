package store.chekak.balik.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Динамические цвета доступны только на Android 12+. На остальных платформах
 * возвращается null, и тема использует фирменную палитру.
 */
@Composable
expect fun dynamicColorScheme(darkTheme: Boolean): ColorScheme?
