package works.bru

import android.database.ContentObserver
import android.os.Handler

class SmsObserver(
    private val handler: Handler,
    private val onChanged: () -> Unit,
) : ContentObserver(handler) {
    private val fire = Runnable { onChanged() }

    override fun onChange(selfChange: Boolean) {
        handler.removeCallbacks(fire)
        handler.postDelayed(fire, DEBOUNCE_MS)
    }

    private companion object {
        const val DEBOUNCE_MS = 750L
    }
}
