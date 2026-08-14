package com.bru

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.TextView

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
    typeface = if (bold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
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
    typeface = Typeface.MONOSPACE
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
