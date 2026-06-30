package app.pwhs.inject.frida.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MinimalistColorScheme = darkColorScheme(
    primary = AccentWhite,
    onPrimary = MatteBlack,
    secondary = AccentBlue,
    onSecondary = Color.White,
    background = MatteBlack,
    onBackground = TextPrimary,
    surface = DarkGray,
    onSurface = TextPrimary,
    surfaceVariant = LightGray,
    onSurfaceVariant = TextSecondary,
    error = StatusRed,
    onError = Color.White
)

@Composable
fun InjectFridaTheme(
    content: @Composable () -> Unit
) {
    // We force Dark Minimalist theme as requested
    MaterialTheme(
        colorScheme = MinimalistColorScheme,
        typography = Typography,
        content = content
    )
}