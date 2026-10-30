package com.spring.musk.antibloater.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spring.musk.antibloater.data.Action
import com.spring.musk.antibloater.data.Event
import com.spring.musk.antibloater.data.PkgState
import com.spring.musk.antibloater.ui.theme.AppIcon
import com.spring.musk.antibloater.ui.theme.Caption
import com.spring.musk.antibloater.ui.theme.Card
import com.spring.musk.antibloater.ui.theme.EmptyState
import com.spring.musk.antibloater.ui.theme.LocalPalette
import com.spring.musk.antibloater.ui.theme.SecondaryButton
import com.spring.musk.antibloater.ui.theme.Space
import com.spring.musk.antibloater.ui.theme.Type

@Composable
fun HistoryScreen(
    events: List<Event>,
    stateOf: (String) -> PkgState?,
    connected: Boolean,
    onRestore: (List<String>) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val p = LocalPalette.current
    val batches = events.groupBy { it.batch }.toList()
    LazyColumn(
        contentPadding = PaddingValues(start = Space.l, end = Space.l,
            top = contentPadding.calculateTopPadding() + Space.s, bottom = contentPadding.calculateBottomPadding() + Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item {
            Text("History", style = Type.title, color = p.text)
            Spacer(Modifier.height(Space.xs))
            Text("Every change, checked with the system. Restore anything, any time.", style = Type.caption, color = p.secondary)
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                SecondaryButton("Export profile", onExport, icon = Icons.Rounded.Upload, enabled = events.isNotEmpty())
                SecondaryButton("Import", onImport, icon = Icons.Rounded.Download)
            }
        }
        if (batches.isEmpty()) item {
            EmptyState(Icons.Rounded.History, "No changes yet",
                "What you remove or disable shows up here. Import a profile to repeat changes from another phone.")
        }
        items(batches, key = { it.first }) { (_, batch) -> BatchCard(batch, stateOf, connected, onRestore) }
    }
}

@Composable
private fun BatchCard(batch: List<Event>, stateOf: (String) -> PkgState?, connected: Boolean, onRestore: (List<String>) -> Unit) {
    val p = LocalPalette.current
    var expanded by remember { mutableStateOf(false) }
    val action = batch.first().action
    val restorable = batch.filter { it.action != Action.Restore && stateOf(it.pkg).let { s -> s != null && s != PkgState.Installed } }
    val shown = if (expanded) batch else batch.take(4)
    Card(padding = PaddingValues(vertical = Space.m)) {
        Row(Modifier.padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${action.past} ${Format.count(batch.size, "app")}", style = Type.bodyStrong, color = p.text)
                Caption(Format.ago(batch.first().at), mono = true, color = p.tertiary)
            }
            if (connected && restorable.size > 1) SecondaryButton("Restore all", { onRestore(restorable.map { it.pkg }) })
        }
        Spacer(Modifier.height(Space.s))
        shown.forEach { e ->
            val state = stateOf(e.pkg)
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(e.pkg, e.label, size = 32.dp)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(e.label, style = Type.body, color = p.text, maxLines = 1)
                    Caption(when {
                        state == null -> "No longer on this phone"
                        e.action == Action.Restore -> "Restored"
                        state == PkgState.Installed -> "Back on the phone"
                        else -> state.name
                    }, color = if (state == PkgState.Installed && e.action != Action.Restore) p.advanced else p.tertiary)
                }
                if (connected && e in restorable) Caption("Restore",
                    Modifier.padding(start = Space.s).clickableText { onRestore(listOf(e.pkg)) }, color = p.accent)
            }
        }
        if (batch.size > 4) Caption(if (expanded) "Show less" else "Show ${batch.size - 4} more",
            Modifier.padding(horizontal = Space.l, vertical = Space.xs).clickableText { expanded = !expanded }, color = p.accent)
    }
}

private fun Modifier.clickableText(onClick: () -> Unit) = clickable(onClick = onClick).padding(vertical = Space.xs)
