package com.spring.musk.antibloater.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spring.musk.antibloater.data.Removal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

/** Surface + hairline, with an optional soft glow in the top-right corner for the card that matters most. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    glow: Color? = null,
    padding: PaddingValues = PaddingValues(Space.l),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(Radius.card)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, Motion.spring, label = "card")
    Column(
        modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(shape)
            .background(p.surface)
            .then(if (glow != null) Modifier.drawBehind {
                drawRect(Brush.radialGradient(
                    listOf(glow.copy(alpha = if (p.dark) 0.22f else 0.14f), Color.Transparent),
                    center = Offset(size.width, 0f), radius = size.maxDimension * 0.75f))
            } else Modifier)
            // In dark, a faint top highlight separates the card from the background like light from above
            .then(if (p.dark) Modifier.drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.035f), Color.Transparent), endY = size.height * 0.4f))
            } else Modifier)
            .border(1.dp, p.hairline, shape)
            .then(if (onClick != null) Modifier.clickable(source, androidx.compose.material3.ripple(), onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = Space.s, bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Type.heading, color = LocalPalette.current.secondary, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** A small colored dot; breathes only while something is actually happening. */
@Composable
fun StatusDot(color: Color, live: Boolean = false, size: Dp = 8.dp) {
    val alpha = if (live && !LocalInspectionMode.current) {
        rememberInfiniteTransition(label = "dot").animateFloat(
            0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot").value
    } else 1f
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha)))
}

@Composable
fun LevelBadge(removal: Removal, modifier: Modifier = Modifier) {
    val color = removal.color
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = Space.s, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        StatusDot(color, size = 6.dp)
        Text(removal.label, style = Type.small, color = color)
    }
}

/** A neutral pill for facts like "Disabled" or "Google". */
@Composable
fun Tag(text: String, color: Color = LocalPalette.current.secondary) {
    Text(
        text, style = Type.small, color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(LocalPalette.current.raised)
            .padding(horizontal = Space.s, vertical = Space.xs),
    )
}

/** The one main action on a screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = LocalPalette.current.accent,
) {
    val p = LocalPalette.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Motion.spring, label = "press")
    val background by animateColorAsState(if (enabled) color else p.raised, label = "bg")
    val foreground = if (enabled) (if (color == p.accent) p.onAccent else Color.White) else p.tertiary
    Row(
        modifier
            .scale(scale)
            .height(52.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(source, null, enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = foreground, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = Type.bodyStrong, color = foreground, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val p = LocalPalette.current
    Row(
        modifier
            .height(44.dp)
            .clip(CircleShape)
            .border(1.dp, p.hairline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (enabled) p.text else p.tertiary
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(text, style = Type.bodyStrong, color = tint, maxLines = 1)
    }
}

/** Round icon-only button, for secondary actions in headers. */
@Composable
fun CircleButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier.size(40.dp).clip(CircleShape).background(p.surface).border(1.dp, p.hairline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = p.text, modifier = Modifier.size(20.dp)) }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, color: Color? = null, count: Int? = null) {
    val p = LocalPalette.current
    val background by animateColorAsState(if (selected) p.text else p.surface, Motion.quick(), label = "chip")
    val foreground = if (selected) p.background else p.text
    Row(
        Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, if (selected) Color.Transparent else p.hairline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        if (color != null) StatusDot(color, size = 6.dp)
        Text(text, style = Type.small, color = foreground)
        if (count != null) Text("$count", style = Type.small.copy(fontFeatureSettings = "tnum"),
            color = if (selected) p.background.copy(alpha = 0.7f) else p.tertiary)
    }
}

/** App icon from the package manager, cached; a lettered tile in previews and for missing icons. */
@Composable
fun AppIcon(pkg: String, label: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val icon by produceState(IconCache.get(pkg), pkg) {
        if (value == null && !inPreview) value = withContext(Dispatchers.IO) { IconCache.load(context, pkg) }
    }
    val shape = RoundedCornerShape(size * 0.28f)
    if (icon != null) {
        Image(icon!!, null, Modifier.size(size).clip(shape))
    } else {
        val p = LocalPalette.current
        val hues = listOf(p.accent, p.advanced, p.expert, Color(0xFF5B7CFA), Color(0xFF9B6BDF), p.secondary)
        val tint = hues[pkg.hashCode().absoluteValue % hues.size]
        Box(Modifier.size(size).clip(shape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(label.take(1).uppercase(), style = Type.bodyStrong.copy(fontWeight = FontWeight.SemiBold), color = tint,
                textAlign = TextAlign.Center)
        }
    }
}

private object IconCache {
    private val cache = LruCache<String, ImageBitmap>(300)
    fun get(pkg: String): ImageBitmap? = cache.get(pkg)
    fun load(context: android.content.Context, pkg: String): ImageBitmap? = runCatching {
        val drawable = context.packageManager.getApplicationIcon(pkg)
        val px = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1) * 2
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(Canvas(bitmap))
        bitmap.asImageBitmap().also { cache.put(pkg, it) }
    }.getOrNull()
}

/** One line of secondary text that never wraps. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.secondary, mono: Boolean = false) {
    Text(text, modifier, style = if (mono) Type.mono else Type.caption, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Horizontal stacked bar: each segment's width is its share. */
@Composable
fun ShareBar(segments: List<Pair<Color, Int>>, modifier: Modifier = Modifier) {
    val total = segments.sumOf { it.second }.coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(LocalPalette.current.raised),
        horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        segments.filter { it.second > 0 }.forEach { (color, n) ->
            Box(Modifier.weight(n.toFloat() / total).height(8.dp).background(color))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().padding(Space.xxxl), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(p.raised), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.secondary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(Space.l))
        Text(title, style = Type.bodyStrong, color = p.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(body, style = Type.caption, color = p.secondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.l))
        action()
    }
}
