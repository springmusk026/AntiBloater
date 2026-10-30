package com.spring.musk.antibloater.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.spring.musk.antibloater.data.Action
import com.spring.musk.antibloater.data.Pkg
import com.spring.musk.antibloater.data.PkgState
import com.spring.musk.antibloater.data.Removal
import com.spring.musk.antibloater.ui.theme.AppIcon
import com.spring.musk.antibloater.ui.theme.Caption
import com.spring.musk.antibloater.ui.theme.LevelBadge
import com.spring.musk.antibloater.ui.theme.LocalPalette
import com.spring.musk.antibloater.ui.theme.PrimaryButton
import com.spring.musk.antibloater.ui.theme.SecondaryButton
import com.spring.musk.antibloater.ui.theme.Space
import com.spring.musk.antibloater.ui.theme.Tag
import com.spring.musk.antibloater.ui.theme.Type
import com.spring.musk.antibloater.ui.theme.color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.surface, scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)) {
        Column(Modifier.navigationBarsPadding().padding(start = Space.xl, end = Space.xl, bottom = Space.xl)) { content() }
    }
}

/** Everything known about one package, and what can be done to it. */
@Composable
fun AppDetail(
    pkg: Pkg,
    blocked: String?,
    connected: Boolean,
    removeAction: Action,
    related: (String) -> Pkg?,
    onAction: (Action) -> Unit,
    onConnect: () -> Unit,
) {
    val p = LocalPalette.current
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(pkg.name, pkg.label, size = 52.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(pkg.label, style = Type.title, color = p.text, maxLines = 2)
                Caption(pkg.name, mono = true, color = p.tertiary)
            }
        }
        Spacer(Modifier.height(Space.m))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LevelBadge(pkg.removal)
            Tag(pkg.source.label)
            if (pkg.state != PkgState.Installed) Tag(pkg.state.name)
        }
        Spacer(Modifier.height(Space.l))
        Notice(pkg.removal)
        val description = pkg.entry?.readable.orEmpty()
        if (description.isNotEmpty()) {
            Spacer(Modifier.height(Space.l))
            Text("What it does", style = Type.heading, color = p.secondary)
            Spacer(Modifier.height(Space.xs))
            Text(description, style = Type.body, color = p.text)
        }
        Related("Needed by", pkg.entry?.neededBy.orEmpty(), related)
        Related("Depends on", pkg.entry?.dependencies.orEmpty(), related)
        Spacer(Modifier.height(Space.xl))
        when {
            pkg.state != PkgState.Installed ->
                if (connected) PrimaryButton("Restore", { onAction(Action.Restore) }, Modifier.fillMaxWidth())
                else PrimaryButton("Connect to restore", onConnect, Modifier.fillMaxWidth())
            blocked != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = p.unsafe, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.s))
                Caption(blocked, color = p.unsafe)
            }
            !connected -> PrimaryButton("Connect to remove", onConnect, Modifier.fillMaxWidth())
            else -> {
                val other = if (removeAction == Action.Uninstall) Action.Disable else Action.Uninstall
                val danger = pkg.removal >= Removal.Expert
                PrimaryButton(removeAction.verb, { onAction(removeAction) }, Modifier.fillMaxWidth(),
                    color = if (danger) pkg.removal.color else p.accent)
                Spacer(Modifier.height(Space.s))
                SecondaryButton("${other.verb} instead", { onAction(other) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.s))
                Caption("You can restore it from History at any time, without a reset.", Modifier.align(Alignment.CenterHorizontally),
                    color = p.tertiary)
            }
        }
    }
}

@Composable
private fun Notice(removal: Removal) {
    val p = LocalPalette.current
    val color = removal.color
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(com.spring.musk.antibloater.ui.theme.Radius.control))
            .background(color.copy(alpha = 0.10f)).padding(Space.m),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(if (removal == Removal.Recommended) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber, null,
            tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.s))
        Text(removal.help, style = Type.caption, color = p.text)
    }
}

