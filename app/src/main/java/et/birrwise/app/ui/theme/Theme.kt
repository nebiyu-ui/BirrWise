package et.birrwise.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F5C3F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F0D1),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4C6358),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF092016),
    background = Color(0xFFF7FBF6),
    onBackground = Color(0xFF181D1A),
    surface = Color(0xFFF7FBF6),
    onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F0),
    surfaceContainer = Color(0xFFEBF0EA),
    surfaceContainerHigh = Color(0xFFE5EAE5),
    surfaceContainerHighest = Color(0xFFDFE4DF),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD8B2),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF0F5C3F),
    onPrimaryContainer = Color(0xFFB8F0D1),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCEE9DA),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDFE4DE),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDFE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

/**
 * Colors with a financial meaning. Screens must pair them with an icon or a label
 * so that income and expenses are never told apart by color alone.
 */
@Immutable
data class FinanceColors(
    val income: Color,
    val expense: Color
)

private val LightFinanceColors = FinanceColors(
    income = Color(0xFF1B7F4B),
    expense = Color(0xFFB3261E)
)

private val DarkFinanceColors = FinanceColors(
    income = Color(0xFF7AD9A6),
    expense = Color(0xFFFFB4AB)
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }

@Composable
fun BirrWiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val financeColors = if (darkTheme) DarkFinanceColors else LightFinanceColors
    CompositionLocalProvider(LocalFinanceColors provides financeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
