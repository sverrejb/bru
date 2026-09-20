package works.bru

import android.app.Activity
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

class ClipboardActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(NotificationManager::class.java).cancel(BruService.CLIP_NOTIF_ID)
        val url = intent.getStringExtra(EXTRA_URL)
        val text = intent.getStringExtra(EXTRA_TEXT)
        when {
            url != null -> startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            text != null -> {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Bru", text))
                Toast.makeText(this, "Copied from the client", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_TEXT = "text"
        const val EXTRA_URL = "url"
    }
}
