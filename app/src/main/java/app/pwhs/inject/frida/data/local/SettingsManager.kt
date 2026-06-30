package app.pwhs.inject.frida.data.local

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("frida_settings", Context.MODE_PRIVATE)

    fun getPort(): String = prefs.getString("port", "27042") ?: "27042"
    fun setPort(port: String) = prefs.edit().putString("port", port).apply()

    fun isStealthMode(): Boolean = prefs.getBoolean("stealth_mode", false)
    fun setStealthMode(enabled: Boolean) = prefs.edit().putBoolean("stealth_mode", enabled).apply()

    fun isStartOnBoot(): Boolean = prefs.getBoolean("start_on_boot", false)
    fun setStartOnBoot(enabled: Boolean) = prefs.edit().putBoolean("start_on_boot", enabled).apply()
}
