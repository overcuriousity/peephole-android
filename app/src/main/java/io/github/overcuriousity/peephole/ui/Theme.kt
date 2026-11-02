package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.overcuriousity.peephole.R

// Hex values are peephole's `assets/css/00-tokens.css`, dark and light blocks.
private val DarkScheme = darkColorScheme(
    background = Color(0xFF0A0B10),
    surface = Color(0xFF10121A),
    surfaceContainer = Color(0xFF171A24),
    surfaceContainerHigh = Color(0xFF171A24),
    surfaceContainerHighest = Color(0xFF1F2330),
    onBackground = Color(0xFFE4E6EE),
    onSurface = Color(0xFFE4E6EE),
    onSurfaceVariant = Color(0xFFA0A4B8),
    outline = Color(0xFF262B40),
    outlineVariant = Color(0xFF212536),
    primary = Color(0xFF4FD1E0),
    onPrimary = Color(0xFF0A0B10),
    primaryContainer = Color(0x244FD1E0),
    onPrimaryContainer = Color(0xFF4FD1E0),
    tertiary = Color(0xFFFF3B3B),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF0A0B10),
    errorContainer = Color(0x26FF6B6B),
    onErrorContainer = Color(0xFFFF6B6B),
)

// Warm paper: base, surface, elevated white cards.
private val LightScheme = lightColorScheme(
    background = Color(0xFFF6F5F0),
    surface = Color(0xFFF6F5F0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFEDE6),
    surfaceContainerHighest = Color(0xFFE8E5DC),
    onBackground = Color(0xFF1F2024),
    onSurface = Color(0xFF1F2024),
    onSurfaceVariant = Color(0xFF4F5159),
    outline = Color(0xFFD9D5C9),
    outlineVariant = Color(0xFFE4E1D7),
    primary = Color(0xFF1F6F8B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0x1F1F6F8B),
    onPrimaryContainer = Color(0xFF1F6F8B),
    secondaryContainer = Color(0x1F1F6F8B),
    onSecondaryContainer = Color(0xFF1F6F8B),
    tertiary = Color(0xFFB30000),
    error = Color(0xFFB3382C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0x1FB3382C),
    onErrorContainer = Color(0xFFB3382C),
)

/** The web's colours Material 3 has no slot for. */
data class PeepholeColors(
    val success: Color,
    val warning: Color,
    val brand: Color,
    /** Severity 0..4, for text and chips. */
    val severity: List<Color>,
    /** Severity 0..4, for chart fills. */
    val severityFill: List<Color>,
    /** An empty chart cell or meter track. */
    val chartTrack: Color,
)

private val DarkPeephole = PeepholeColors(
    success = Color(0xFF5FD39A),
    warning = Color(0xFFF0B344),
    brand = Color(0xFFFF3B3B),
    severity = listOf(Color(0xFF9A9EB2), Color(0xFFF0C34A), Color(0xFFFF9440), Color(0xFFFF5C5C), Color(0xFFD17DFF)),
    severityFill = listOf(Color(0xFF3A3F52), Color(0xFF73561A), Color(0xFFC2531F), Color(0xFFEE5D5D), Color(0xFFE9A7FF)),
    chartTrack = Color(0xFF1F2330),
)

private val LightPeephole = PeepholeColors(
    success = Color(0xFF2B7048),
    warning = Color(0xFF845B16),
    brand = Color(0xFFB30000),
    severity = listOf(Color(0xFF63656C), Color(0xFF7F6100), Color(0xFFB04A00), Color(0xFFB31C1C), Color(0xFF7A1FA2)),
    severityFill = listOf(Color(0xFFC4C1B6), Color(0xFFD99A0B), Color(0xFFD9661C), Color(0xFFBD2F30), Color(0xFF6A2090)),
    chartTrack = Color(0xFFE8E5DC),
)

val LocalPeepholeColors = staticCompositionLocalOf { DarkPeephole }

val Success: Color
    @Composable @ReadOnlyComposable get() = LocalPeepholeColors.current.success

val Warning: Color
    @Composable @ReadOnlyComposable get() = LocalPeepholeColors.current.warning

// The web admin's two families (design v1 §3), bundled under OFL-1.1.
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
private val JetBrainsMono = FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal))

// Inter everywhere; v1 §3 fixes the weights of the three body/label slots.
private val Type = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Inter),
        displayMedium = displayMedium.copy(fontFamily = Inter),
        displaySmall = displaySmall.copy(fontFamily = Inter),
        headlineLarge = headlineLarge.copy(fontFamily = Inter),
        headlineMedium = headlineMedium.copy(fontFamily = Inter),
        headlineSmall = headlineSmall.copy(fontFamily = Inter),
        titleLarge = titleLarge.copy(fontFamily = Inter),
        titleMedium = titleMedium.copy(fontFamily = Inter),
        titleSmall = titleSmall.copy(fontFamily = Inter),
        bodyLarge = bodyLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
        bodyMedium = bodyMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Normal),
        bodySmall = bodySmall.copy(fontFamily = Inter),
        labelLarge = labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = Inter),
        labelSmall = labelSmall.copy(fontFamily = Inter),
    )
}

/** Addresses, hashes, origins: the web admin's `.mono`. */
val MonoMedium = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp)
val MonoSmall = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp)

/** The spacing scale (design v2 §4 item 7); pick from here, not a literal. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

@Composable
fun PeepholeTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPeepholeColors provides if (dark) DarkPeephole else LightPeephole) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = Type,
            shapes = Shapes(
                small = RoundedCornerShape(6.dp),
                medium = RoundedCornerShape(6.dp),
                large = RoundedCornerShape(10.dp),
            ),
            content = content,
        )
    }
}
