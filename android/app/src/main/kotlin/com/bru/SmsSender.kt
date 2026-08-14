package com.bru

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import java.util.concurrent.ConcurrentHashMap

class SmsSender(private val context: Context) {

    private class Tracker(val total: Int) {
        var done = 0
        var failed = false
    }

    private val trackers = ConcurrentHashMap<Long, Tracker>()

    fun send(seq: Long, to: String, body: String) {
        val sms = manager()
        val parts = sms.divideMessage(body)
        trackers[seq] = Tracker(parts.size.coerceAtLeast(1))
        val sent = PendingIntent.getBroadcast(
            context,
            seq.toInt(),
            Intent(ACTION_SENT).setPackage(context.packageName).putExtra(EXTRA_SEQ, seq),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (parts.size <= 1) {
                sms.sendTextMessage(to, null, body, sent, null)
            } else {
                sms.sendMultipartTextMessage(to, null, parts, ArrayList(parts.map { sent }), null)
            }
        } catch (e: Exception) {
            trackers.remove(seq)
            throw e
        }
    }

    fun messageFailed(seq: Long, ok: Boolean): Boolean {
        val tracker = trackers[seq] ?: return false
        tracker.done++
        if (!ok) tracker.failed = true
        if (tracker.done < tracker.total) return false
        trackers.remove(seq)
        return tracker.failed
    }

    private fun manager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    companion object {
        const val ACTION_SENT = "com.bru.SMS_SENT"
        const val EXTRA_SEQ = "seq"
    }
}
