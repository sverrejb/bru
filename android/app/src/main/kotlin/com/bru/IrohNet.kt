package com.bru

import android.content.Context
import android.util.Log
import computer.iroh.Connection
import computer.iroh.Endpoint
import computer.iroh.EndpointAddr
import computer.iroh.EndpointId
import computer.iroh.EndpointOptions
import computer.iroh.IrohAndroid
import computer.iroh.presetN0
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object IrohNet {
    val ALPN = "bru/1".toByteArray()
    private const val TAG = "bru"

    private val MAX_FRAME: UInt = 4u * 1024u * 1024u

    private val initLock = Mutex()
    @Volatile private var endpoint: Endpoint? = null

    private val serveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var serving = false

    suspend fun endpoint(context: Context): Endpoint {
        endpoint?.let { return it }
        return initLock.withLock {
            endpoint ?: build(context).also { endpoint = it }
        }
    }

    private suspend fun build(context: Context): Endpoint = withContext(Dispatchers.IO) {
        IrohAndroid.installAndroidContext(context.applicationContext)
        val ep = Endpoint.bind(
            EndpointOptions(
                preset = presetN0(),
                secretKey = IdentityStore(context).secretKey,
                alpns = listOf(ALPN),
            ),
        )
        Log.i(TAG, "iroh endpoint up: ${ep.id()}")
        ep
    }

    suspend fun myId(context: Context): String = endpoint(context).id().toString()

    fun startServing(context: Context, dispatch: suspend (String) -> String) {
        if (serving) return
        serving = true
        val app = context.applicationContext
        serveScope.launch {
            try {
                val ep = endpoint(app)
                while (true) {
                    val incoming = ep.acceptNext() ?: break
                    launch {
                        try {
                            handle(app, incoming.accept().connect(), dispatch)
                        } catch (e: Exception) {
                            Log.w(TAG, "serve error: ${e.javaClass.simpleName}: ${e.message}")
                        }
                    }
                }
                Log.w(TAG, "accept loop ended — endpoint closed")
            } catch (e: Throwable) {
                Log.e(TAG, "accept loop failed", e)
            } finally {
                serving = false
            }
        }
    }

    private suspend fun handle(context: Context, conn: Connection, dispatch: suspend (String) -> String) {
        val allowed = IdentityStore(context).peerId
        val remote = conn.remoteId().toString()
        if (allowed != remote) {
            Log.w(TAG, "rejected peer $remote (paired with ${allowed ?: "nobody"})")
            conn.close(1, "unknown peer".toByteArray())
            return
        }
        val bi = conn.acceptBi()
        val reqBytes = bi.recv().readToEnd(MAX_FRAME)
        val respJson = dispatch(String(reqBytes, Charsets.UTF_8))
        val send = bi.send()
        send.writeAll(respJson.toByteArray(Charsets.UTF_8))
        send.finish()
        conn.closed()
    }

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
            bi.recv().readToEnd(MAX_FRAME)
            conn.close(0, "ok".toByteArray())
            true
        } catch (e: Exception) {
            Log.w(TAG, "dial peer failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }
}
