package com.androidcallrecorder.app.record

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class LiveTranscriber(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private val parts = StringBuilder()

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        stop()
        parts.clear()
        val rec = SpeechRecognizer.createSpeechRecognizer(context)
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = restart()
            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) restart()
            }
            override fun onResults(results: Bundle?) {
                append(results)
                restart()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val t = first(partialResults)
                if (t.isNotBlank()) {
                    RecordingSession.update { it.copy(liveTranscript = (parts.toString() + " " + t).trim()) }
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer = rec
        rec.startListening(intent())
    }

    fun text(): String = parts.toString().trim()

    fun stop() {
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    private fun restart() {
        try {
            recognizer?.startListening(intent())
        } catch (_: Exception) {
        }
    }

    private fun append(results: Bundle?) {
        val t = first(results)
        if (t.isBlank()) return
        if (parts.isNotEmpty()) parts.append(' ')
        parts.append(t)
        RecordingSession.update { it.copy(liveTranscript = parts.toString()) }
    }

    private fun first(results: Bundle?): String {
        return results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
    }

    private fun intent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }
}
