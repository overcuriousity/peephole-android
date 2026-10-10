package io.github.overcuriousity.peephole.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

/** Addresses, hashes, origins: the web admin's `.mono`. */
val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)

@Composable
fun PeepholeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        shapes = Shapes(
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(6.dp),
            large = RoundedCornerShape(10.dp),
        ),
        content = content,
    )
}
