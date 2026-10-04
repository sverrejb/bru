package works.bru

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SendFileActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        val app = applicationContext
        if (uri == null) {
            Toast.makeText(app, "Only files can be sent", Toast.LENGTH_SHORT).show()
            return finish()
        }
        Toast.makeText(app, "Sending to the client…", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            val input = runCatching { app.contentResolver.openInputStream(uri) }.getOrNull()
            val name = displayName(app, uri)
            val mime = app.contentResolver.getType(uri) ?: "application/octet-stream"
            withContext(Dispatchers.Main) { finish() }
            val message = when {
                input == null -> "Could not read that file"
                input.use { ClientPush(app).sendFile(name, mime, it) } -> "Sent to the client"
                else -> "Could not reach the client"
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment ?: "file"
}
