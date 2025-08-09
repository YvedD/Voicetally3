package com.yvesds.voicetally3.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedPrefsHelper @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("VoiceTally3Prefs", Context.MODE_PRIVATE)

    fun setString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        Log.d("SharedPrefsHelper", "✅ setString: $key = $value")
    }

    fun getString(key: String, default: String? = null): String? {
        val value = prefs.getString(key, default)
        Log.d("SharedPrefsHelper", "✅ getString: $key = $value")
        return value
    }

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        Log.d("SharedPrefsHelper", "✅ setBoolean: $key = $value")
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        val value = prefs.getBoolean(key, default)
        Log.d("SharedPrefsHelper", "✅ getBoolean: $key = $value")
        return value
    }
    fun setDouble(key: String, value: Double) {
        prefs.edit().putString(key, value.toString()).apply()
    }

    fun getDouble(key: String, default: Double = 0.0): Double {
        return prefs.getString(key, default.toString())?.toDoubleOrNull() ?: default
    }

    fun saveEmailList(emails: List<String>) {
        val jsonArray = JSONArray(emails)
        prefs.edit().putString("email_list", jsonArray.toString()).apply()
        Log.d("SharedPrefsHelper", "✅ Email list opgeslagen: $emails")
    }
    fun isPerHourEnabled(): Boolean {
        return getBoolean("per_hour_enabled", false)
    }

    fun setPerHourEnabled(enabled: Boolean) {
        setBoolean("per_hour_enabled", enabled)
    }

    fun getEmailList(): List<String> {
        val json = prefs.getString("email_list", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            List(jsonArray.length()) { i -> jsonArray.getString(i) }
                .also { Log.d("SharedPrefsHelper", "✅ Email list geladen: $it") }
        } catch (e: Exception) {
            Log.e("SharedPrefsHelper", "❌ Fout bij parsen van email_list", e)
            emptyList()
        }
    }
}
