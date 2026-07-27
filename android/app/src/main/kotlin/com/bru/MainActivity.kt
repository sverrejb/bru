package com.bru

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import computer.iroh.Endpoint
import computer.iroh.EndpointOptions
import computer.iroh.IrohAndroid
import computer.iroh.presetN0
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val ALPN = "bru/1".toByteArray()

private const val ONLINE_TIMEOUT_MS = 15_000L

class MainActivity : Activity() {
    private val scope = MainScope()
    private var endpoint: Endpoint? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this).apply {
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            text = "binding iroh endpoint…"
        }
        setContentView(view)

        scope.launch {
            try {
                IrohAndroid.installAndroidContext(applicationContext)
                val ep = Endpoint.bind(EndpointOptions(preset = presetN0(), alpns = listOf(ALPN)))
                endpoint = ep
                val id = ep.id().toString()
                Log.i("bru", "iroh endpoint bound: $id")
                view.text = "iroh endpoint bound\n\n$id\n\nreaching relay…"

                val online = withTimeoutOrNull(ONLINE_TIMEOUT_MS) { ep.online() } != null
                Log.i("bru", if (online) "iroh online" else "iroh bound but offline")
                view.text = if (online) {
                    "iroh connection is up\n\n$id"
                } else {
                    "iroh endpoint bound, but no relay after ${ONLINE_TIMEOUT_MS / 1000}s\n\n$id"
                }
            } catch (e: Exception) {
                Log.e("bru", "iroh bind failed", e)
                view.text = "iroh bind failed\n\n${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        endpoint?.close()
        super.onDestroy()
    }
}
