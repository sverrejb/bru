package com.bru

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.delay
import org.json.JSONObject

class WakeNotifier(private val context: Context) {

    suspend fun fire(reason: String = "new_sms"): Boolean {
        val json = JSONObject()
            .put("op", "wake")
            .put("reason", reason)
            .put("name", deviceName(context))
            .toString()
        for (attempt in 1..RETRY_ATTEMPTS) {
            if (IrohNet.dialPeer(context, json)) {
                Log.i(TAG, "wake delivered" + if (attempt > 1) " (attempt $attempt)" else "")
                return true
            }
            if (attempt < RETRY_ATTEMPTS) delay(RETRY_DELAY_MS)
        }
        Log.i(TAG, "wake not delivered after $RETRY_ATTEMPTS attempts (peer offline/unpaired)")
        return false
    }

    suspend fun sendClipboard(text: String): Boolean =
        IrohNet.dialPeer(context, JSONObject().put("op", "clipboard").put("text", text).toString())

    companion object {
        private const val TAG = "bru"
        private const val RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 3000L

        fun deviceName(context: Context): String =
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                ?.takeIf { it.isNotBlank() } ?: Build.MODEL

        fun pairedPeer(context: Context): String? {
            val store = IdentityStore(context)
            val id = store.peerId ?: return null
            return Pairing.peerLabel(id, store.peerName)
        }
    }
}
