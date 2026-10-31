package com.example.aiassistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Speech-to-text (SpeechRecognizer) + text-to-speech. Call from the main thread. */
class VoiceManager(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** [languageTag] e.g. "hi-IN" or "en-IN". */
    fun startListening(
        languageTag: String,
        partial: (String) -> Unit,
        result: (String) -> Unit,
        failed: (Int) -> Unit,
    ) {
        stopListening()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                release()
                failed(error)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                release()
                if (text.isNullOrBlank()) failed(SpeechRecognizer.ERROR_NO_MATCH) else result(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let(partial)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        r.startListening(intent)
    }

    /** Destroy after the current callback returns (safe to call from inside a listener). */
    private fun release() {
        val r = recognizer
        recognizer = null
        if (r != null) Handler(Looper.getMainLooper()).post { r.destroy() }
    }

    fun stopListening() {
        recognizer?.destroy()
        recognizer = null
    }

    /**
     * Speaks [text]. Devanagari text uses the Hindi (India) voice, otherwise English (India).
     * Returns false if the needed voice data is missing (fell back to the default voice).
     */
    fun speak(text: String, languageTag: String): Boolean {
        val engine = tts ?: return true
        if (!ttsReady) return true
        val hindi = text.any { it in '\u0900'..'\u097F' }
        val locale = when {
            hindi -> Locale("hi", "IN")
            languageTag.startsWith("hi") -> Locale.forLanguageTag("en-IN")
            else -> Locale.forLanguageTag(languageTag)
        }
        val r = engine.setLanguage(locale)
        val ok = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ok) engine.setLanguage(Locale.getDefault())
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant_reply")
        return ok
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun destroy() {
        stopListening()
        tts?.shutdown()
        tts = null
    }
}
