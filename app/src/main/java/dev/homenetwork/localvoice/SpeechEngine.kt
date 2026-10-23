package dev.homenetwork.localvoice

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * On-device speech recognition (sherpa-onnx, Moonshine base English, bundled in assets/model).
 *
 * The recognizer loads only when needed and is released after [IDLE_UNLOAD_MS] without use,
 * so nothing heavy stays in memory between searches. Loading runs on a background thread
 * while the microphone is already recording, which hides most of the load time.
 */
object SpeechEngine {
    const val SAMPLE_RATE = 16000
    private const val DIR = "model"
    private const val IDLE_UNLOAD_MS = 3 * 60 * 1000L

    private val loader = Executors.newSingleThreadExecutor { Thread(it, "local-voice-loader") }
    private val main = Handler(Looper.getMainLooper())
    private val unload = Runnable { release() }

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    val isLoaded: Boolean get() = recognizer != null

    /** Starts loading (or returns the loaded recognizer) and postpones the idle unload. */
    fun acquire(context: Context): Future<OfflineRecognizer> {
        main.removeCallbacks(unload)
        val assets = context.applicationContext.assets
        return loader.submit<OfflineRecognizer> {
            recognizer ?: OfflineRecognizer(assets, recognizerConfig()).also { recognizer = it }
        }
    }

    /** Call when a recognition is finished; the model is released after a quiet period. */
    fun done() {
        main.removeCallbacks(unload)
        main.postDelayed(unload, IDLE_UNLOAD_MS)
    }

    // Runs on the loader thread so it can never overlap a load.
    private fun release() {
        loader.execute {
            recognizer?.release()
            recognizer = null
        }
    }

    /** Small voice-activity detector used to notice when the speaker has stopped. Loads instantly. */
    fun newVad(context: Context) = Vad(
        context.applicationContext.assets,
        VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "$DIR/silero_vad.onnx",
                threshold = 0.5f,
                minSilenceDuration = 0.25f,
                minSpeechDuration = 0.1f,
                windowSize = 512,
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 1,
        ),
    )

    private fun recognizerConfig() = OfflineRecognizerConfig(
        modelConfig = OfflineModelConfig(
            moonshine = OfflineMoonshineModelConfig(
                encoder = "$DIR/encoder_model.ort",
                mergedDecoder = "$DIR/decoder_model_merged.ort",
            ),
            tokens = "$DIR/tokens.txt",
            numThreads = 4,
        ),
    )
}
