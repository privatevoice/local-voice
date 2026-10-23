package dev.homenetwork.localvoice

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Opened by the remote's mic button (android.speech.action.WEB_SEARCH). Shows nothing:
 * hands off to TextInjectionService and closes at once, so the app underneath keeps its
 * focused text field. The remote's mic light is the listening indicator.
 */
class VoiceSearchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = TextInjectionService.instance
        if (service != null) {
            service.listenAndType()
        } else {
            Toast.makeText(this, "Turn on Local Voice in Settings > Accessibility", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
