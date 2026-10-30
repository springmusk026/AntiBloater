package com.spring.musk.antibloater.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spring.musk.antibloater.adb.Status
import com.spring.musk.antibloater.ui.theme.Caption
import com.spring.musk.antibloater.ui.theme.Card
import com.spring.musk.antibloater.ui.theme.CircleButton
import com.spring.musk.antibloater.ui.theme.LocalPalette
import com.spring.musk.antibloater.ui.theme.PrimaryButton
import com.spring.musk.antibloater.ui.theme.SecondaryButton
import com.spring.musk.antibloater.ui.theme.Space
import com.spring.musk.antibloater.ui.theme.Type

/** What the user can do from the Connect screen. */
class ConnectActions(
    val back: () -> Unit,
    val openAboutPhone: () -> Unit,
    val openWifi: () -> Unit,
    val openWirelessDebugging: () -> Unit,
    val startPairing: () -> Unit,
    val cancelPairing: () -> Unit,
    val submitCode: (String) -> Unit,
    val retry: () -> Unit,
)

private enum class Step(val title: String) {
    Developer("Turn on Developer options"),
    Wifi("Join a Wi-Fi network"),
    Wireless("Turn on Wireless debugging"),
    Pair("Pair AntiBloater"),
}

@Composable
fun ConnectScreen(status: Status, actions: ConnectActions, contentPadding: PaddingValues = PaddingValues()) {
    val p = LocalPalette.current
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding() + Space.s, bottom = contentPadding.calculateBottomPadding() + Space.xxl)
            .padding(horizontal = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", actions.back)
        Spacer(Modifier.height(Space.xs))
        Text(if (status.isConnected) "Connected" else "Connect", style = Type.title, color = p.text)
        Text(
            "Android's wireless debugging lets AntiBloater run the same commands a computer would. " +
                "Everything stays on this phone: the connection is to itself.",
            style = Type.body, color = p.secondary,
        )
        Spacer(Modifier.height(Space.xs))

        if (status == Status.NeedsComputer) {
            ComputerCard(actions)
            return@Column
        }
        val current = when (status) {
            Status.NeedsDeveloperOptions -> Step.Developer
            Status.NeedsWifi -> Step.Wifi
            Status.NeedsWirelessDebugging -> Step.Wireless
            Status.Connected -> null
            else -> Step.Pair
        }
        Card(padding = PaddingValues(Space.l)) {
            Step.entries.forEachIndexed { i, step ->
                val done = current == null || step.ordinal < current.ordinal
                StepRow(i + 1, step.title, done = done, active = step == current, last = i == Step.entries.lastIndex) {
                    when (step) {
                        Step.Developer -> StepBody(
                            "Open About phone and tap Build number seven times. On some phones it's under Software information.",
                            "Open About phone", actions.openAboutPhone)
                        Step.Wifi -> StepBody(
                            "Wireless debugging needs Wi-Fi to switch on. Any network works, it doesn't need internet.",
                            "Open Wi-Fi settings", actions.openWifi)
                        Step.Wireless -> StepBody(
                            "In Developer options, turn on Wireless debugging and allow it on this network.",
                            "Open Developer options", actions.openWirelessDebugging)
                        Step.Pair -> PairBody(status, actions)
                    }
                }
            }
        }
        if (status.isConnected) {
            Card(glow = p.accent) {
                Text("You're set", style = Type.bodyStrong, color = p.text)
                Spacer(Modifier.height(Space.xs))
                Caption("AntiBloater reconnects by itself whenever Wireless debugging is on.")
                Spacer(Modifier.height(Space.m))
                PrimaryButton("Done", actions.back, Modifier.fillMaxWidth())
            }
        }
        PrivacyNote()
    }
}

@Composable
private fun StepRow(n: Int, title: String, done: Boolean, active: Boolean, last: Boolean, body: @Composable () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().animateContentSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(when { done -> p.accent; active -> p.text; else -> p.raised }),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Rounded.Check, null, tint = p.onAccent, modifier = Modifier.size(16.dp))
                else Text("$n", style = Type.small, color = if (active) p.background else p.tertiary)
            }
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else Space.l)) {
            Text(title, style = Type.bodyStrong, color = if (done || active) p.text else p.tertiary,
                modifier = Modifier.padding(top = 4.dp))
            AnimatedVisibility(active) { Column(Modifier.padding(top = Space.s)) { body() } }
        }
    }
}

