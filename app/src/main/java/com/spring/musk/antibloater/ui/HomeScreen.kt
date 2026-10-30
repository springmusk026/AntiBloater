package com.spring.musk.antibloater.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spring.musk.antibloater.adb.Status
import com.spring.musk.antibloater.data.Action
import com.spring.musk.antibloater.data.Event
import com.spring.musk.antibloater.data.Source
import com.spring.musk.antibloater.ui.theme.Caption
import com.spring.musk.antibloater.ui.theme.Card
import com.spring.musk.antibloater.ui.theme.CircleButton
import com.spring.musk.antibloater.ui.theme.LocalPalette
import com.spring.musk.antibloater.ui.theme.PrimaryButton
import com.spring.musk.antibloater.ui.theme.SecondaryButton
import com.spring.musk.antibloater.ui.theme.SectionHeader
import com.spring.musk.antibloater.ui.theme.ShareBar
import com.spring.musk.antibloater.ui.theme.Space
import com.spring.musk.antibloater.ui.theme.StatusDot
import com.spring.musk.antibloater.ui.theme.Type
import com.spring.musk.antibloater.ui.theme.entrance
import com.spring.musk.antibloater.ui.theme.reduceMotion

@Composable
fun HomeScreen(
    state: HomeState,
    status: Status,
    onReview: () -> Unit,
    onConnect: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onRemoved: () -> Unit,
    onReapply: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val p = LocalPalette.current
    LazyColumn(
        contentPadding = PaddingValues(
            start = Space.l, end = Space.l,
            top = contentPadding.calculateTopPadding() + Space.s,
            bottom = contentPadding.calculateBottomPadding() + Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AntiBloater", style = Type.title, color = p.text)
                    ConnectionLine(status, onConnect)
                }
                CircleButton(Icons.Rounded.Settings, "Settings", onSettings)
            }
        }
        item { Box(Modifier.entrance(0)) { Hero(state, onReview) } }
        if (state.cameBack.isNotEmpty()) item { Box(Modifier.entrance(1)) { CameBack(state, onReapply) } }
        if (!status.isConnected && status != Status.Checking) item { Box(Modifier.entrance(2)) { ConnectCard(onConnect) } }
        item {
            Row(Modifier.entrance(3), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Tile("Removed or disabled", "${state.removed}", "See them", Modifier.weight(1f), onRemoved)
                Tile("Changes made", "${state.changes}", "History", Modifier.weight(1f), onHistory)
            }
        }
        if (state.recent.isNotEmpty()) {
            item { SectionHeader("Recent") }
            item {
                Card(Modifier.entrance(4), padding = PaddingValues(vertical = Space.xs)) {
                    state.recent.forEach { RecentRow(it) }
                }
            }
        }
    }
}

@Composable
fun ConnectionLine(status: Status, onConnect: () -> Unit) {
    val p = LocalPalette.current
    val (color, text) = when (status) {
        Status.Connected -> p.accent to "Connected through wireless debugging"
        Status.Connecting, Status.Checking -> p.advanced to "Connecting…"
        is Status.Pairing -> p.advanced to "Pairing…"
        is Status.Failed -> p.unsafe to "Not connected · tap to fix"
        else -> p.neutral to "Not connected · browsing only"
    }
    Row(
        Modifier.clickable(enabled = !status.isConnected, onClick = onConnect).padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color, live = status == Status.Connecting || status is Status.Pairing)
        Spacer(Modifier.width(Space.s))
        Caption(text)
    }
}

