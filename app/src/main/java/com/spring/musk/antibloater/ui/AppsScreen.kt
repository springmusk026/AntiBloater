package com.spring.musk.antibloater.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.spring.musk.antibloater.data.Action
import com.spring.musk.antibloater.data.Pkg
import com.spring.musk.antibloater.data.PkgState
import com.spring.musk.antibloater.data.Removal
import com.spring.musk.antibloater.ui.theme.AppIcon
import com.spring.musk.antibloater.ui.theme.Caption
import com.spring.musk.antibloater.ui.theme.Chip
import com.spring.musk.antibloater.ui.theme.EmptyState
import com.spring.musk.antibloater.ui.theme.LevelBadge
import com.spring.musk.antibloater.ui.theme.LocalPalette
import com.spring.musk.antibloater.ui.theme.PrimaryButton
import com.spring.musk.antibloater.ui.theme.Radius
import com.spring.musk.antibloater.ui.theme.Space
import com.spring.musk.antibloater.ui.theme.Tag
import com.spring.musk.antibloater.ui.theme.Type
import com.spring.musk.antibloater.ui.theme.color

@Composable
fun AppsScreen(
    apps: List<Pkg>,
    loading: Boolean,
    filters: Filters,
    counts: Map<Removal?, Int>,
    selection: Set<String>,
    connected: Boolean,
    removeAction: Action,
    onFilters: (Filters) -> Unit,
    onOpen: (Pkg) -> Unit,
    onToggle: (Pkg) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onApply: (Action) -> Unit,
    onConnect: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + Space.s,
                bottom = contentPadding.calculateBottomPadding() + if (selection.isEmpty()) Space.xxl else 96.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = Space.l)) {
                    Text("Apps", style = Type.title, color = p.text)
                    Spacer(Modifier.height(Space.m))
                    SearchField(filters.query) { onFilters(filters.copy(query = it)) }
                }
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.m),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    item { Chip("All", filters.level == null, { onFilters(filters.copy(level = null)) }, count = counts[null]) }
                    items(Removal.entries.toList()) { level ->
                        Chip(level.label, filters.level == level, { onFilters(filters.copy(level = level)) },
                            color = level.color, count = counts[level] ?: 0)
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = Space.l).padding(bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
                    Segmented(Shown.entries.map { it.label }, filters.shown.ordinal) { onFilters(filters.copy(shown = Shown.entries[it])) }
                    Spacer(Modifier.weight(1f))
                    Chip("Your apps", filters.userApps, { onFilters(filters.copy(userApps = !filters.userApps)) })
                }
            }
            if (filters.level != null && apps.isNotEmpty()) item {
                Caption(filters.level.help, Modifier.padding(horizontal = Space.l, vertical = Space.xs), color = p.tertiary)
            }
            if (apps.isEmpty() && !loading) item {
                EmptyState(Icons.Rounded.Inventory2,
                    if (filters.query.isNotBlank()) "No matches" else "Nothing here",
                    if (filters.shown == Shown.Off) "Apps you remove or disable show up here, ready to restore."
                    else "Try another level, or include the apps you installed.")
            }
            items(apps, key = { it.name }) { pkg ->
                AppRow(pkg, selected = pkg.name in selection, selecting = selection.isNotEmpty(),
                    onClick = { if (selection.isEmpty()) onOpen(pkg) else onToggle(pkg) }, onSelect = { onToggle(pkg) })
            }
        }
        AnimatedVisibility(
            selection.isNotEmpty(),
            Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()),
            enter = slideInVertically { it }, exit = slideOutVertically { it },
        ) {
            SelectionBar(selection.size, apps.size, filters.shown, connected, removeAction,
                onSelectAll, onClearSelection, onApply, onConnect)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(p.surface).border(1.dp, p.hairline, CircleShape)
            .padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = p.tertiary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.s))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search name or package", style = Type.body, color = p.tertiary)
            BasicTextField(query, onChange, singleLine = true, textStyle = Type.body.copy(color = p.text),
                cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth())
        }
        if (query.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", tint = p.secondary,
            modifier = Modifier.size(20.dp).clip(CircleShape).combinedClickable(onClick = { onChange("") }))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.height(34.dp).clip(CircleShape).background(p.raised).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.fillMaxHeightCompat().clip(CircleShape)
                    .background(if (on) p.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .combinedClickable(onClick = { onSelect(i) })
                    .padding(horizontal = Space.m),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = Type.small, color = if (on) p.text else p.secondary) }
        }
    }
}

private fun Modifier.fillMaxHeightCompat() = this.then(Modifier.height(28.dp))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(pkg: Pkg, selected: Boolean, selecting: Boolean, onClick: () -> Unit, onSelect: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) p.accent.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onSelect)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.combinedClickable(onClick = onSelect)) {
            AppIcon(pkg.name, pkg.label)
            if (selecting || selected) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape)
                        .background(if (selected) p.accent else p.surface)
                        .border(1.5.dp, if (selected) p.accent else p.tertiary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(Icons.Rounded.Check, null, tint = p.onAccent, modifier = Modifier.size(12.dp)) }
            }
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(pkg.label, style = Type.bodyStrong, color = p.text, maxLines = 1)
            Caption(pkg.name, mono = true, color = p.tertiary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(pkg.removal)
                if (pkg.entry != null) Tag(pkg.source.label)
                when (pkg.state) {
                    PkgState.Removed -> Tag("Removed")
                    PkgState.Disabled -> Tag("Disabled")
                    PkgState.Installed -> if (!pkg.system) Tag("Installed by you")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectionBar(
    count: Int, total: Int, shown: Shown, connected: Boolean, removeAction: Action,
    onSelectAll: () -> Unit, onClear: () -> Unit, onApply: (Action) -> Unit, onConnect: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier.padding(Space.l).fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(Radius.card))
            .clip(RoundedCornerShape(Radius.card))
            .background(p.surface)
            .border(1.dp, p.hairline, RoundedCornerShape(Radius.card))
            .padding(start = Space.l, end = Space.s, top = Space.s, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("$count selected", style = Type.bodyStrong, color = p.text)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                if (count < total) Caption("Select all $total", Modifier.combinedClickable(onClick = onSelectAll), color = p.accent)
                Caption("Clear", Modifier.combinedClickable(onClick = onClear), color = p.secondary)
            }
        }
        when {
            !connected -> PrimaryButton("Connect", onConnect)
            shown == Shown.Off -> PrimaryButton("Restore $count", { onApply(Action.Restore) })
            else -> PrimaryButton("${removeAction.verb} $count", { onApply(removeAction) })
        }
    }
}
