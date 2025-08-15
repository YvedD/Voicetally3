package com.yvesds.voicetally3

import android.app.Application
import android.content.Context
import com.yvesds.voicetally3.utils.LocaleHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VoiceTallyApp : Application() {
    override fun attachBaseContext(base: Context) {
        val sharedPrefsHelper = com.yvesds.voicetally3.data.SharedPrefsHelper(base)
        val langCode = sharedPrefsHelper.getString("app_language") ?: "nl"
        val context = LocaleHelper.setLocale(base, langCode)
        super.attachBaseContext(context)
    }

    override fun onCreate() {
        super.onCreate()
        // Geen globale init nodig hier.
    }

    override fun onTerminate() {
        super.onTerminate()
    }
}
