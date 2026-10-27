package com.garza.dreamcatcher.dream

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Continuous dictation on top of [SpeechRecognizer]. The platform recognizer stops after each
 * pause, so this restarts it until the user taps stop or stays silent for a while.
 * Must be used from the main thread.
 */
class SpeechDictation(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        /** Words heard so far in the current phrase; replaced on every call. */
        fun onPartial(text: String)

        /** A finished phrase to append to the dream. */
        fun onPhrase(text: String)

        fun onListeningChanged(listening: Boolean)

        fun onError(message: String)
    }

    val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var wantListening = false
    private var silentRounds = 0

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
    }

    fun start() {
        if (!isAvailable || wantListening) return
        wantListening = true
        silentRounds = 0
        listener.onListeningChanged(true)
        listen()
    }

    fun stop() {
        if (!wantListening) return
        wantListening = false
        handler.removeCallbacksAndMessages(null)
        // stopListening still delivers the final words of the current phrase via onResults.
        recognizer?.stopListening()
        listener.onListeningChanged(false)
    }

    fun destroy() {
        wantListening = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(recognitionListener)
            recognizer = it
        }
        r.startListening(recognizerIntent)
    }

    private fun restartSoon() {
        handler.postDelayed({ if (wantListening) listen() }, RESTART_DELAY_MS)
    }

    private fun fail(message: String) {
        wantListening = false
        listener.onPartial("")
        listener.onListeningChanged(false)
        listener.onError(message)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            listener.onPartial(partialResults.bestMatch())
        }

        override fun onResults(results: Bundle?) {
            val text = results.bestMatch()
            listener.onPartial("")
            if (text.isNotBlank()) {
                silentRounds = 0
                listener.onPhrase(text)
            }
            if (wantListening) restartSoon()
        }

        override fun onError(error: Int) {
            listener.onPartial("")
            if (!wantListening) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (++silentRounds >= MAX_SILENT_ROUNDS) stop() else restartSoon()
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    recognizer?.cancel()
                    if (++silentRounds >= MAX_SILENT_ROUNDS) {
                        fail("The speech recognizer is busy. Tap the mic to try again, or type.")
                    } else {
                        restartSoon()
                    }
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    fail("Microphone permission is needed to speak your dream.")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                    fail("Speech recognition needs a connection right now. You can type instead.")
                else -> fail("Couldn't hear you (error $error). Tap the mic to try again, or type.")
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun Bundle?.bestMatch(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private companion object {
        const val RESTART_DELAY_MS = 150L
        /** Each silent round is roughly 5–10 s, so this stops after about half a minute of quiet. */
        const val MAX_SILENT_ROUNDS = 4
    }
}
