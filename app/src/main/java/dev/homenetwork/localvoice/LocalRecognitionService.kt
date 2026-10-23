package dev.homenetwork.localvoice

import android.content.Context
import android.content.ContextParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log

/** System speech recognizer backed by the on-device engine. Apps reach it via SpeechRecognizer. */
class LocalRecognitionService : RecognitionService() {
    private val main = Handler(Looper.getMainLooper())
    private var listener: Listener? = null

    override fun onStartListening(intent: Intent?, callback: Callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.i(TAG, "onStartListening from ${callback.callingAttributionSource}")
        }
        listener?.cancel()
        var speechBegun = false
        listener = Listener(
            context = this,
            audioContext = callerContext(callback),
            onReady = { post { callback.readyForSpeech(Bundle()) } },
            onPartial = { text ->
                post {
                    if (!speechBegun) {
                        speechBegun = true
                        callback.beginningOfSpeech()
                    }
                    callback.partialResults(results(text))
                }
            },
            onFinal = { text ->
                post {
                    callback.endOfSpeech()
                    if (text.isEmpty()) callback.error(SpeechRecognizer.ERROR_NO_MATCH)
                    else callback.results(results(text))
                }
            },
            onError = { post { callback.error(SpeechRecognizer.ERROR_AUDIO) } },
        ).also { it.start() }
    }

    override fun onStopListening(callback: Callback) {
        listener?.finish()
    }

    override fun onCancel(callback: Callback) {
        listener?.cancel()
        listener = null
    }

    override fun onDestroy() {
        listener?.cancel()
        super.onDestroy()
    }

    // Record on behalf of the calling app: Android only lets a background service open the mic
    // when the recording is attributed to the foreground app that asked for it.
    private fun callerContext(callback: Callback): Context =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            createContext(ContextParams.Builder().setNextAttributionSource(callback.callingAttributionSource).build())
        } else {
            this
        }

    // The caller may have gone away; a dead callback is not our problem.
    private fun post(block: () -> Unit) = main.post { runCatching(block) }

    private fun results(text: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
        putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(1f))
    }

    companion object {
        private const val TAG = "LocalVoice"
    }
}
