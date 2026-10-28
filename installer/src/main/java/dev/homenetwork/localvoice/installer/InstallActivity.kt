package dev.homenetwork.localvoice.installer

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Installs the bundled Local Voice APK through a PackageInstaller session. Apps that Android
 * installs from a downloaded file get "restricted settings" (no accessibility service) on
 * Android 13+, and Android TV has no way to lift that; session installs don't get it.
 */
class InstallActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var action: Button
    private lateinit var open: Button
    private var installing = false

    private val result = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        ?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    installing = false
                    refresh()
                }
                else -> {
                    installing = false
                    refresh()
                    status.text = "Install failed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}" +
                        "\n\nIf it says the app isn't compatible, this box needs the other download " +
                        "(32-bit vs 64-bit)."
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = body()
        action = button("", ::onAction)
        open = button("Open Local Voice") {
            val pm = packageManager
            (pm.getLeanbackLaunchIntentForPackage(TARGET) ?: pm.getLaunchIntentForPackage(TARGET))
                ?.let(::startActivity)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(48)
            setPadding(pad, dp(32), pad, dp(32))
            addView(label("Local Voice Setup", 34f))
            addView(body().apply {
                text = "Installs Local Voice in a way that lets you turn on its accessibility " +
                    "service. You can uninstall this setup app afterwards."
            })
            addView(status.apply { setPadding(0, dp(24), 0, dp(8)); setTextColor(Color.WHITE) })
            addView(action)
            addView(open)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#101418"))
            addView(column)
        })
        val filter = IntentFilter(ACTION_RESULT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(result, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(result, filter)
    }

    override fun onDestroy() {
        unregisterReceiver(result)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (!installing) refresh()
    }

    private fun refresh() {
        open.visibility = android.view.View.GONE
        action.isEnabled = true
        when {
            Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls() -> {
                status.text = "Step 1: allow Local Voice Setup to install apps. Turn it on in the " +
                    "next screen, then come back here."
                action.text = "Allow installing"
            }
            !isInstalled() -> {
                status.text = "Step 2: install Local Voice."
                action.text = "Install Local Voice"
            }
            needsReinstall() -> {
                status.text = "Local Voice is already installed, but it was installed directly from the " +
                    "download, so Android won't let you turn on its accessibility service. Remove " +
                    "that copy first, then install it again from here."
                action.text = "Remove old copy"
            }
            else -> {
                status.text = "✓ Local Voice is installed. Open it and follow its checklist — in " +
                    "accessibility settings, Local Voice is at the very bottom of the list."
                action.text = "Reinstall / update"
                open.visibility = android.view.View.VISIBLE
                open.requestFocus()
            }
        }
    }

    private fun onAction() {
        when {
            Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls() -> try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } catch (e: ActivityNotFoundException) {
                status.text = "Open Settings > Apps > Security & restrictions > Unknown sources and " +
                    "turn on Local Voice Setup, then come back here."
            }
            isInstalled() && needsReinstall() ->
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$TARGET")))
            else -> install()
        }
    }

    private fun install() {
        installing = true
        action.isEnabled = false
        status.text = "Installing… (about half a minute)"
        Thread {
            try {
                val installer = packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    .apply { setAppPackageName(TARGET) }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    assets.open(BUNDLED_APK).use { input ->
                        session.openWrite("local-voice.apk", 0, -1).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val callback = PendingIntent.getBroadcast(
                        this, id, Intent(ACTION_RESULT).setPackage(packageName), flags)
                    session.commit(callback.intentSender)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    installing = false
                    refresh()
                    status.text = "Install failed: ${e.message}"
                }
            }
        }.start()
    }

    private fun isInstalled() = try {
        packageManager.getPackageInfo(TARGET, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Restricted settings exist from Android 13; anything we didn't install may carry them. */
    private fun needsReinstall(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return packageManager.getInstallSourceInfo(TARGET).installingPackageName != packageName
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun label(text: String, sizeSp: Float) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(Color.WHITE)
    }

    private fun body() = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(Color.LTGRAY)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TARGET = "dev.homenetwork.localvoice"
        private const val BUNDLED_APK = "local-voice.apk"
        private const val ACTION_RESULT = "dev.homenetwork.localvoice.installer.RESULT"
    }
}
