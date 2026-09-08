package works.bru

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

class IdentityStore(context: Context) {
    private val prefs = context.getSharedPreferences("bru", Context.MODE_PRIVATE)

    val secretKey: ByteArray by lazy {
        prefs.getString(KEY_SECRET, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: generate().also {
                prefs.edit().putString(KEY_SECRET, Base64.encodeToString(it, Base64.NO_WRAP)).apply()
            }
    }

    var peerId: String?
        get() = prefs.getString(KEY_PEER, null)
        set(value) {
            prefs.edit().putString(KEY_PEER, value).apply()
        }

    var peerName: String?
        get() = prefs.getString(KEY_PEER_NAME, null)
        set(value) {
            prefs.edit().putString(KEY_PEER_NAME, value).apply()
        }

    var relayUrl: String?
        get() = prefs.getString(KEY_RELAY_URL, null)
        set(value) {
            prefs.edit().putString(KEY_RELAY_URL, value).apply()
        }

    var relayToken: String?
        get() = prefs.getString(KEY_RELAY_TOKEN, null)
        set(value) {
            prefs.edit().putString(KEY_RELAY_TOKEN, value).apply()
        }

    private fun generate(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    private companion object {
        const val KEY_SECRET = "iroh_secret_key"
        const val KEY_PEER = "peer_id"
        const val KEY_PEER_NAME = "peer_name"
        const val KEY_RELAY_URL = "relay_url"
        const val KEY_RELAY_TOKEN = "relay_token"
    }
}