@Composable
private fun Hero(state: HomeState, onReview: () -> Unit) {
    val p = LocalPalette.current
    val target = state.recommended.size
    // Starts at whatever is known when first drawn (0 while loading) and counts to each new value
    val shown = remember { Animatable(target.toFloat()) }
    val still = reduceMotion()
    LaunchedEffect(target) {
        if (still) shown.snapTo(target.toFloat()) else shown.animateTo(target.toFloat(), com.spring.musk.antibloater.ui.theme.Motion.count())
    }
    Card(glow = p.accent) {
        Text("Safe to remove", style = Type.heading, color = p.secondary)
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (state.loading) "–" else "${kotlin.math.round(shown.value).toInt()}", style = Type.hero, color = p.text)
            Spacer(Modifier.width(Space.s))
            Text("apps", style = Type.body, color = p.secondary, modifier = Modifier.padding(bottom = Space.s))
        }
        Text("Rated Recommended by UAD-ng's reviewers, on this ${state.device}", style = Type.caption, color = p.secondary)
        if (target > 0) {
            Spacer(Modifier.height(Space.l))
            val bySource = state.recommendedBySource
            val colors = sourceColors()
            ShareBar(bySource.map { (s, n) -> colors(s) to n })
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                bySource.take(3).forEach { (source, n) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(colors(source), size = 6.dp)
                        Spacer(Modifier.width(Space.xs))
                        Caption("${source.label} $n")
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
            PrimaryButton("Review $target apps", onReview, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.ArrowForward)
        } else if (!state.loading) {
            Spacer(Modifier.height(Space.m))
            Caption("Nothing left that's rated safe. Advanced and Expert packages are in Apps.", color = p.accent)
        }
        Spacer(Modifier.height(Space.m))
        Caption(listOf("${Format.thousands(state.listSize)} packages known",
            state.lastChange?.let { "changed ${Format.ago(it)}" }).filterNotNull().joinToString(" · "),
            mono = true, color = p.tertiary)
    }
}

/** Sources get shades of the accent: they're all "safe to remove", the split is only where they came from. */
@Composable
private fun sourceColors(): (Source) -> androidx.compose.ui.graphics.Color {
    val p = LocalPalette.current
    return { source ->
        val alpha = when (source) {
            Source.Oem -> 1f; Source.Carrier -> 0.75f; Source.Google -> 0.55f; Source.Misc -> 0.4f
            Source.Aosp -> 0.3f; Source.Unknown -> 0.2f
        }
        p.accent.copy(alpha = alpha)
    }
}

@Composable
private fun CameBack(state: HomeState, onReapply: () -> Unit) {
    val p = LocalPalette.current
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Sync, null, tint = p.advanced, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
            Text("${Format.count(state.cameBack.size, "app")} came back", style = Type.bodyStrong, color = p.text)
        }
        Spacer(Modifier.height(Space.xs))
        Caption(state.cameBack.take(3).joinToString { it.label } + if (state.cameBack.size > 3) " and more" else "")
        Caption("System updates often reinstall removed apps.", color = p.tertiary)
        Spacer(Modifier.height(Space.m))
        SecondaryButton("Remove them again", onReapply)
    }
}

@Composable
private fun ConnectCard(onConnect: () -> Unit) {
    val p = LocalPalette.current
    Card(onClick = onConnect) {
        Text("Connect to make changes", style = Type.bodyStrong, color = p.text)
        Spacer(Modifier.height(Space.xs))
        Text("AntiBloater runs the same commands a computer would, through this phone's own wireless debugging. No root, no PC. Takes about a minute, once.",
            style = Type.caption, color = p.secondary)
        Spacer(Modifier.height(Space.m))
        SecondaryButton("Set up connection", onConnect, icon = Icons.AutoMirrored.Rounded.ArrowForward)
    }
}

@Composable
private fun Tile(label: String, value: String, action: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Card(modifier, onClick = onClick) {
        Caption(label)
        Spacer(Modifier.height(Space.xs))
        Text(value, style = Type.metric, color = p.text)
        Spacer(Modifier.height(Space.s))
        Caption(action + " →", color = p.secondary)
    }
}

@Composable
fun RecentRow(event: Event) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (event.action == Action.Restore) p.secondary else p.accent, size = 6.dp)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(event.label, style = Type.body, color = p.text, maxLines = 1)
            Caption(event.action.past, color = p.tertiary)
        }
        Caption(Format.ago(event.at), mono = true, color = p.tertiary)
    }
}
