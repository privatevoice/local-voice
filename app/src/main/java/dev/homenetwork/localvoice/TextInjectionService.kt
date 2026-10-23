package dev.homenetwork.localvoice

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Types recognized speech into the text field of whatever app is on screen.
 *
 * Being an enabled accessibility service also gives this app the "in use" process status
 * Android requires before a background service (LocalRecognitionService) may open the mic.
 */
class TextInjectionService : AccessibilityService() {

    private var listener: Listener? = null

    override fun onServiceConnected() {
        instance = this
        // This service stays running, so load the model now rather than on the first button press.
        Thread({ runCatching { SpeechEngine.get(this) } }, "local-voice-preload").start()
    }

    override fun onDestroy() {
        listener?.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Listens once (from this always-on service, which may use the mic) and types the result. */
    fun listenAndType() {
        listener?.cancel()
        listener = Listener(
            context = this,
            onReady = {},
            onPartial = {},
            onFinal = { text ->
                main.post {
                    if (text.isEmpty()) toast("Didn't catch that") else insertWhenReady(text)
                }
            },
            onError = { message -> main.post { toast(message) } },
        ).also { it.start() }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    /**
     * Takes over the remote's mic button so it works even while Google's voice search is still
     * installed. Both the press and the release are consumed so nothing else reacts to it.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode !in MIC_KEYS) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) listenAndType()
        return true
    }

    override fun onInterrupt() = Unit

    /**
     * Types [text] into the app on screen, retrying briefly until that app's window is active
     * (it is hidden from accessibility while any window of ours covers it).
     * Shows what was heard if there is nowhere to type it.
     */
    fun insertWhenReady(text: String, attempt: Int = 0) {
        main.postDelayed({
            when {
                insert(text) -> Unit
                attempt < MAX_ATTEMPTS -> insertWhenReady(text, attempt + 1)
                else -> toast("Heard: $text (no text box on screen)")
            }
        }, RETRY_DELAY_MS)
    }

    private fun insert(text: String): Boolean {
        val root = rootInActiveWindow?.takeIf { it.packageName != packageName } ?: return false
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: firstEditable(root)
            ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (!target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
        // Submit, as if Enter/Search were pressed on the keyboard.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            main.postDelayed({
                target.refresh()
                target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }, SUBMIT_DELAY_MS)
        }
        return true
    }

    private fun firstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let(::firstEditable)
            if (found != null) return found
        }
        return null
    }

    private val main = Handler(Looper.getMainLooper())

    companion object {
        private const val MAX_ATTEMPTS = 8
        private const val RETRY_DELAY_MS = 250L
        private const val SUBMIT_DELAY_MS = 150L

        // Android TV remotes send SEARCH for the mic button; some send VOICE_ASSIST.
        private val MIC_KEYS = setOf(KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_VOICE_ASSIST)

        @Volatile
        var instance: TextInjectionService? = null
            private set
    }
}