@Composable
private fun StepBody(text: String, button: String, onClick: () -> Unit) {
    Text(text, style = Type.caption, color = LocalPalette.current.secondary)
    Spacer(Modifier.height(Space.m))
    SecondaryButton(button, onClick)
}

@Composable
private fun PairBody(status: Status, actions: ConnectActions) {
    val p = LocalPalette.current
    when (status) {
        is Status.Pairing -> {
            if (status.port == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = p.accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(Space.s))
                    Text("Waiting for the pairing code", style = Type.caption, color = p.text)
                }
                Spacer(Modifier.height(Space.s))
                Text("In Wireless debugging, tap “Pair device with pairing code”. Then type the code into AntiBloater's notification, without leaving Settings.",
                    style = Type.caption, color = p.secondary)
                Spacer(Modifier.height(Space.m))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    SecondaryButton("Open Wireless debugging", actions.openWirelessDebugging)
                    SecondaryButton("Cancel", actions.cancelPairing)
                }
            } else {
                Text("Pairing code found. Enter the 6 digits shown in Settings:", style = Type.caption, color = p.secondary)
                Spacer(Modifier.height(Space.m))
                CodeField(status, actions.submitCode)
                if (status.error != null) {
                    Spacer(Modifier.height(Space.s))
                    Caption(status.error, color = p.unsafe)
                }
            }
        }
        Status.Connecting, Status.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), color = p.accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(Space.s))
            Text("Connecting…", style = Type.caption, color = p.text)
        }
        is Status.Failed -> {
            Text(status.message, style = Type.caption, color = p.unsafe)
            Spacer(Modifier.height(Space.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                SecondaryButton("Try again", actions.retry)
                SecondaryButton("Pair again", actions.startPairing)
            }
        }
        else -> {
            Text("You'll see a notification. Then in Wireless debugging, tap “Pair device with pairing code” and type the code into the notification.",
                style = Type.caption, color = p.secondary)
            Spacer(Modifier.height(Space.m))
            PrimaryButton("Start pairing", actions.startPairing)
        }
    }
}

@Composable
private fun CodeField(status: Status.Pairing, onSubmit: (String) -> Unit) {
    val p = LocalPalette.current
    var code by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        BasicTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit).take(6) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 22.sp, fontFeatureSettings = "tnum", letterSpacing = 6.sp,
                color = p.text, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(p.accent),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (code.isEmpty()) Text("••••••", style = TextStyle(fontSize = 22.sp, letterSpacing = 6.sp, color = p.tertiary))
                    inner()
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(com.spring.musk.antibloater.ui.theme.Radius.control))
                .background(p.raised).border(1.dp, p.hairline, RoundedCornerShape(com.spring.musk.antibloater.ui.theme.Radius.control))
                .padding(horizontal = Space.m, vertical = Space.m),
        )
        PrimaryButton(if (status.busy) "Pairing…" else "Pair", { onSubmit(code) }, enabled = code.length == 6 && !status.busy)
    }
}

@Composable
private fun ComputerCard(actions: ConnectActions) {
    val p = LocalPalette.current
    Card {
        Text("This Android version needs a computer once", style = Type.bodyStrong, color = p.text)
        Spacer(Modifier.height(Space.s))
        Text("Android 10 and older have no wireless debugging. Turn on USB debugging, connect a computer with adb and run:",
            style = Type.caption, color = p.secondary)
        Spacer(Modifier.height(Space.s))
        Text("adb tcpip 5555", style = Type.mono, color = p.text,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(com.spring.musk.antibloater.ui.theme.Radius.control))
                .background(p.raised).padding(Space.m))
        Spacer(Modifier.height(Space.s))
        Caption("Then unplug and tap Connect. Repeat after each restart.", color = p.tertiary)
        Spacer(Modifier.height(Space.m))
        PrimaryButton("Connect", actions.retry)
    }
}

@Composable
private fun PrivacyNote() {
    val p = LocalPalette.current
    Row(Modifier.padding(horizontal = Space.xs, vertical = Space.s), verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.Lock, null, tint = p.tertiary, modifier = Modifier.size(14.dp).padding(top = 2.dp))
        Spacer(Modifier.width(Space.s))
        Caption2("The pairing key stays in AntiBloater's private storage. Turning off Wireless debugging closes the connection.")
    }
}

@Composable
private fun Caption2(text: String) = Text(text, style = Type.caption, color = LocalPalette.current.tertiary)
