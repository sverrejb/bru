package works.bru

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

val MONO: Typeface = Typeface.create("serif-monospace", Typeface.NORMAL)
val MONO_BOLD: Typeface = Typeface.create("serif-monospace", Typeface.BOLD)

val BG = 0xFFF7F5F0.toInt()
val FG = 0xFF3D3A35.toInt()
val MUTED = 0xFF837D72.toInt()

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.text(
    s: String,
    size: Float,
    color: Int,
    bold: Boolean = false,
    center: Boolean = false,
) = TextView(this).apply {
    text = s
    textSize = size
    setTextColor(color)
    typeface = if (bold) MONO_BOLD else MONO
    if (center) gravity = Gravity.CENTER_HORIZONTAL
}

fun Context.button(
    label: String,
    filled: Boolean,
    small: Boolean = false,
    onClick: () -> Unit,
) = Button(this).apply {
    text = label
    isAllCaps = false
    typeface = MONO
    textSize = if (small) 10.5f else 15f
    letterSpacing = 0.1f
    stateListAnimator = null
    if (small) setPadding(dp(14), dp(8), dp(14), dp(8)) else setPadding(dp(20), dp(12), dp(20), dp(12))
    background = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        if (filled) {
            setColor(FG)
        } else {
            setColor(BG)
            setStroke(dp(2), FG)
        }
    }
    setTextColor(if (filled) BG else FG)
    setOnClickListener { onClick() }
}

fun Context.input(hint: String, value: String?) = EditText(this).apply {
    setText(value)
    this.hint = hint
    textSize = 14f
    typeface = MONO
    setTextColor(FG)
    setHintTextColor(MUTED)
    setSingleLine()
    setPadding(dp(12), dp(10), dp(12), dp(10))
    background = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(BG)
        setStroke(dp(2), MUTED)
    }
}

fun Context.dialog(title: String, body: View, positiveLabel: String, onPositive: () -> Unit) {
    val heading = text(title, 20f, FG, bold = true).apply {
        setPadding(dp(24), dp(22), dp(24), dp(6))
    }
    body.setPadding(dp(24), dp(6), dp(24), dp(10))
    val d = AlertDialog.Builder(this)
        .setCustomTitle(heading)
        .setView(body)
        .setPositiveButton(positiveLabel) { _, _ -> onPositive() }
        .setNegativeButton("Cancel", null)
        .create()
    d.window?.setBackgroundDrawable(
        InsetDrawable(
            GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(BG)
            },
            dp(24),
        ),
    )
    d.show()
    for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)) {
        d.getButton(which).apply {
            typeface = MONO
            isAllCaps = false
            setTextColor(FG)
        }
    }
}
