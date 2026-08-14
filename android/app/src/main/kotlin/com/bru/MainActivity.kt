package com.bru

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {

    private val scope = MainScope()
    private lateinit var status: TextView
    private lateinit var permissionStatus: TextView
    private lateinit var permissionButton: View
    private lateinit var footer: TextView
    private lateinit var scanButton: View
    private lateinit var unpair: View
    private var endpointError: String? = null
    private var wakeStatus: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = text("Bru", 56f, FG, bold = true, center = true).apply {
            letterSpacing = 0.3f
        }
        val tagline = text("Bidirectional Remote Uplink", 13f, MUTED, center = true).apply {
            letterSpacing = 0.08f
        }
        status = text("", 18f, FG, center = true).apply { setTextIsSelectable(true) }
        permissionStatus = text("", 13f, MUTED, center = true)
        permissionButton = button("Grant permissions", filled = false, small = true) {
            openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }
        scanButton = button("Pair", filled = true) { scan() }
        unpair = button("Unpair", filled = false) { confirmUnpair() }
        footer = text("", 12f, MUTED, center = true)
        val licenses = text("Open source licenses", 12f, MUTED, center = true).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { openUrl("https://bru.works/licenses") }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(40), dp(28), dp(28))
            addView(title, lp(4))
            addView(tagline, lp(48))
            addView(status, lp(24))
            addView(scanButton, lp(24, wrap = true))
            addView(unpair, lp(24, wrap = true))
            addView(permissionStatus, lp(12))
            addView(permissionButton, lp(28, wrap = true))
            addView(footer, lp(12))
            addView(licenses, lp(wrap = true))
        }
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime(),
            )
            v.updatePadding(top = dp(40) + bars.top, bottom = dp(28) + bars.bottom)
            insets
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            addView(content)
        })

        requestStartupPermissions()
        consumePairLink(intent)

        val app = applicationContext
        scope.launch {
            try {
                IrohNet.myId(app)
            } catch (e: Throwable) {
                Log.e(TAG, "iroh endpoint failed", e)
                endpointError = "${e.javaClass.simpleName}: ${e.message}"
                render()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startServiceIfPaired()
        render()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render() {
        val peer = WakeNotifier.pairedPeer(this)
        val paired = peer != null

        status.text = when {
            endpointError != null -> "iroh endpoint failed"
            paired -> "Paired with\n$peer"
            else -> "Not paired"
        }
        val granted = missingPermissions().isEmpty()
        permissionStatus.text = if (granted) "Permissions: granted" else "Permissions: NOT granted"
        permissionButton.visibility = if (granted) View.GONE else View.VISIBLE

        scanButton.visibility = if (paired) View.GONE else View.VISIBLE
        unpair.visibility = if (paired) View.VISIBLE else View.GONE
        footer.text = endpointError ?: wakeStatus ?: if (paired) {
            ""
        } else {
            "Open https://bru.works on your computer, then tap Pair to scan the QR code."
        }
    }

    private fun scan() = startActivityForResult(Intent(this, QrScanActivity::class.java), REQ_SCAN)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQ_SCAN || resultCode != RESULT_OK) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        data?.getStringExtra(QrScanActivity.EXTRA_RESULT)?.let { commitPairing(it) }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumePairLink(intent)
    }

    private fun consumePairLink(intent: Intent?) {
        val link = intent?.getStringExtra(EXTRA_PAIR_LINK) ?: return
        intent.removeExtra(EXTRA_PAIR_LINK)
        commitPairing(link)
    }

    private fun commitPairing(link: String) {
        val params = Pairing.apply(this, link)
        if (params == null) {
            Toast.makeText(this, "Not a bru pairing link", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Paired with ${params.label}", Toast.LENGTH_LONG).show()
        requestBatteryExemption()
        sayHello()
    }

    private fun startServiceIfPaired() {
        if (IdentityStore(this).peerId == null) return
        BruService.start(this)
    }

    private fun missingPermissions(): List<String> {
        val wanted = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        return wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }

    private fun requestStartupPermissions() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQ_PERMS)
    }

    private fun openSettings(action: String) =
        startActivity(Intent(action, Uri.parse("package:$packageName")))

    private fun openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startServiceIfPaired()
        render()
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
    }

    private fun sayHello() {
        wakeStatus = "Reaching the client…"
        render()
        scope.launch {
            val delivered = WakeNotifier(this@MainActivity).fire("pairing")
            wakeStatus = if (delivered) {
                null
            } else {
                "Could not reach the client. Make sure it is open, then unpair and pair again."
            }
            render()
        }
    }

    private fun confirmUnpair() {
        AlertDialog.Builder(this)
            .setTitle("Unpair?")
            .setMessage("Forget this pairing? You'll need to pair again to reconnect.")
            .setPositiveButton("Unpair") { _, _ ->
                Pairing.clear(this)
                wakeStatus = null
                Toast.makeText(this, "Unpaired", Toast.LENGTH_SHORT).show()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun lp(bottomDp: Int = 0, wrap: Boolean = false) = LinearLayout.LayoutParams(
        if (wrap) LinearLayout.LayoutParams.WRAP_CONTENT else LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(bottomDp) }

    companion object {
        private const val TAG = "bru"
        private const val REQ_PERMS = 1
        private const val REQ_SCAN = 2
        const val EXTRA_PAIR_LINK = "pair_link"
    }
}
