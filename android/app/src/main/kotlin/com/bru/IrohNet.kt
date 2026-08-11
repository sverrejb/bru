package com.bru

import android.content.Context
import android.util.Log
import computer.iroh.Endpoint
import computer.iroh.EndpointAddr
import computer.iroh.EndpointId
import computer.iroh.EndpointOptions
import computer.iroh.IrohAndroid
import computer.iroh.presetN0
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object IrohNet {
    val ALPN = "bru/1".toByteArray()
    private const val TAG = "bru"

    private val MAX_REPLY: UInt = 4u * 1024u * 1024u

    private val initLock = Mutex()
    @Volatile private var endpoint: Endpoint? = null

    suspend fun endpoint(context: Context): Endpoint {
        endpoint?.let { return it }
        return initLock.withLock {
            endpoint ?: build(context).also { endpoint = it }
        }
    }

    private suspend fun build(context: Context): Endpoint {
        IrohAndroid.installAndroidContext(context.applicationContext)
        val ep = Endpoint.bind(
            EndpointOptions(
                preset = presetN0(),
                secretKey = IdentityStore(context).secretKey,
                alpns = listOf(ALPN),
            ),
        )
        Log.i(TAG, "iroh endpoint up: ${ep.id()}")
        return ep
    }

    suspend fun myId(context: Context): String = endpoint(context).id().toString()

    suspend fun dialPeer(context: Context, reqJson: String): Boolean {
        val peerId = IdentityStore(context).peerId ?: run {
            Log.w(TAG, "no peer paired — cannot push")
            return false
        }
        return try {
            val ep = endpoint(context)
            val addr = EndpointAddr(EndpointId.fromString(peerId), null, emptyList())
            val conn = ep.connect(addr, ALPN)
            val bi = conn.openBi()
            val send = bi.send()
            send.writeAll(reqJson.toByteArray(Charsets.UTF_8))
            send.finish()
            bi.recv().readToEnd(MAX_REPLY)
            conn.close(0, "ok".toByteArray())
            true
        } catch (e: Exception) {
            Log.w(TAG, "dial peer failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }
}
