package com.spring.musk.antibloater.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.spring.musk.antibloater.data.AppTrackers
import com.spring.musk.antibloater.data.Grant
import com.spring.musk.antibloater.data.Sensitive
import com.spring.musk.antibloater.data.Starter
import com.spring.musk.antibloater.ui.theme.AppIcon
import com.spring.musk.antibloater.ui.theme.Caption
import com.spring.musk.antibloater.ui.theme.Card
import com.spring.musk.antibloater.ui.theme.Chip
import com.spring.musk.antibloater.ui.theme.CircleButton
import com.spring.musk.antibloater.ui.theme.EmptyState
import com.spring.musk.antibloater.ui.theme.LocalPalette
import com.spring.musk.antibloater.ui.theme.PrimaryButton
import com.spring.musk.antibloater.ui.theme.SecondaryButton
import com.spring.musk.antibloater.ui.theme.Space
import com.spring.musk.antibloater.ui.theme.Tag
import com.spring.musk.antibloater.ui.theme.Type

enum class Tool(val title: String, val blurb: String, val icon: ImageVector, val route: String) {
    Permissions("Permission audit", "Which apps can use your camera, microphone, location and more. Take access away in one tap.",
        Icons.Rounded.VerifiedUser, "tool/permissions"),
    Trackers("Tracker scan", "Finds advertising and analytics SDKs inside the apps you installed.", Icons.Rounded.Radar, "tool/trackers"),
    Background("Background apps", "Apps that start when the phone boots. Restrict the ones that don't need to.",
        Icons.Rounded.PowerSettingsNew, "tool/background"),
    Animations("Animation speed", "Make the whole phone feel faster by shortening system animations.", Icons.Rounded.Animation, "tool/animations"),
}

@Composable
fun ToolsScreen(onOpen: (Tool) -> Unit, contentPadding: PaddingValues = PaddingValues()) {
    val p = LocalPalette.current
    LazyColumn(
        contentPadding = padded(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item { Text("Tools", style = Type.title, color = p.text) }
        items(Tool.entries.toList()) { tool ->
            Card(onClick = { onOpen(tool) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(p.raised), contentAlignment = Alignment.Center) {
                        Icon(tool.icon, null, tint = p.text, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(tool.title, style = Type.bodyStrong, color = p.text)
                        Spacer(Modifier.height(2.dp))
                        Text(tool.blurb, style = Type.caption, color = p.secondary)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.tertiary)
                }
            }
        }
    }
}

private fun padded(contentPadding: PaddingValues) = PaddingValues(start = Space.l, end = Space.l,
    top = contentPadding.calculateTopPadding() + Space.s, bottom = contentPadding.calculateBottomPadding() + Space.xxl)

private fun LazyListScope.toolHeader(tool: Tool, onBack: () -> Unit, subtitle: String) = item {
    val p = LocalPalette.current
    Column {
        CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        Spacer(Modifier.height(Space.m))
        Text(tool.title, style = Type.title, color = p.text)
        Spacer(Modifier.height(Space.xs))
        Text(subtitle, style = Type.caption, color = p.secondary)
        Spacer(Modifier.height(Space.xs))
    }
}

@Composable
private fun NeedsConnection(onConnect: () -> Unit, what: String) {
    Card {
        Text("Connect to $what", style = Type.bodyStrong, color = LocalPalette.current.text)
        Spacer(Modifier.height(Space.xs))
        Caption("You can look around now; changes need the wireless debugging connection.")
        Spacer(Modifier.height(Space.m))
        SecondaryButton("Set up connection", onConnect)
    }
}

// ─── Permissions ──────────────────────────────────────────────────────────────

@Composable
fun PermissionsScreen(
    grants: List<Grant>?, connected: Boolean, onRevoke: (Grant) -> Unit, onConnect: () -> Unit, onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val p = LocalPalette.current
    var group by remember { mutableStateOf(Sensitive.Location) }
    var system by remember { mutableStateOf(false) }
    val visible = grants.orEmpty().filter { system || !it.system }
    val counts = visible.groupingBy { it.group }.eachCount()
    val rows = visible.filter { it.group == group }.sortedBy { it.label.lowercase() }
    LazyColumn(contentPadding = padded(contentPadding), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        toolHeader(Tool.Permissions, onBack, "Apps holding each permission right now. Revoking is the same as switching it off in Settings; the app can ask again.")
        if (!connected) item { NeedsConnection(onConnect, "revoke permissions") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                items(Sensitive.entries.filter { (counts[it] ?: 0) > 0 || it == group }) { g ->
                    Chip(g.label, g == group, { group = g }, count = counts[g] ?: 0)
                }
            }
        }
        item { Chip("Include system apps", system, { system = !system }) }
        if (grants != null && rows.isEmpty()) item {
            EmptyState(Icons.Rounded.Shield, "No apps", "Nothing ${if (system) "" else "you installed "}holds ${group.label.lowercase()} access.")
        }
        if (rows.isNotEmpty()) item {
            Card(padding = PaddingValues(vertical = Space.xs)) {
                rows.forEach { g ->
                    AppLine(g.pkg, g.label, if (g.system) "System app" else g.permissions.joinToString { it.substringAfterLast('.') }) {
                        if (connected) Caption("Revoke", Modifier.clickable { onRevoke(g) }.padding(Space.xs), color = p.unsafe)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLine(pkg: String, label: String, detail: String, trailing: @Composable () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(pkg, label, size = 36.dp)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.body, color = p.text, maxLines = 1)
            Caption(detail, color = p.tertiary)
        }
        trailing()
    }
}

// ─── Trackers ─────────────────────────────────────────────────────────────────

@Composable
fun TrackersScreen(scan: TrackerScan, onScan: () -> Unit, onOpenApp: (String) -> Unit, onBack: () -> Unit,
                   contentPadding: PaddingValues = PaddingValues()) {
    val p = LocalPalette.current
    val results = scan.results
    LazyColumn(contentPadding = padded(contentPadding), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        toolHeader(Tool.Trackers, onBack, "Looks for the code of ${com.spring.musk.antibloater.data.Trackers.known.size} known tracking SDKs inside each app. Nothing is uploaded.")
        item {
            Card(glow = if (results != null && results.isNotEmpty()) p.advanced else null) {
                when {
                    scan.running -> {
                        Text("Scanning ${scan.done} of ${scan.total}", style = Type.bodyStrong, color = p.text)
                        Spacer(Modifier.height(Space.m))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { if (scan.total == 0) 0f else scan.done / scan.total.toFloat() },
                            modifier = Modifier.fillMaxWidth().clip(CircleShape), color = p.accent, trackColor = p.raised)
                    }
                    results == null -> {
                        Text("Scan the apps you installed", style = Type.bodyStrong, color = p.text)
                        Spacer(Modifier.height(Space.xs))
                        Caption("Takes a few seconds per hundred apps.")
                        Spacer(Modifier.height(Space.m))
                        PrimaryButton("Start scan", onScan)
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${results.size}", style = Type.hero, color = p.text)
                            Spacer(Modifier.width(Space.s))
                            Text(if (results.size == 1) "app has trackers" else "apps have trackers", style = Type.body,
                                color = p.secondary, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        Caption("out of ${scan.total} you installed · ${results.sumOf { it.trackers.size }} trackers in all")
                        Spacer(Modifier.height(Space.m))
                        SecondaryButton("Scan again", onScan)
                    }
                }
            }
        }
        if (!results.isNullOrEmpty()) item {
            Card(padding = PaddingValues(vertical = Space.xs)) { results.forEach { TrackerRow(it, onOpenApp) } }
        }
    }
}

@Composable
private fun TrackerRow(app: AppTrackers, onOpenApp: (String) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable { onOpenApp(app.pkg) }.padding(horizontal = Space.l, vertical = Space.m)) {
        AppIcon(app.pkg, app.label, size = 36.dp)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.label, style = Type.bodyStrong, color = p.text, maxLines = 1, modifier = Modifier.weight(1f))
                Caption("${app.trackers.size}", mono = true, color = p.advanced)
            }
            Spacer(Modifier.height(Space.xs))
            FlowTags(app.trackers.map { it.name })
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowTags(tags: List<String>) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { Tag(it) }
    }
}

