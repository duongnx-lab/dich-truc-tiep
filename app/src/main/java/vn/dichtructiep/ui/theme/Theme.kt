package vn.dichtructiep.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF2B3A9B)
private val IndigoLight = Color(0xFFBAC3FF)
private val Teal = Color(0xFF00897B)
private val TealLight = Color(0xFF5EDBC6)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2F1E6),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFB4462B),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFF8F9FF),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF0E1E7A),
    primaryContainer = Color(0xFF283593),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = TealLight,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFB2F1E6),
    tertiary = Color(0xFFFFB4A1),
    background = Color(0xFF11131A),
    surface = Color(0xFF11131A),
)

/** Màu nhấn cho người A / người B trong chế độ hội thoại. */
val SideAColor = Color(0xFF3949AB)
val SideBColor = Color(0xFF00897B)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
