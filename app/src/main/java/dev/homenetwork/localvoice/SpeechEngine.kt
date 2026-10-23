package dev.homenetwork.localvoice

import android.content.Context
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

/**
 * On-device streaming speech recognizer (sherpa-onnx, streaming Zipformer English model
 * bundled in assets/model). Loaded once per process; loading takes a second or two.
 */
object SpeechEngine {
    const val SAMPLE_RATE = 16000
    private const val DIR = "model"

    @Volatile
    private var recognizer: OnlineRecognizer? = null

    val isLoaded: Boolean get() = recognizer != null

    fun get(context: Context): OnlineRecognizer =
        recognizer ?: synchronized(this) {
            recognizer ?: OnlineRecognizer(context.applicationContext.assets, config())
                .also { recognizer = it }
        }

    private fun config() = OnlineRecognizerConfig(
        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
        modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = "$DIR/encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx",
                decoder = "$DIR/decoder-epoch-99-avg-1-chunk-16-left-128.onnx",
                joiner = "$DIR/joiner-epoch-99-avg-1-chunk-16-left-128.onnx",
            ),
            tokens = "$DIR/tokens.txt",
            numThreads = 4,
            modelType = "zipformer2",
        ),
        endpointConfig = EndpointConfig(
            // Nothing said for 3 s: give up.
            rule1 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 3.0f, minUtteranceLength = 0f),
            // Something said, then 0.8 s of silence: done.
            rule2 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = 0.8f, minUtteranceLength = 0f),
            // Hard cap on one utterance.
            rule3 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 0f, minUtteranceLength = 15f),
        ),
        enableEndpoint = true,
    )
}
