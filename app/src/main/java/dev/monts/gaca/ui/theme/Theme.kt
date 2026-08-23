package dev.monts.gaca.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// One theme, light-only, matching the approved design canvas — the whole
// point of the design is a calm, specific palette, not one that redefines
// itself under system dark mode. Revisit if a dark variant is ever wanted.
private val GacaColorScheme = lightColorScheme(
    primary = HeaderAccent,
    onPrimary = OnAccent,
    background = PageBackground,
    onBackground = TextPrimary,
    surface = PageBackground,
    onSurface = TextPrimary,
    surfaceVariant = ChipBackground,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor,
    error = SeverityUrgentDot,
    errorContainer = SeverityUrgentBg,
)

@Composable
fun GacaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GacaColorScheme,
        typography = GacaTypography,
        content = content,
    )
}
