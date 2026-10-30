package com.spring.musk.antibloater

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.spring.musk.antibloater.adb.Status
import com.spring.musk.antibloater.data.Action
import com.spring.musk.antibloater.data.AppTrackers
import com.spring.musk.antibloater.data.Entry
import com.spring.musk.antibloater.data.Event
import com.spring.musk.antibloater.data.Grant
import com.spring.musk.antibloater.data.Outcome
import com.spring.musk.antibloater.data.Pkg
import com.spring.musk.antibloater.data.PkgState
import com.spring.musk.antibloater.data.Removal
import com.spring.musk.antibloater.data.Sensitive
import com.spring.musk.antibloater.data.Trackers
import com.spring.musk.antibloater.ui.AppDetail
import com.spring.musk.antibloater.ui.AppsScreen
import com.spring.musk.antibloater.ui.ConfirmBatch
import com.spring.musk.antibloater.ui.ConnectActions
import com.spring.musk.antibloater.ui.ConnectScreen
import com.spring.musk.antibloater.ui.Filters
import com.spring.musk.antibloater.ui.HistoryScreen
import com.spring.musk.antibloater.ui.HomeScreen
import com.spring.musk.antibloater.ui.HomeState
import com.spring.musk.antibloater.ui.MainViewModel
import com.spring.musk.antibloater.ui.PermissionsScreen
import com.spring.musk.antibloater.ui.SettingsActions
import com.spring.musk.antibloater.ui.SettingsScreen
import com.spring.musk.antibloater.ui.SettingsState
import com.spring.musk.antibloater.ui.ToolsScreen
import com.spring.musk.antibloater.ui.TrackerScan
import com.spring.musk.antibloater.ui.TrackersScreen
import com.spring.musk.antibloater.ui.Work
import com.spring.musk.antibloater.ui.WorkProgress
import com.spring.musk.antibloater.ui.theme.AntiBloaterTheme
import com.spring.musk.antibloater.ui.theme.LocalPalette
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Renders every screen with realistic data, light and dark, for design review and the README:
 *   ./gradlew recordPaparazziDebug   → app/src/test/snapshots/images
 */
