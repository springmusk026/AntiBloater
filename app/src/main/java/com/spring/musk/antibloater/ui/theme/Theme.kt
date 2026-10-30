package com.spring.musk.antibloater.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spring.musk.antibloater.data.Removal

/** The only spacing values used anywhere. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

object Radius {
    val card = 16.dp
    val control = 10.dp
}

object Motion {
    val spring = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
    fun <T> quick() = tween<T>(220)
    fun <T> count() = tween<T>(800)
}

/**
 * Color carries meaning, nothing else:
 *   accent    – safe to remove / done / connected (the product's "clean" color)
 *   advanced, expert, unsafe – the removal levels, rising in risk
 *   neutral   – unknown, inactive
 */
@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val raised: Color,
    val hairline: Color,
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val advanced: Color,
    val expert: Color,
    val unsafe: Color,
    val neutral: Color,
    val dark: Boolean,
)

val Light = Palette(
    background = Color(0xFFF5F6F3),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFECEEEA),
    hairline = Color(0x14101814),
    text = Color(0xFF111714),
    secondary = Color(0xFF5B6560),
    tertiary = Color(0xFF8E9792),
    accent = Color(0xFF0E8A5F),
    onAccent = Color(0xFFFFFFFF),
    advanced = Color(0xFFB27300),
    expert = Color(0xFFC2410C),
    unsafe = Color(0xFFC62828),
    neutral = Color(0xFF8E9792),
    dark = false,
)

val Dark = Palette(
    background = Color(0xFF0D0F0E),
    surface = Color(0xFF161917),
    raised = Color(0xFF202421),
    hairline = Color(0x1AFFFFFF),
    text = Color(0xFFECEFEC),
    secondary = Color(0xFF9BA59F),
    tertiary = Color(0xFF6B746F),
    accent = Color(0xFF3DD68C),
    onAccent = Color(0xFF052E1C),
    advanced = Color(0xFFF2BE4A),
    expert = Color(0xFFFB923C),
    unsafe = Color(0xFFF87171),
    neutral = Color(0xFF6B746F),
    dark = true,
)

val LocalPalette = staticCompositionLocalOf { Light }

object Type {
    val hero = TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum", letterSpacing = (-1).sp)
    val metric = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val heading = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
    val bodyStrong = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
    val caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val small = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val mono = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace)
}

val Removal.color: Color
    @Composable get() = LocalPalette.current.let {
        when (this) {
            Removal.Recommended -> it.accent
            Removal.Advanced -> it.advanced
            Removal.Expert -> it.expert
            Removal.Unsafe -> it.unsafe
            Removal.Unlisted -> it.neutral
        }
    }

@Composable
fun AntiBloaterTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) Dark else Light
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.accent, onPrimary = p.onAccent,
        background = p.background, onBackground = p.text,
        surface = p.surface, onSurface = p.text, onSurfaceVariant = p.secondary,
        surfaceVariant = p.raised, surfaceContainer = p.surface, surfaceContainerHigh = p.surface,
        surfaceContainerLow = p.background, surfaceContainerHighest = p.raised,
        outline = p.hairline, outlineVariant = p.hairline, error = p.unsafe,
        secondaryContainer = p.raised, onSecondaryContainer = p.text,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = Typography(bodyLarge = Type.body, bodyMedium = Type.caption), content = content)
    }
}
