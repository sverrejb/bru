package works.bru

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (IdentityStore(context).peerId == null) {
            Log.i("bru", "boot: not paired — not starting")
            return
        }
        Log.i("bru", "boot: starting service")
        BruService.start(context)
    }
}
