package dev.homenetwork.localvoice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Launcher screen: a checklist of what Local Voice needs, plus a box to try it in. */
class SetupActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var micStatus: TextView
    private lateinit var a11yStatus: TextView
    private lateinit var modelStatus: TextView
    private lateinit var buttonStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        micStatus = body()
        a11yStatus = body()
        modelStatus = body()
        buttonStatus = body()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(48)
            setPadding(pad, dp(32), pad, dp(32))
            addView(label("Local Voice", 34f, Color.WHITE))
            addView(body().apply {
                text = "Private voice search for the remote's mic button. Speech is recognized on " +
                    "this device — the app has no internet permission."
            })
            addView(section("1. Microphone", micStatus, button("Allow microphone") {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            }))
            addView(section("2. Accessibility (mic button + typing)", a11yStatus, button("Open accessibility settings") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }))
            addView(body().apply {
                text = "Turn on \"Local Voice\". If it is greyed out (installed outside the Play Store): " +
                    "Settings > Apps > Local Voice > ⋮ > Allow restricted settings, then try again."
                setTextColor(Color.GRAY)
            })
            addView(section("3. Speech model", modelStatus, null))
            addView(section("4. Mic button (only if pressing it does nothing)", buttonStatus,
                button("Learn my mic button", ::learnMicButton)))
            addView(label("Try it", 22f, Color.WHITE).apply { setPadding(0, dp(24), 0, dp(8)) })
            addView(body().apply { text = "Select the box below, press the remote's mic button and say something." })
            addView(EditText(context).apply {
                hint = "Your words appear here"
                inputType = InputType.TYPE_CLASS_TEXT
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
            })
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#101418"))
            addView(column)
        })
    }

    override fun onResume() {
        super.onResume()
        showLearnedKey()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        TextInjectionService.instance?.stopLearning()
        main.removeCallbacksAndMessages(null)
    }

    private fun learnMicButton() {
        val service = TextInjectionService.instance
        if (service == null) {
            buttonStatus.text = "Turn on accessibility (step 2) first."
            return
        }
        buttonStatus.text = "Press your remote's mic button now…"
        val timeout = Runnable {
            service.stopLearning()
            buttonStatus.text = "No button detected. On this device the mic button may not reach apps."
        }
        main.postDelayed(timeout, LEARN_TIMEOUT_MS)
        service.learnMicButton { keyCode ->
            main.removeCallbacks(timeout)
            buttonStatus.text = "✓ Learned ${KeyEvent.keyCodeToString(keyCode)}. Try it below."
        }
    }

    private fun showLearnedKey() {
        val key = TextInjectionService.learnedKey(this)
        buttonStatus.text = if (key == KeyEvent.KEYCODE_UNKNOWN) {
            "Standard mic buttons work automatically."
        } else {
            "✓ Using ${KeyEvent.keyCodeToString(key)} as the mic button"
        }
    }

    private fun refresh() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        micStatus.text = if (mic) "✓ Allowed" else "✗ Not allowed yet"
        a11yStatus.text = if (TextInjectionService.instance != null) "✓ On" else "✗ Off"
        modelStatus.text = if (SpeechEngine.isLoaded) {
            "✓ Loaded (unloads after 3 idle minutes)"
        } else {
            "Loads when you press the mic button, while you speak"
        }
        main.postDelayed(::refresh, 1000)
    }

    private fun section(title: String, status: TextView, action: View?) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(24), 0, 0)
        addView(label(title, 22f, Color.WHITE))
        addView(status)
        action?.let(::addView)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun label(text: String, sizeSp: Float, color: Int) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
    }

    private fun body() = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(Color.LTGRAY)
        gravity = Gravity.START
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_MIC = 1
        private const val LEARN_TIMEOUT_MS = 10_000L
    }
}
