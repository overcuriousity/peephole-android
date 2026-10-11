package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.overcuriousity.peephole.R

// Dark only; hex values are peephole's `00-tokens.css` dark block (design §3).
private val Colors = darkColorScheme(
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

val Success = Color(0xFF5FD39A)

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
fun PeepholeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Type,
        shapes = Shapes(
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(6.dp),
            large = RoundedCornerShape(10.dp),
        ),
        content = content,
    )
}
