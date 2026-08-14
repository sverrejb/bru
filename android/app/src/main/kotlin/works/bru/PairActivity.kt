package works.bru

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class PairActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent.data?.toString()
        val params = link?.let { Pairing.parse(it) }
        if (link == null || params == null) {
            Toast.makeText(this, "Invalid pairing link", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Pair with ${params.label}?")
            .setMessage(
                "Pair with this client (${Pairing.shortId(params.peerId)}) and " +
                    "replace any current pairing?\n\n" +
                    "Only confirm if you just opened the pairing page on your own computer.",
            )
            .setPositiveButton("Pair") { _, _ ->
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(MainActivity.EXTRA_PAIR_LINK, link),
                )
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
