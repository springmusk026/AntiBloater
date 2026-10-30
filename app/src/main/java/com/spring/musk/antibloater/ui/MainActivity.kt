package com.spring.musk.antibloater.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spring.musk.antibloater.BuildConfig
import com.spring.musk.antibloater.adb.Connection
import com.spring.musk.antibloater.app
import com.spring.musk.antibloater.data.Action
import com.spring.musk.antibloater.data.Pkg
import com.spring.musk.antibloater.data.Profile
import com.spring.musk.antibloater.data.Removal
import com.spring.musk.antibloater.ui.theme.AntiBloaterTheme
import com.spring.musk.antibloater.ui.theme.LocalPalette
import com.spring.musk.antibloater.ui.theme.Type
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AntiBloaterTheme { App(model) } }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from Settings: things may have changed there
        model.refresh()
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Rounded.Home),
    Apps("apps", "Apps", Icons.Rounded.Apps),
    History("history", "History", Icons.Rounded.History),
    Tools("tools", "Tools", Icons.Rounded.Build),
}

/** A batch waiting for the user to confirm, because it includes risky packages. */
private data class Pending(val groups: Map<Action, List<Pkg>>) {
    val targets get() = groups.values.flatten()
}

@Composable
private fun App(model: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val p = LocalPalette.current
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val status by model.status.collectAsState()
    val work by model.work.collectAsState()
    var detail by remember { mutableStateOf<Pkg?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }

    fun toast(text: String) = scope.launch { snackbar.showSnackbar(text) }

    /** Runs right away when everything is Recommended (or a restore); otherwise asks first. */
    fun request(groups: Map<Action, List<Pkg>>) {
        val allowed = groups.mapValues { (action, list) ->
            list.filter { action == Action.Restore || model.blockedReason(it) == null }
        }.filterValues { it.isNotEmpty() }
        val skipped = groups.values.sumOf { it.size } - allowed.values.sumOf { it.size }
        if (skipped > 0) toast("$skipped skipped: protected or Unsafe")
        if (allowed.isEmpty()) return
        val risky = allowed.any { (action, list) -> action != Action.Restore && list.any { it.removal != Removal.Recommended } }
        if (risky) pending = Pending(allowed) else model.run(allowed)
    }

    fun request(targets: List<Pkg>, action: Action) = request(mapOf(action to targets))

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        model.connection.startPairing()
        context.startActivity(Connection.wirelessDebuggingIntent())
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(model.exportProfile().toByteArray()) }
        }.onSuccess { toast("Profile saved") }.onFailure { toast("Couldn't save the profile") }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val profile = runCatching { context.contentResolver.openInputStream(uri)!!.use { it.reader().readText() } }
            .getOrNull()?.let(Profile::parse)
        if (profile == null) { toast("That file isn't an AntiBloater profile"); return@rememberLauncherForActivityResult }
        val targets = model.profileTargets(profile)
        if (targets.isEmpty()) toast("Nothing to change: this phone already matches the profile")
        else if (!status.isConnected) { toast("Connect first, then import again"); nav.navigate("connect") }
        else request(targets)
    }

    val connectActions = ConnectActions(
        back = { nav.popBackStack() },
        openAboutPhone = { context.startActivity(Connection.aboutPhoneIntent()) },
        openWifi = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
        openWirelessDebugging = { context.startActivity(Connection.wirelessDebuggingIntent()) },
        startPairing = {
            if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            else { model.connection.startPairing(); context.startActivity(Connection.wirelessDebuggingIntent()) }
        },
        cancelPairing = { model.connection.cancelPairing() },
        submitCode = { code -> scope.launch { model.connection.submitCode(code) } },
        retry = { model.connection.refresh() },
    )

    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route
    Scaffold(
        containerColor = p.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (Tab.entries.any { it.route == route }) NavigationBar(containerColor = p.surface) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = { nav.go(tab.route) },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(tab.label, style = Type.small) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = p.text, selectedTextColor = p.text, indicatorColor = p.raised,
                            unselectedIconColor = p.tertiary, unselectedTextColor = p.tertiary),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(p.background)) {
            NavHost(nav, "home", enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) {
                composable("home") {
                    val home by model.home.collectAsState()
                    HomeScreen(home, status,
                        onReview = { model.showLevel(Removal.Recommended); nav.go("apps") },
                        onConnect = { nav.navigate("connect") },
                        onSettings = { nav.navigate("settings") },
                        onHistory = { nav.go("history") },
                        onRemoved = { model.showLevel(null, Shown.Off); nav.go("apps") },
                        onReapply = { model.reapply(home.cameBack) },
                        contentPadding = padding)
                }
                composable("apps") {
                    val apps by model.visible.collectAsState()
                    val all by model.packages.collectAsState()
                    val filters by model.filters.collectAsState()
                    val counts by model.levelCounts.collectAsState()
                    val selection by model.selection.collectAsState()
                    val action by model.prefs.removeAction.collectAsState()
                    AppsScreen(apps, all == null, filters, counts, selection, status.isConnected, action,
                        onFilters = model::setFilters,
                        onOpen = { detail = it },
                        onToggle = { model.toggle(it.name) },
                        onSelectAll = { model.selectAll(apps) },
                        onClearSelection = model::clearSelection,
                        onApply = { a -> request(apps.filter { it.name in selection }, a) },
                        onConnect = { nav.navigate("connect") },
                        contentPadding = padding)
                }
                composable("history") {
                    val events by model.history.collectAsState()
                    val all by model.packages.collectAsState()
                    val states = all.orEmpty().associate { it.name to it.state }
                    HistoryScreen(events, { states[it] }, status.isConnected,
                        onRestore = { names -> request(all.orEmpty().filter { it.name in names }, Action.Restore) },
                        onExport = { exporter.launch("antibloater-${Build.MODEL.replace(' ', '-').lowercase()}.json") },
                        onImport = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        contentPadding = padding)
                }
                composable("tools") { ToolsScreen({ nav.navigate(it.route) }, padding) }
                composable("connect") { ConnectScreen(status, connectActions, padding) }
                composable("settings") {
                    val action by model.prefs.removeAction.collectAsState()
                    val unsafe by model.prefs.allowUnsafe.collectAsState()
                    val entries by context.app.knowledge.entries.collectAsState()
                    var updating by remember { mutableStateOf(false) }
                    var updatedAt by remember { mutableStateOf(context.app.knowledge.updatedAt) }
                    SettingsScreen(
                        SettingsState(status, action, unsafe, entries.size, updatedAt, updating, BuildConfig.VERSION_NAME),
                        SettingsActions(
                            back = { nav.popBackStack() },
                            connect = { nav.navigate("connect") },
                            forget = { scope.launch { model.connection.forget(); toast("Pairing forgotten") } },
                            setAction = model.prefs::setRemoveAction,
                            setUnsafe = model.prefs::setAllowUnsafe,
                            updateList = {
                                updating = true
                                scope.launch {
                                    context.app.knowledge.update()
                                        .onSuccess { toast("Package list updated: ${Format.thousands(it)} packages"); model.refresh() }
                                        .onFailure { toast("Couldn't update: ${it.message ?: "no connection"}") }
                                    updatedAt = context.app.knowledge.updatedAt
                                    updating = false
                                }
                            },
                            openUrl = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) },
                        ),
                        padding)
                }
                toolRoutes(nav, status.isConnected, padding) { toast(it) }
            }
        }
    }

    detail?.let { pkg ->
        val all by model.packages.collectAsState()
        val fresh = all?.firstOrNull { it.name == pkg.name } ?: pkg
        val action by model.prefs.removeAction.collectAsState()
        Sheet({ detail = null }) {
            AppDetail(fresh, model.blockedReason(fresh), status.isConnected, action,
                related = { name -> all?.firstOrNull { it.name == name } },
                onAction = { a -> detail = null; request(listOf(fresh), a) },
                onConnect = { detail = null; nav.navigate("connect") })
        }
    }
    pending?.let { batch ->
        Sheet({ pending = null }) {
            ConfirmBatch(batch.targets, batch.groups.keys.first(),
                onConfirm = { pending = null; model.run(batch.groups) },
                onCancel = { pending = null })
        }
    }
    work?.let { w ->
        Sheet({ if (w.finished) model.dismissWork() }) {
            WorkProgress(w, onDone = model::dismissWork, onUndo = {
                val undo = w.outcomes.orEmpty().filter { it.ok }.map { it.pkg }
                model.dismissWork()
                model.run(undo, Action.Restore)
            })
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.toolRoutes(
    nav: NavHostController, connected: Boolean, padding: androidx.compose.foundation.layout.PaddingValues, toast: (String) -> Unit,
) {
    composable(Tool.Permissions.route) {
        val tools: ToolsViewModel = viewModel()
        val grants by tools.grants.collectAsState()
        Notices(tools, toast)
        LaunchedEffect(Unit) { tools.loadGrants() }
        PermissionsScreen(grants, connected, tools::revoke, { nav.navigate("connect") }, { nav.popBackStack() }, padding)
    }
    composable(Tool.Trackers.route) {
        val tools: ToolsViewModel = viewModel()
        val scan by tools.scan.collectAsState()
        val context = androidx.compose.ui.platform.LocalContext.current
        TrackersScreen(scan, tools::scanTrackers, { pkg ->
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        }, { nav.popBackStack() }, padding)
    }
    composable(Tool.Background.route) {
        val tools: ToolsViewModel = viewModel()
        val starters by tools.starters.collectAsState()
        Notices(tools, toast)
        LaunchedEffect(connected) { tools.loadStarters() }
        BackgroundScreen(starters, connected, { s, r -> tools.setRestricted(s, r) }, { nav.navigate("connect") }, { nav.popBackStack() }, padding)
    }
    composable(Tool.Animations.route) {
        val tools: ToolsViewModel = viewModel()
        val scale by tools.scale.collectAsState()
        Notices(tools, toast)
        AnimationsScreen(scale, connected, { tools.setScale(it) }, { nav.navigate("connect") }, { nav.popBackStack() }, padding)
    }
}

@Composable
private fun Notices(tools: ToolsViewModel, toast: (String) -> Unit) {
    val notice by tools.notice.collectAsState()
    LaunchedEffect(notice) { notice?.let { toast(it); tools.clearNotice() } }
}

/** Switches tabs without stacking them up. */
private fun NavHostController.go(route: String) = navigate(route) {
    popUpTo(graph.startDestinationId) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

