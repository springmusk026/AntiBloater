package com.spring.musk.antibloater.model

/**
 * Curated OEM debloat presets.
 * Each preset contains packages that are safe to remove for that manufacturer.
 */
object OemPresets {

    data class Preset(
        val id: String,
        val name: String,
        val description: String,
        val packages: Set<String>
    )

    val samsung = Preset(
        id = "samsung",
        name = "Samsung",
        description = "Bixby, Galaxy Store bloat, Samsung apps you don't use",
        packages = setOf(
            "com.samsung.android.bixby.agent",
            "com.samsung.android.bixby.service",
            "com.samsung.android.visionintelligence",
            "com.samsung.android.arzone",
            "com.samsung.android.aremoji",
            "com.samsung.android.app.spage",
            "com.samsung.android.game.gamehome",
            "com.samsung.android.game.gametools",
            "com.samsung.android.forest",
            "com.samsung.android.app.tips",
            "com.samsung.android.mobileservice",
            "com.samsung.android.themestore",
            "com.samsung.android.app.watchmanagerstub",
            "com.samsung.android.kidsinstaller",
            "com.samsung.android.app.routines",
            "com.samsung.android.ardrawing",
            "com.samsung.android.app.dressroom",
            "com.sec.android.app.samsungapps",
            "com.samsung.android.smartsuggestions",
            "com.samsung.android.app.sharelive"
        )
    )

    val xiaomi = Preset(
        id = "xiaomi",
        name = "Xiaomi / MIUI",
        description = "Analytics, MIUI bloat, preloaded Chinese apps",
        packages = setOf(
            "com.miui.analytics",
            "com.miui.msa.global",
            "com.miui.daemon",
            "com.miui.hybrid",
            "com.miui.hybrid.accessory",
            "com.xiaomi.glgm",
            "com.xiaomi.midrop",
            "com.xiaomi.mipicks",
            "com.mi.android.globalFileexplorer",
            "com.miui.player",
            "com.miui.videoplayer",
            "com.miui.yellowpage",
            "com.miui.bugreport",
            "com.xiaomi.payment",
            "com.mipay.wallet.in",
            "com.miui.cloudbackup",
            "com.miui.gallery",
            "com.miui.weather2",
            "com.miui.cleanmaster",
            "com.miui.compass"
        )
    )

    val oneplus = Preset(
        id = "oneplus",
        name = "OnePlus",
        description = "OnePlus community, shelf, preloaded apps",
        packages = setOf(
            "com.oneplus.membership",
            "com.oneplus.store",
            "com.oneplus.gamespace",
            "com.oneplus.brickmode",
            "com.oneplus.card",
            "com.oneplus.account",
            "net.oneplus.weather",
            "net.oneplus.launcher",
            "com.oneplus.note",
            "com.oneplus.calculator",
            "com.oneplus.soundrecorder",
            "com.oneplus.screenrecorder",
            "com.heytap.browser",
            "com.heytap.market",
            "com.heytap.music",
            "com.heytap.cloud"
        )
    )

    val pixel = Preset(
        id = "pixel",
        name = "Google Pixel",
        description = "Non-essential Google apps you can remove",
        packages = setOf(
            "com.google.android.apps.magazines",
            "com.google.android.apps.tachyon",
            "com.google.android.videos",
            "com.google.android.music",
            "com.google.android.apps.youtube.music",
            "com.google.android.apps.docs",
            "com.google.android.apps.maps",
            "com.google.android.apps.photos",
            "com.google.android.youtube",
            "com.google.android.apps.googleassistant",
            "com.google.android.projection.gearhead",
            "com.google.android.apps.subscriptions.red",
            "com.google.android.apps.wellbeing",
            "com.google.android.apps.recorder"
        )
    )

    val oppo = Preset(
        id = "oppo",
        name = "Oppo / Realme",
        description = "ColorOS bloat, Heytap services, preloaded apps",
        packages = setOf(
            "com.coloros.gamespace",
            "com.coloros.assistantscreen",
            "com.coloros.compass2",
            "com.coloros.floatassistant",
            "com.coloros.oshare",
            "com.coloros.weather2",
            "com.heytap.browser",
            "com.heytap.cloud",
            "com.heytap.market",
            "com.heytap.music",
            "com.oppo.market",
            "com.oppo.music",
            "com.nearme.gamecenter",
            "com.finshell.wallet"
        )
    )

    val all: List<Preset> = listOf(samsung, xiaomi, oneplus, pixel, oppo)

    /**
     * Detect which preset might apply based on device manufacturer.
     */
    fun detectPreset(): Preset? {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("samsung") -> samsung
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> xiaomi
            manufacturer.contains("oneplus") -> oneplus
            manufacturer.contains("google") -> pixel
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> oppo
            else -> null
        }
    }
}
