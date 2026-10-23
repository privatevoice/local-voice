package dev.homenetwork.localvoice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * Records one utterance and transcribes it on-device.
 *
 * Recording starts immediately (so the remote's mic light comes on at once) while the speech
 * model loads in the background; a voice-activity detector ends the recording once the speaker
 * stops, then the whole utterance is transcribed. Callbacks arrive on the recording thread.
 *
 * [audioContext] is the context the recording is attributed to. A RecognitionService passes
 * a context chained to the calling app, so the mic is opened on that (foreground) app's behalf.
 */
class Listener(
    private val context: Context,
    private val audioContext: Context = context,
    private val onReady: () -> Unit,
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
        val engine = SpeechEngine.acquire(context)
        try {
            val audio = record() ?: return
            if (cancelled) return
            if (audio.isEmpty()) {
                onFinal("")
                return
            }
            val waitStart = SystemClock.elapsedRealtime()
            val recognizer = try {
                engine.get()
            } catch (e: Exception) {
                Log.e(TAG, "engine load failed", e)
                if (!cancelled) onError("Speech engine failed to load")
                return
            }
            val decodeStart = SystemClock.elapsedRealtime()
            val stream = recognizer.createStream()
            val text = try {
                stream.acceptWaveform(audio, SpeechEngine.SAMPLE_RATE)
                recognizer.decode(stream)
                recognizer.getResult(stream).text
            } finally {
                stream.release()
            }
            val end = SystemClock.elapsedRealtime()
            Log.i(TAG, "audio ${audio.size * 1000 / SpeechEngine.SAMPLE_RATE}ms, waited for model ${decodeStart - waitStart}ms, decode ${end - decodeStart}ms")
            if (!cancelled) onFinal(clean(text))
        } finally {
            SpeechEngine.done()
        }
    }

    /**
     * Records until the speaker stops. Returns the speech (with a little lead-in), an empty array
     * if nothing was said, or null if the microphone could not be opened.
     */
    @SuppressLint("MissingPermission")
    private fun record(): FloatArray? {
        val rate = SpeechEngine.SAMPLE_RATE
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // VOICE_RECOGNITION (not MIC) is what makes Android TV switch the remote's microphone on.
        val audio = AudioRecord.Builder().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setContext(audioContext)
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            setBufferSizeInBytes(maxOf(minBuffer, rate / 5 * 2))
        }.build()
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            if (!cancelled) onError("Microphone unavailable")
            return null
        }

        val vad = SpeechEngine.newVad(context)
        val samples = FloatRecorder()
        val window = ShortArray(VAD_WINDOW)
        val started = SystemClock.elapsedRealtime()
        var speechStart = -1
        var silentSamples = 0
        try {
            audio.startRecording()
            onReady()
            while (running) {
                val n = audio.read(window, 0, window.size)
                if (n <= 0) continue
                val chunk = FloatArray(n) { window[it] / 32768f }
                samples.add(chunk)
                vad.acceptWaveform(chunk)
                val elapsed = SystemClock.elapsedRealtime() - started
                if (vad.isSpeechDetected()) {
                    if (speechStart < 0) speechStart = samples.size - n
                    silentSamples = 0
                } else if (speechStart >= 0) {
                    silentSamples += n
                    if (silentSamples >= rate * END_SILENCE_MS / 1000) break
                } else if (elapsed > NO_SPEECH_TIMEOUT_MS) {
                    break
                }
                if (elapsed > MAX_LISTEN_MS) break
            }
        } finally {
            runCatching { audio.stop() }
            audio.release()
            vad.release()
        }
        if (speechStart < 0) return FloatArray(0)
        // Keep a short lead-in before detected speech so the first word isn't clipped.
        val from = maxOf(0, speechStart - rate * LEAD_IN_MS / 1000)
        return samples.toArray(from)
    }

    // Moonshine writes sentences ("Law and order svu."); a search box wants just the words.
    private fun clean(text: String) = text.trim().trimEnd('.', ',', '!', '?', ';', ':')

    /** Growable float buffer (avoids boxing a few hundred thousand samples). */
    private class FloatRecorder {
        private var data = FloatArray(SpeechEngine.SAMPLE_RATE * 4)
        var size = 0
            private set

        fun add(chunk: FloatArray) {
            if (size + chunk.size > data.size) data = data.copyOf(maxOf(data.size * 2, size + chunk.size))
            chunk.copyInto(data, size)
            size += chunk.size
        }

        fun toArray(from: Int) = data.copyOfRange(from, size)
    }

    companion object {
        private const val TAG = "LocalVoice"
        private const val VAD_WINDOW = 512           // Silero VAD window at 16 kHz
        private const val END_SILENCE_MS = 700       // pause that ends the utterance
        private const val LEAD_IN_MS = 300
        private const val NO_SPEECH_TIMEOUT_MS = 6000L
        private const val MAX_LISTEN_MS = 12_000L
    }
}