@Composable
private fun Related(title: String, names: List<String>, related: (String) -> Pkg?) {
    if (names.isEmpty()) return
    val p = LocalPalette.current
    Spacer(Modifier.height(Space.l))
    Text(title, style = Type.heading, color = p.secondary)
    Spacer(Modifier.height(Space.xs))
    names.forEach { name ->
        val other = related(name)
        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (other?.state == PkgState.Installed) p.accent else p.neutral))
            Spacer(Modifier.width(Space.s))
            Caption(other?.label ?: name, Modifier.weight(1f), color = p.text)
            Caption(when (other?.state) { null -> "not on phone"; PkgState.Installed -> "on phone"; else -> other.state.name.lowercase() },
                color = p.tertiary)
        }
    }
}

/** Shown before a batch that includes anything riskier than Recommended. */
@Composable
fun ConfirmBatch(targets: List<Pkg>, action: Action, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val p = LocalPalette.current
    val risky = targets.groupingBy { it.removal }.eachCount().filterKeys { it != Removal.Recommended }
    Text("${action.verb} ${Format.count(targets.size, "app")}?", style = Type.title, color = p.text)
    Spacer(Modifier.height(Space.s))
    Text("Some of these aren't rated Recommended. Removing them can turn off features you use.", style = Type.body, color = p.secondary)
    Spacer(Modifier.height(Space.l))
    risky.toSortedMap().forEach { (level, n) ->
        Row(Modifier.padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(level)
            Spacer(Modifier.width(Space.s))
            Caption(Format.count(n, "app"), color = p.text)
        }
    }
    Spacer(Modifier.height(Space.xl))
    PrimaryButton("${action.verb} ${targets.size}", onConfirm, Modifier.fillMaxWidth(),
        color = risky.keys.maxOrNull()?.color ?: p.accent)
    Spacer(Modifier.height(Space.s))
    SecondaryButton("Cancel", onCancel, Modifier.fillMaxWidth())
}

/** Progress while a batch runs, then what actually happened. */
@Composable
fun WorkProgress(work: Work, onDone: () -> Unit, onUndo: () -> Unit) {
    val p = LocalPalette.current
    val outcomes = work.outcomes
    if (outcomes == null) {
        Text(when (work.action) { Action.Uninstall -> "Removing"; Action.Disable -> "Disabling"; Action.Restore -> "Restoring" },
            style = Type.title, color = p.text)
        Spacer(Modifier.height(Space.xs))
        Caption("${work.done} of ${work.total} · ${work.current}")
        Spacer(Modifier.height(Space.l))
        Dots(work.done, work.total)
        Spacer(Modifier.height(Space.xl))
        return
    }
    val ok = outcomes.count { it.ok }
    val failed = outcomes.filterNot { it.ok }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (failed.isEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber, null,
            tint = if (failed.isEmpty()) p.accent else p.advanced, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Space.s))
        Text("${work.action.past} ${Format.count(ok, "app")}", style = Type.title, color = p.text)
    }
    Spacer(Modifier.height(Space.xs))
    Caption(if (failed.isEmpty()) "Checked with the system: every change took effect." else "${failed.size} didn't change:")
    if (failed.isNotEmpty()) {
        Spacer(Modifier.height(Space.s))
        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            failed.forEach {
                Column(Modifier.padding(vertical = Space.xs)) {
                    Text(it.pkg.label, style = Type.bodyStrong, color = p.text)
                    Caption(it.message, color = p.unsafe)
                }
            }
        }
    }
    Spacer(Modifier.height(Space.xl))
    PrimaryButton("Done", onDone, Modifier.fillMaxWidth())
    if (ok > 0 && work.action != Action.Restore) {
        Spacer(Modifier.height(Space.s))
        SecondaryButton("Undo", onUndo, Modifier.fillMaxWidth())
    }
}

/** A dotted track that fills as items finish. Caps at 40 dots. */
@Composable
private fun Dots(done: Int, total: Int) {
    val p = LocalPalette.current
    val n = total.coerceAtMost(40)
    val lit = if (total == 0) 0 else (done * n) / total
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(n) { i ->
            Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(if (i < lit) p.accent else p.raised))
        }
    }
}
