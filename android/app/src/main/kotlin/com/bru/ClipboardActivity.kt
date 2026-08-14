package com.bru

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class ClipboardActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(EXTRA_TEXT)?.let {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Bru", it))
            Toast.makeText(this, "Copied from the client", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        const val EXTRA_TEXT = "text"
    }
}
