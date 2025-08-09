package com.yvesds.voicetally3.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * ✅ SpeechRecognitionHelper
 * - Encapsuleert Google SpeechRecognizer
 * - Lambda callbacks voor final/partial/error
 * - Geluidstriggers bij relevante events
 */
class SpeechRecognitionHelper(
    private val context: Context,
    private val soundPlayer: SoundPlayer,
    private val onFinalResult: (String) -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            //Log.d(TAG, "🔊 Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            //Log.d(TAG, "🎤 Start spraak")
            soundPlayer.play("start")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            //Log.d(TAG, "🔇 Einde spraak")
            soundPlayer.play("stop")
        }

        override fun onError(error: Int) {
            val explanation = getSpeechErrorExplanation(error)
            //Log.e(TAG, "❌ Fout tijdens spraak: $explanation")
            onError(explanation)
            soundPlayer.play("error")
        }

        override fun onResults(results: Bundle?) {
            val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spoken = data?.joinToString(" ")?.trim().orEmpty()
            //Log.d(TAG, "✅ Definitief resultaat: $spoken")
            onFinalResult(spoken)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val data = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = data?.joinToString(" ")?.trim().orEmpty()
            if (partial.isNotBlank()) {
                Log.d(TAG, "🟡 Tussentijds: $partial")
                onPartialResult(partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
            //Log.d(TAG, "✅ SpeechRecognizer geïnitialiseerd")
        } else {
            val msg = "Spraakherkenning niet beschikbaar op dit toestel"
            Log.e(TAG, "❌ $msg")
            soundPlayer.play("error")
            onError(msg)
        }
    }

    fun startListening(language: String = "nl-NL") {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

            // ⏱️ Snellere detectie via silence thresholds
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 250L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 250L)
        }
        speechRecognizer?.startListening(intent)
        //Log.d(TAG, "🎙️ Start luisteren met taal: $language")
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        //Log.d(TAG, "⏹️ Stop luisteren")
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        //Log.d(TAG, "🗑️ SpeechRecognizer opgeruimd")
    }

    private fun getSpeechErrorExplanation(errorCode: Int): String = when (errorCode) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Netwerk time-out"
        SpeechRecognizer.ERROR_NETWORK -> "Netwerkprobleem"
        SpeechRecognizer.ERROR_AUDIO -> "Audio-opname probleem"
        SpeechRecognizer.ERROR_SERVER -> "Serverfout"
        SpeechRecognizer.ERROR_CLIENT -> "Clientfout (trigger mogelijk te lang)"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Geen spraak binnen timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "Geen match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer bezet"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Geen toestemming voor microfoon"
        else -> "Onbekende foutcode ($errorCode)"
    }

    companion object {
        private const val TAG = "SpeechRecognitionHelper"
    }
}
