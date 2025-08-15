package com.yvesds.voicetally3.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.yvesds.voicetally3.R
import com.yvesds.voicetally3.data.SettingsKeys
import com.yvesds.voicetally3.data.SharedPrefsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bestandsnaam is TonePlayer.kt, maar klasse heet SoundPlayer (zoals elders gebruikt).
 * - Laadt korte UI-geluiden.
 * - Respecteert SettingsKeys.ENABLE_EXTRA_SOUNDS.
 */
@Singleton
class SoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPrefsHelper: SharedPrefsHelper
) {
    private val soundPool: SoundPool
    private val soundMap = mutableMapOf<String, Int>()
    @Volatile private var loaded = false

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        // ✅ Laden van sounds
        soundMap["start"] = soundPool.load(context, R.raw.start_recording, 1)
        soundMap["stop"] = soundPool.load(context, R.raw.stop_recording, 1)
        soundMap["success"] = soundPool.load(context, R.raw.succes, 1)
        soundMap["nosuccess"] = soundPool.load(context, R.raw.partial_unrecognized, 1)
        soundMap["partial"] = soundPool.load(context, R.raw.partial_recognized, 1)
        soundMap["error"] = soundPool.load(context, R.raw.error, 1)
        soundMap["bell"] = soundPool.load(context, R.raw.bell, 1)

        soundPool.setOnLoadCompleteListener { _, _, status ->
            loaded = status == 0
            Log.d("SoundPlayer", if (loaded) "✅ Sounds loaded" else "❌ Failed to load sounds")
        }
    }

    fun play(tag: String) {
        // Check via instelling of geluiden actief zijn
        val enabled = sharedPrefsHelper.getBoolean(SettingsKeys.ENABLE_EXTRA_SOUNDS, true)
        if (!enabled) {
            Log.d("SoundPlayer", "🔇 Sounds disabled by user settings")
            return
        }
        if (!loaded) {
            Log.w("SoundPlayer", "⚠️ Sounds not loaded yet")
            return
        }
        val soundId = soundMap[tag]
        if (soundId != null) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
            Log.d("SoundPlayer", "▶️ Played: $tag")
        } else {
            Log.e("SoundPlayer", "❌ Sound not found for tag: $tag")
        }
    }

    fun release() {
        try {
            soundPool.release()
            Log.d("SoundPlayer", "🧹 SoundPool released")
        } catch (_: Exception) {
        }
    }
}
