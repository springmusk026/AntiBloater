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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.spring.musk.antibloater.adb.Status
import com.spring.musk.antibloater.data.Action
import com.spring.musk.antibloater.ui.theme.Caption
import com.spring.musk.antibloater.ui.theme.Card
import com.spring.musk.antibloater.ui.theme.CircleButton
import com.spring.musk.antibloater.ui.theme.LocalPalette
import com.spring.musk.antibloater.ui.theme.SecondaryButton
import com.spring.musk.antibloater.ui.theme.SectionHeader
import com.spring.musk.antibloater.ui.theme.Space
import com.spring.musk.antibloater.ui.theme.Type

data class SettingsState(
    val status: Status,
    val removeAction: Action,
    val allowUnsafe: Boolean,
    val listSize: Int,
    /** Null when the list bundled with the app is in use. */
    val listUpdatedAt: Long?,
    val updating: Boolean,
    val version: String,
)

class SettingsActions(
    val back: () -> Unit,
    val connect: () -> Unit,
    val forget: () -> Unit,
    val setAction: (Action) -> Unit,
    val setUnsafe: (Boolean) -> Unit,
    val updateList: () -> Unit,
    val openUrl: (String) -> Unit,
)

@Composable
fun SettingsScreen(state: SettingsState, actions: SettingsActions, contentPadding: PaddingValues = PaddingValues()) {
    val p = LocalPalette.current
    Column(
        Modifier.verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + Space.s, bottom = contentPadding.calculateBottomPadding() + Space.xxl)
            .padding(horizontal = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", actions.back)
        Spacer(Modifier.height(Space.xs))
        Text("Settings", style = Type.title, color = p.text)

        SectionHeader("Connection")
        Card {
            ConnectionLine(state.status, actions.connect)
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (!state.status.isConnected) SecondaryButton("Connect", actions.connect)
                SecondaryButton("Forget pairing", actions.forget)
            }
        }

        SectionHeader("When you remove an app")
        Card(padding = PaddingValues(vertical = Space.xs)) {
            Radio("Uninstall for this user", "Gone from the app drawer and stops running. Data is kept. Recommended.",
                state.removeAction == Action.Uninstall) { actions.setAction(Action.Uninstall) }
            Radio("Disable only", "Stays installed and listed in Settings, but can't run or show up.",
                state.removeAction == Action.Disable) { actions.setAction(Action.Disable) }
        }
        Caption("Both can be undone from History, no reset needed.", Modifier.padding(horizontal = Space.xs), color = p.tertiary)

        SectionHeader("Safety")
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow Unsafe packages", style = Type.bodyStrong, color = p.text)
                    Caption("Removing them can stop the phone from booting.", color = p.unsafe)
                }
                Switch(state.allowUnsafe, actions.setUnsafe, colors = SwitchDefaults.colors(
                    checkedTrackColor = p.unsafe, checkedThumbColor = p.surface,
                    uncheckedTrackColor = p.raised, uncheckedBorderColor = p.hairline, uncheckedThumbColor = p.tertiary))
            }
        }

        SectionHeader("Package list")
        Card {
            Text("Universal Android Debloater (UAD-ng)", style = Type.bodyStrong, color = p.text)
            Caption("${Format.thousands(state.listSize)} packages · " +
                (state.listUpdatedAt?.let { "updated ${Format.date(it)}" } ?: "bundled with this version"))
            Spacer(Modifier.height(Space.m))
            SecondaryButton(if (state.updating) "Updating…" else "Update now", actions.updateList, enabled = !state.updating)
            Spacer(Modifier.height(Space.s))
            Text("Downloads the latest list from GitHub. The only time AntiBloater uses the internet.", style = Type.caption, color = p.tertiary)
        }

        SectionHeader("About")
        Card(padding = PaddingValues(vertical = Space.xs)) {
            Link("Source code", "github.com/springmusk026/AntiBloater") { actions.openUrl("https://github.com/springmusk026/AntiBloater") }
            Link("Package descriptions", "UAD-ng contributors, GPL-3.0") {
                actions.openUrl("https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation")
            }
            Link("adb binary", "LADB by Tyler Nijmeh, GPL-3.0") { actions.openUrl("https://github.com/tytydraco/LADB") }
        }
        Caption("AntiBloater ${state.version} · GPL-3.0", Modifier.padding(Space.xs), color = p.tertiary)
    }
}

@Composable
private fun Radio(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.bodyStrong, color = p.text)
            Text(detail, style = Type.caption, color = p.secondary)
        }
        Spacer(Modifier.width(Space.m))
        Box(Modifier.size(20.dp).clip(CircleShape).background(if (selected) p.accent else p.raised), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(p.onAccent))
        }
    }
}

@Composable
private fun Link(title: String, detail: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.l, vertical = Space.m)) {
        Text(title, style = Type.body, color = p.text)
        Caption(detail, color = p.tertiary)
    }
}
