package com.spring.musk.antibloater.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** True when rendering stills (screenshots, tests): every animation shows its final state. */
val LocalStill = staticCompositionLocalOf { false }

/** Android's "Remove animations" sets the animator scale to 0; honor it like Reduce Motion. */
@Composable
fun reduceMotion(): Boolean {
    if (LocalStill.current) return true
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Cards arrive in order: fade, 6dp lift and a hair of scale, staggered by [index]. Only on first appearance. */
fun Modifier.entrance(index: Int): Modifier = composed {
    val still = reduceMotion()
    val progress = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        delay(index * 45L)
        progress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        val t = progress.value
        alpha = t
        translationY = (1 - t) * 6.dp.toPx()
        scaleX = 0.985f + 0.015f * t
        scaleY = scaleX
    }
}

data class NavItem(val label: String, val icon: ImageVector)

/**
 * Floating pill navigation. The selection is one shape that slides between items,
 * so moving between tabs reads as one motion rather than two icons recoloring.
 */
@Composable
fun PillNav(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = CircleShape
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(if (p.dark) 0.dp else 12.dp, shape, ambientColor = p.text.copy(alpha = 0.12f), spotColor = p.text.copy(alpha = 0.12f))
            .clip(shape)
            .background(p.surface)
            .border(1.dp, p.hairline, shape)
            .padding(Space.xs),
    ) {
        val slot = maxWidth / items.size
        val x by animateDpAsState(slot * selected.coerceAtLeast(0), spring(dampingRatio = 0.86f, stiffness = 500f), label = "nav")
        if (selected >= 0) Box(Modifier.offset(x = x).width(slot).fillMaxHeight().clip(shape).background(p.raised))
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(shape)
                        .clickable(remember { MutableInteractionSource() }, null) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(item.icon, item.label, tint = if (on) p.text else p.tertiary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(Space.xs))
                    Text(item.label, style = Type.small, color = if (on) p.text else p.tertiary)
                }
            }
        }
    }
}

/** Placeholder rows while a list loads, shaped like the real ones. */
@Composable
fun SkeletonRows(count: Int = 6) {
    val p = LocalPalette.current
    Column {
        repeat(count) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(p.raised))
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(listOf(0.5f, 0.38f, 0.62f)[i % 3]).height(12.dp).clip(CircleShape).background(p.raised))
                    Spacer(Modifier.height(Space.s))
                    Box(Modifier.fillMaxWidth(listOf(0.7f, 0.55f, 0.8f)[i % 3]).height(8.dp).clip(CircleShape).background(p.raised.copy(alpha = 0.6f)))
                }
            }
        }
    }
}
