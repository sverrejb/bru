package com.bru

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
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
    private lateinit var footer: TextView
    private lateinit var pasteField: EditText
    private lateinit var pasteBar: View
    private lateinit var primaryHolder: LinearLayout
    private var endpointError: String? = null
    private var wakeStatus: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val title = text("bru", 56f, FG, bold = true, center = true).apply {
            letterSpacing = 0.3f
        }
        val tagline = text("Bidirectional Remote Uplink", 13f, MUTED, center = true).apply {
            letterSpacing = 0.08f
        }
        status = text("", 18f, FG, center = true).apply { setTextIsSelectable(true) }
        pasteBar = buildPasteBar()
        primaryHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        footer = text("", 12f, MUTED, center = true)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(40), dp(28), dp(28))
            addView(title, lp(4))
            addView(tagline, lp(48))
            addView(status, lp(24))
            addView(pasteBar, lp(24))
            addView(primaryHolder, lp(28))
            addView(footer)
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

        val app = applicationContext
        scope.launch {
            try {
                IrohNet.myId(app)
                IrohNet.startServing(app) { dispatch(app, it) }
            } catch (e: Throwable) {
                Log.e(TAG, "iroh endpoint failed", e)
                endpointError = "${e.javaClass.simpleName}: ${e.message}"
                render()
            }
        }
    }

    override fun onResume() {
        super.onResume()
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
        pasteBar.visibility = if (paired) View.GONE else View.VISIBLE
        primaryHolder.visibility = if (paired) View.VISIBLE else View.GONE
        primaryHolder.removeAllViews()
        if (paired) {
            primaryHolder.addView(button("Reconnect", filled = true) { sayHello() }, buttonLp(12))
            primaryHolder.addView(button("Unpair", filled = false) { confirmUnpair() }, buttonLp())
        }
        footer.text = endpointError ?: wakeStatus ?: if (paired) {
            ""
        } else {
            "Open bru.works on your computer, then copy the pair link printed under the QR code and paste it above."
        }
    }

    private fun pair() {
        val params = Pairing.apply(this, pasteField.text.toString())
        if (params == null) {
            Toast.makeText(this, "Not a bru pairing link", Toast.LENGTH_LONG).show()
            return
        }
        pasteField.text.clear()
        Toast.makeText(this, "Paired with ${params.label}", Toast.LENGTH_LONG).show()
        sayHello()
    }

    private fun sayHello() {
        wakeStatus = "Reaching the browser…"
        render()
        scope.launch {
            val delivered = WakeNotifier(this@MainActivity).fire("pairing")
            wakeStatus = if (delivered) {
                "The browser knows about this phone."
            } else {
                "Could not reach the browser. Make sure bru.works is open on it, then tap Reconnect."
            }
            render()
        }
    }

    private fun confirmUnpair() {
        AlertDialog.Builder(this)
            .setTitle("Unpair?")
            .setMessage("Forget this browser? You'll need to pair again to reconnect.")
            .setPositiveButton("Unpair") { _, _ ->
                Pairing.clear(this)
                wakeStatus = null
                Toast.makeText(this, "Unpaired", Toast.LENGTH_SHORT).show()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildPasteBar(): View {
        pasteField = EditText(this).apply {
            hint = "paste the pair link"
            setSingleLine()
            inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextColor(FG)
            setHintTextColor(MUTED)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    pair()
                    true
                } else {
                    false
                }
            }
        }
        val pairButton = button("Pair", filled = true) { pair() }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                pasteField,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                pairButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(8) },
            )
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun lp(bottomDp: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(bottomDp) }

    private fun buttonLp(bottomDp: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(bottomDp) }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false, center: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
            if (center) gravity = Gravity.CENTER_HORIZONTAL
        }

    private fun button(label: String, filled: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        typeface = Typeface.MONOSPACE
        textSize = 15f
        letterSpacing = 0.1f
        stateListAnimator = null
        setPadding(dp(20), dp(12), dp(20), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            if (filled) setColor(FG) else {
                setColor(BG)
                setStroke(dp(2), FG)
            }
        }
        setTextColor(if (filled) BG else FG)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        setOnClickListener { onClick() }
    }

    private companion object {
        const val TAG = "bru"

        val BG = 0xFFF7F5F0.toInt()
        val FG = 0xFF3D3A35.toInt()
        val MUTED = 0xFF837D72.toInt()
    }
}
