package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Speech Recognition Manager with Continuous Voice Mode support.
 */
class DonarkSpeechManager(
    private val context: Context,
    private val onCommandRecognized: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isContinuousMode = MutableStateFlow(false)
    val isContinuousMode: StateFlow<Boolean> = _isContinuousMode.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private var isPausedForSpeaking = false

    init {
        mainHandler.post {
            initRecognizer()
        }
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition is not available on this device.")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    Log.d(TAG, "onReadyForSpeech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "onBeginningOfSpeech")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                    Log.d(TAG, "onEndOfSpeech")
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    Log.d(TAG, "SpeechRecognizer error: $error")
                    // If continuous mode is active and we are not speaking, restart listening after a brief pause
                    if (_isContinuousMode.value && !isPausedForSpeaking) {
                        mainHandler.postDelayed({
                            if (_isContinuousMode.value && !isPausedForSpeaking) {
                                startListeningInternal()
                            }
                        }, 600)
                    }
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val command = matches?.firstOrNull() ?: ""
                    Log.d(TAG, "Speech recognized: $command")
                    _partialText.value = ""

                    if (command.isNotBlank()) {
                        onCommandRecognized(command)
                    } else if (_isContinuousMode.value && !isPausedForSpeaking) {
                        mainHandler.postDelayed({
                            if (_isContinuousMode.value && !isPausedForSpeaking) {
                                startListeningInternal()
                            }
                        }, 500)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    _partialText.value = text
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening() {
        mainHandler.post {
            startListeningInternal()
        }
    }

    private fun startListeningInternal() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return

        if (speechRecognizer == null) {
            initRecognizer()
        }

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition: ${e.message}")
            _isListening.value = false
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
            _isListening.value = false
        }
    }

    fun setContinuousMode(enabled: Boolean) {
        _isContinuousMode.value = enabled
        if (enabled) {
            startListening()
        } else {
            stopListening()
        }
    }

    /**
     * Called before DONARK speaks so recognition doesn't capture TTS audio.
     */
    fun onTtsStarting() {
        isPausedForSpeaking = true
        stopListening()
    }

    /**
     * Called after DONARK finishes speaking. If continuous mode is on, resumes listening!
     */
    fun onTtsFinished() {
        isPausedForSpeaking = false
        if (_isContinuousMode.value) {
            mainHandler.postDelayed({
                if (_isContinuousMode.value && !isPausedForSpeaking) {
                    startListening()
                }
            }, 300)
        }
    }

    fun destroy() {
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    companion object {
        private const val TAG = "DonarkSpeechManager"
    }
}
