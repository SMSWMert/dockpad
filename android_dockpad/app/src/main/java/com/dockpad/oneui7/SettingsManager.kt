package com.dockpad.oneui7

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dockpad_prefs", Context.MODE_PRIVATE)

    var lastPcIp: String
        get() = prefs.getString("last_pc_ip", "") ?: ""
        set(value) = prefs.edit().putString("last_pc_ip", value).apply()

    var lastPcName: String
        get() = prefs.getString("last_pc_name", "Monster Abra A7") ?: "Monster Abra A7"
        set(value) = prefs.edit().putString("last_pc_name", value).apply()

    var lastWsPort: Int
        get() = prefs.getInt("last_ws_port", 8765)
        set(value) = prefs.edit().putInt("last_ws_port", value).apply()

    var autoConnect: Boolean
        get() = prefs.getBoolean("auto_connect", true)
        set(value) = prefs.edit().putBoolean("auto_connect", value).apply()

    // 3, 5, 10 minutes or 0 (Off)
    var standbyTimeoutMin: Int
        get() = prefs.getInt("standby_timeout_min", 5)
        set(value) = prefs.edit().putInt("standby_timeout_min", value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", true)
        set(value) = prefs.edit().putBoolean("keep_screen_on", value).apply()

    var nightBrightness: Int
        get() = prefs.getInt("night_brightness", 75)
        set(value) = prefs.edit().putInt("night_brightness", value).apply()

    var ramThreshold: Int
        get() = prefs.getInt("ram_threshold", 85)
        set(value) = prefs.edit().putInt("ram_threshold", value).apply()

    var privacyMode: Boolean
        get() = prefs.getBoolean("privacy_mode", false)
        set(value) = prefs.edit().putBoolean("privacy_mode", value).apply()

    var notifWhatsappEnabled: Boolean
        get() = prefs.getBoolean("notif_whatsapp", true)
        set(value) = prefs.edit().putBoolean("notif_whatsapp", value).apply()

    var notifMailEnabled: Boolean
        get() = prefs.getBoolean("notif_mail", true)
        set(value) = prefs.edit().putBoolean("notif_mail", value).apply()
}
