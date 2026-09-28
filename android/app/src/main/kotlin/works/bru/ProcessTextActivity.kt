package works.bru

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProcessTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = (
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            )?.toString()
        val image = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (image == null && text.isNullOrEmpty()) return finish()
        val app = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val clip = image?.let { imageForSend(app, it) }
            val message = if (image != null && clip == null) {
                "Could not send that image"
            } else {
                val sent = if (clip != null) {
                    ClientPush(app).sendClipboard("", clip.first, clip.second)
                } else {
                    ClientPush(app).sendClipboard(text.orEmpty())
                }
                if (sent) "Sent to the client" else "Could not reach the client"
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
