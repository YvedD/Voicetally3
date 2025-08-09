package com.yvesds.voicetally3.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ✅ SpeechParsingUseCase (geoptimaliseerd v2)
 * - Verwerkt gesproken tekst naar (soort, aantal) paren
 * - Gebruikt directe alias → soort mapping (sneller)
 * - Ondersteunt fallback matching
 */
@Singleton
class SpeechParsingUseCase @Inject constructor() {

    suspend fun parseSpeech(
        spokenText: String,
        aliasToSpeciesMap: Map<String, String>
    ): List<Pair<String, Int>> = withContext(Dispatchers.Default) {

        if (spokenText.isBlank() || aliasToSpeciesMap.isEmpty()) {
            Log.w(TAG, "⚠️ Lege invoer of lege mapping")
            return@withContext emptyList()
        }

        Log.d(TAG, "🔍 Parsing met ${aliasToSpeciesMap.size} aliassen")

        return@withContext SpeechParser.extractSpeciesChunks(
            spokenText = spokenText.lowercase().trim(),
            aliasToSpeciesMap = aliasToSpeciesMap
        )
    }

    companion object {
        private const val TAG = "SpeechParsingUseCase"
    }
}