@RunWith(Parameterized::class)
class Screens(private val dark: Boolean) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "dark={0}") fun modes() = listOf(false, true)

        private val uad: Map<String, Entry> by lazy {
            Json { ignoreUnknownKeys = true }.decodeFromString(File("src/main/assets/uad_lists.json").readText())
        }

        private fun pkg(name: String, label: String, state: PkgState = PkgState.Installed, system: Boolean = true) =
            Pkg(name, label, system, state, uad[name])

        val apps = listOf(
            pkg("com.samsung.android.bixby.agent", "Bixby Voice"),
            pkg("com.facebook.appmanager", "Facebook App Manager"),
            pkg("com.facebook.services", "Facebook Services"),
            pkg("com.samsung.android.app.tips", "Tips"),
            pkg("com.samsung.android.arzone", "AR Zone"),
            pkg("com.google.android.apps.tachyon", "Meet"),
            pkg("com.samsung.android.game.gamehome", "Game Launcher"),
            pkg("com.microsoft.skydrive", "OneDrive"),
            pkg("com.google.android.googlequicksearchbox", "Google"),
            pkg("com.samsung.android.app.spage", "Samsung Free"),
            pkg("com.netflix.partner.activation", "Netflix activation"),
        )
        val now = 1_761_800_000_000L
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6.copy(nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT, softButtons = false),
        maxPercentDifference = 0.1,
    )

    private fun shot(name: String, content: @Composable () -> Unit) = paparazzi.snapshot(name) {
        AntiBloaterTheme(dark) {
            Box(Modifier.fillMaxSize().background(LocalPalette.current.background)) { content() }
        }
    }

    private val inset = PaddingValues(top = 40.dp)

    private val events = listOf(
        Event("com.samsung.android.bixby.agent", "Bixby Voice", Action.Uninstall, now - 3_600_000, 2),
        Event("com.facebook.appmanager", "Facebook App Manager", Action.Uninstall, now - 3_600_000, 2),
        Event("com.facebook.services", "Facebook Services", Action.Uninstall, now - 3_600_000, 2),
        Event("com.samsung.android.app.tips", "Tips", Action.Disable, now - 90_000_000, 1),
    )

    @Test fun home() = shot("home") {
        val recommended = apps.filter { it.removal == Removal.Recommended }
        HomeScreen(HomeState("Samsung SM-S911B", false, recommended + recommended + recommended.take(4), 14,
            cameBack = apps.take(2), recent = events.take(3), changes = 27),
            Status.Connected, {}, {}, {}, {}, {}, {}, inset)
    }

    @Test fun homeDisconnected() = shot("home-disconnected") {
        HomeScreen(HomeState("Xiaomi 2201117TG", false, apps.filter { it.removal == Removal.Recommended }, 0,
            emptyList(), emptyList(), 0), Status.NeedsWirelessDebugging, {}, {}, {}, {}, {}, {}, inset)
    }

    @Test fun apps() = shot("apps") {
        val filters = Filters(level = null)
        val list = MainViewModel.filter(apps, filters)
        AppsScreen(list, false, filters, mapOf(null to 11, Removal.Recommended to 7, Removal.Advanced to 3, Removal.Expert to 1),
            setOf(), true, Action.Uninstall, {}, {}, {}, {}, {}, {}, {}, inset)
    }

    @Test fun appsSelecting() = shot("apps-selecting") {
        val filters = Filters()
        val list = MainViewModel.filter(apps, filters)
        AppsScreen(list, false, filters, mapOf(null to 11, Removal.Recommended to list.size, Removal.Advanced to 3),
            list.take(3).map { it.name }.toSet(), true, Action.Uninstall, {}, {}, {}, {}, {}, {}, {}, inset)
    }

    @Test fun detail() = shot("detail") {
        Column(Modifier.padding(20.dp)) {
            AppDetail(apps[0], null, true, Action.Uninstall, { n -> apps.firstOrNull { it.name == n } }, {}, {})
        }
    }

    @Test fun detailAdvanced() = shot("detail-advanced") {
        Column(Modifier.padding(20.dp)) {
            AppDetail(apps.first { it.name == "com.google.android.googlequicksearchbox" }, null, true, Action.Uninstall,
                { null }, {}, {})
        }
    }

    @Test fun confirm() = shot("confirm") {
        Column(Modifier.padding(20.dp)) { ConfirmBatch(apps.take(9), Action.Uninstall, {}, {}) }
    }

    @Test fun progress() = shot("progress") {
        Column(Modifier.padding(20.dp)) { WorkProgress(Work(Action.Uninstall, 12, 7, "Game Launcher"), {}, {}) }
    }

    @Test fun results() = shot("results") {
        Column(Modifier.padding(20.dp)) {
            WorkProgress(Work(Action.Uninstall, 4, 4, outcomes = apps.take(3).map { Outcome(it, true) } +
                Outcome(apps[5], false, "Failure [DELETE_FAILED_INTERNAL_ERROR]")), {}, {})
        }
    }

    @Test fun history() = shot("history") {
        HistoryScreen(events, { if (it == "com.facebook.services") PkgState.Installed else PkgState.Removed }, true, {}, {}, {}, inset)
    }

    private val connect = ConnectActions({}, {}, {}, {}, {}, {}, {}, {})

    @Test fun connectSteps() = shot("connect") { ConnectScreen(Status.NeedsWirelessDebugging, connect, inset) }
    @Test fun connectPairing() = shot("connect-pairing") { ConnectScreen(Status.Pairing(port = 37145), connect, inset) }
    @Test fun connectWaiting() = shot("connect-waiting") { ConnectScreen(Status.Pairing(port = null), connect, inset) }

    @Test fun tools() = shot("tools") { ToolsScreen({}, inset) }

    @Test fun trackers() = shot("trackers") {
        val t = Trackers.known.associateBy { it.name }
        TrackersScreen(TrackerScan(64, 64, listOf(
            AppTrackers("com.example.shop", "ShopNow", listOf("Google AdMob", "Google Firebase Analytics", "AppsFlyer", "Facebook Analytics", "AppLovin").map { t.getValue(it) }),
            AppTrackers("com.example.news", "Daily News", listOf("Google AdMob", "Taboola", "comScore").map { t.getValue(it) }),
            AppTrackers("com.example.run", "RunTrack", listOf("Google Crashlytics", "Amplitude").map { t.getValue(it) }),
        )), {}, {}, {}, inset)
    }

    @Test fun permissions() = shot("permissions") {
        PermissionsScreen(listOf(
            Grant("com.example.shop", "ShopNow", false, Sensitive.Location, listOf("android.permission.ACCESS_FINE_LOCATION")),
            Grant("com.example.run", "RunTrack", false, Sensitive.Location, listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")),
            Grant("com.example.maps", "Maps", false, Sensitive.Location, listOf("android.permission.ACCESS_COARSE_LOCATION")),
            Grant("com.example.cam", "Camera+", false, Sensitive.Camera, listOf("android.permission.CAMERA")),
        ), true, {}, {}, {}, inset)
    }

    @Test fun settings() = shot("settings") {
        SettingsScreen(SettingsState(Status.Connected, Action.Uninstall, false, 5382, null, false, "2.0"),
            SettingsActions({}, {}, {}, {}, {}, {}, {}), inset)
    }
}