// ─── Background apps ──────────────────────────────────────────────────────────

@Composable
fun BackgroundScreen(
    starters: List<Starter>?, connected: Boolean, onRestrict: (Starter, Boolean) -> Unit, onConnect: () -> Unit, onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val p = LocalPalette.current
    var system by remember { mutableStateOf(false) }
    val rows = starters.orEmpty().filter { system || !it.system }
    LazyColumn(contentPadding = padded(contentPadding), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        toolHeader(Tool.Background, onBack, "Restricted apps can't run in the background until you open them, which stops most of them starting at boot. Messaging apps may deliver notifications late.")
        if (!connected) item { NeedsConnection(onConnect, "restrict apps") }
        item { Chip("Include system apps", system, { system = !system }) }
        if (rows.isNotEmpty()) item {
            Card(padding = PaddingValues(vertical = Space.xs)) {
                rows.forEach { s ->
                    AppLine(s.pkg, s.label, when (s.restricted) { true -> "Restricted"; false -> "Can run in background"; null -> "Starts at boot" }) {
                        if (connected && s.restricted != null) Switch(
                            checked = s.restricted, onCheckedChange = { onRestrict(s, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent,
                                uncheckedTrackColor = p.raised, uncheckedBorderColor = p.hairline, uncheckedThumbColor = p.tertiary))
                    }
                }
            }
        }
    }
}

// ─── Animation speed ──────────────────────────────────────────────────────────

@Composable
fun AnimationsScreen(scale: Float?, connected: Boolean, onSet: (Float) -> Unit, onConnect: () -> Unit, onBack: () -> Unit,
                     contentPadding: PaddingValues = PaddingValues()) {
    val p = LocalPalette.current
    val options = listOf(0f to "Off", 0.5f to "0.5×", 0.75f to "0.75×", 1f to "1× (default)")
    LazyColumn(contentPadding = padded(contentPadding), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        toolHeader(Tool.Animations, onBack, "Sets window, transition and animator speed together, the same three options in Developer options.")
        if (!connected) item { NeedsConnection(onConnect, "change the speed") }
        item {
            Card(padding = PaddingValues(vertical = Space.xs)) {
                options.forEach { (value, label) ->
                    val on = scale == value
                    Row(Modifier.fillMaxWidth().clickable(enabled = connected) { onSet(value) }
                        .padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = Type.body, color = p.text, modifier = Modifier.weight(1f))
                        Box(Modifier.size(20.dp).clip(CircleShape).background(if (on) p.accent else p.raised),
                            contentAlignment = Alignment.Center) {
                            if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(p.onAccent))
                        }
                    }
                }
            }
        }
        item { Caption(if (scale == null) "Right now the three settings differ." else "0.5× is the usual sweet spot: snappier, still smooth.", color = p.tertiary) }
    }
}
