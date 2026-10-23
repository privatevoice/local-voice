package dev.homenetwork.localvoice

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /**
     * Types [text] into the app on screen once our popup has closed and that app's window is
     * active again (while the popup covers it, its window is hidden from accessibility).
     * Shows what was heard if there is nowhere to type it.
     */
    fun insertWhenReady(text: String, attempt: Int = 0) {
        main.postDelayed({
            when {
                insert(text) -> Unit
                attempt < MAX_ATTEMPTS -> insertWhenReady(text, attempt + 1)
                else -> Toast.makeText(this, "Heard: $text (no text box on screen)", Toast.LENGTH_LONG).show()
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
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
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

        @Volatile
        var instance: TextInjectionService? = null
            private set
    }
}
