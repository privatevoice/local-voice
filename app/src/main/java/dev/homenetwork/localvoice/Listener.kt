package dev.homenetwork.localvoice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log

/**
 * Records one utterance and transcribes it on-device.
 * Callbacks arrive on the recording thread; callers post to their own thread.
 */
class Listener(
    private val context: Context,
    private val onReady: () -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    @Volatile private var running = false
    @Volatile private var cancelled = false

    fun start() {
        running = true
        Thread(::run, "local-voice-listener").start()
    }

    /** Stop listening and deliver whatever was heard. */
    fun finish() {
        running = false
    }

    /** Stop listening and deliver nothing. */
    fun cancel() {
        cancelled = true
        running = false
    }

    @SuppressLint("MissingPermission")
    private fun run() {
        val recognizer = try {
            SpeechEngine.get(context)
        } catch (e: Throwable) {
            Log.e(TAG, "engine load failed", e)
            if (!cancelled) onError("Speech engine failed to load")
            return
        }
        if (cancelled) return

        val rate = SpeechEngine.SAMPLE_RATE
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // VOICE_RECOGNITION (not MIC) is what makes Android TV switch the remote's microphone on.
        val audio = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, rate / 5 * 2),
        )
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            if (!cancelled) onError("Microphone unavailable")
            return
        }

        val stream = recognizer.createStream()
        val buffer = ShortArray(rate / 10) // 100 ms
        val deadline = SystemClock.elapsedRealtime() + MAX_LISTEN_MS
        var text = ""
        try {
            audio.startRecording()
            onReady()
            while (running && SystemClock.elapsedRealtime() < deadline) {
                val n = audio.read(buffer, 0, buffer.size)
                if (n <= 0) continue
                stream.acceptWaveform(FloatArray(n) { buffer[it] / 32768f }, rate)
                while (recognizer.isReady(stream)) recognizer.decode(stream)
                val partial = recognizer.getResult(stream).text.trim()
                if (partial != text) {
                    text = partial
                    onPartial(clean(text))
                }
                if (recognizer.isEndpoint(stream)) break
            }
            stream.inputFinished()
            while (recognizer.isReady(stream)) recognizer.decode(stream)
            text = recognizer.getResult(stream).text.trim()
        } finally {
            runCatching { audio.stop() }
            audio.release()
            stream.release()
        }
        if (!cancelled) onFinal(clean(text))
    }

    // The model emits upper-case words; searches read better in lower case.
    private fun clean(text: String) = text.lowercase()

    companion object {
        private const val TAG = "LocalVoice"
        private const val MAX_LISTEN_MS = 15_000L
    }
}
