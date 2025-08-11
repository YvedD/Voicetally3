package com.yvesds.voicetally3.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Hulpklaase voor het werken met de Android SpeechRecognizer API.
 *
 * - Vereenvoudigt instellen en starten/stoppen van spraakherkenning.
 * - Verzorgt lifecycle-beheer en callbacks naar de UI.
 */
class SpeechRecognitionHelper(
    context: Context,
    private val onFinalResult: (String) -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private val speechRecognizer: SpeechRecognizer =
        SpeechRecognizer.createSpeechRecognizer(context.applicationContext)

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech started")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Kan gebruikt worden voor VU-meter visualisatie
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Niet gebruikt
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "Speech ended")
        }

        override fun onError(errorCode: Int) {
            val explanation = getSpeechErrorExplanation(errorCode)
            Log.w(TAG, "Speech recognition error: $explanation")
            onError(explanation)
        }

        override fun onResults(results: Bundle?) {
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            if (matches.isNotEmpty()) {
                onFinalResult(matches.first())
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partials = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            if (partials.isNotEmpty()) {
                onPartialResult(partials.first())
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Niet gebruikt
        }
    }

    init {
        speechRecognizer.setRecognitionListener(recognitionListener)
    }

    /**
     * Start spraakherkenning met Nederlands als taal.
     */
    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "nl-NL")
        }
        speechRecognizer.startListening(intent)
    }

    /**
     * Stop huidige spraakherkenningssessie.
     */
    fun stopListening() {
        speechRecognizer.stopListening()
    }

    /**
     * Ruim resources op. Aanroepen bij onDestroy van je Activity/Fragment.
     */
    fun destroy() {
        speechRecognizer.destroy()
    }

    /**
     * Vertaal foutcodes naar leesbare uitleg voor logging/gebruikersfeedback.
     */
    fun getSpeechErrorExplanation(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT [Netwerk time-out]"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK [Netwerk probleem]"
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO [Audio opname probleem]"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER [Server fout]"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT [Client fout]"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT [Geen spraak binnen timeout]"
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH [Geen match]"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY [Recognizer busy]"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS [Geen permissies]"
            else -> "Onbekende foutcode"
        }
    }

    companion object {
        private const val TAG = "SpeechRecognitionHelper"
    }
}
