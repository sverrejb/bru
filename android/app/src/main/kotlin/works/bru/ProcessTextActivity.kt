package works.bru

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
        val app = applicationContext
        if (!text.isNullOrEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val ok = WakeNotifier(app).sendClipboard(text)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        app,
                        if (ok) "Sent to the client" else "Could not reach the client",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
        finish()
    }
}
