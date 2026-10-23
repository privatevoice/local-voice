package dev.homenetwork.localvoice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Opened by the remote's mic button (android.speech.action.WEB_SEARCH).
 * Listens once, shows the live transcript, then closes.
 */
class VoiceSearchActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private var listener: Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = text(20f, Color.LTGRAY)
        transcript = text(34f, Color.WHITE)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E6101418"))
            val pad = dp(40)
            setPadding(pad, pad, pad, pad)
            minimumWidth = dp(640)
            addView(status)
            addView(transcript)
        })
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            return
        }
        startListening()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_MIC && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            status.text = "Microphone permission needed"
            main.postDelayed(::finish, CLOSE_DELAY_MS)
        }
    }

    override fun onPause() {
        super.onPause()
        listener?.cancel()
        listener = null
        main.removeCallbacksAndMessages(null)
        if (!isChangingConfigurations) finish()
    }

    private fun startListening() {
        if (listener != null) return
        status.text = "Starting…"
        transcript.text = ""
        listener = Listener(
            context = this,
            onReady = { main.post { status.text = "Listening…" } },
            onPartial = { text -> main.post { transcript.text = text } },
            onFinal = { text ->
                main.post {
                    transcript.text = text
                    val injector = TextInjectionService.instance
                    when {
                        text.isEmpty() -> {
                            status.text = "Didn't catch that"
                            main.postDelayed(::finish, CLOSE_DELAY_MS)
                        }
                        injector != null -> {
                            // Close first so the app underneath gets its window and focus back.
                            injector.insertWhenReady(text)
                            finish()
                        }
                        else -> {
                            status.text = "Heard (turn on Local Voice in Accessibility to type it)"
                            main.postDelayed(::finish, CLOSE_DELAY_MS)
                        }
                    }
                }
            },
            onError = { message ->
                main.post {
                    status.text = message
                    main.postDelayed(::finish, CLOSE_DELAY_MS)
                }
            },
        ).also { it.start() }
    }

    private fun text(sizeSp: Float, color: Int) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        gravity = Gravity.CENTER
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_MIC = 1
        private const val CLOSE_DELAY_MS = 3000L
    }
}
