package works.bru

import android.content.Context
import android.util.Log
import computer.iroh.Connection
import computer.iroh.Endpoint
import computer.iroh.EndpointAddr
import computer.iroh.EndpointId
import computer.iroh.EndpointOptions
import computer.iroh.IrohAndroid
import computer.iroh.RelayConfig
import computer.iroh.RelayMap
import computer.iroh.RelayMode
import computer.iroh.presetMinimal
import computer.iroh.presetN0
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

typealias Body = suspend (OutputStream) -> Unit

private const val MAX_FRAME = 8 * 1024 * 1024
private const val CHUNK = 64 * 1024

internal suspend fun readHeader(read: suspend () -> ByteArray): Pair<ByteArray, ByteArray> {
    val header = ByteArrayOutputStream()
    while (true) {
        val chunk = read()
        val newline = chunk.indexOf('\n'.code.toByte())
        if (newline >= 0) {
            header.write(chunk, 0, newline)
            return header.toByteArray() to chunk.copyOfRange(newline + 1, chunk.size)
        }
        if (chunk.isEmpty()) return header.toByteArray() to ByteArray(0)
        header.write(chunk)
        if (header.size() > MAX_FRAME) throw IOException("frame too large")
    }
}

object IrohNet {
    val ALPN = "bru/1".toByteArray()
    private const val TAG = "bru"

    private val QUIC_PORT: UShort = 7842u
    private const val RELAY_TIMEOUT_MS = 15_000L

    private val initLock = Mutex()
    @Volatile private var endpoint: Endpoint? = null

    private val serveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var serveJob: Job? = null
    @Volatile private var dispatcher: (suspend (String, Body) -> String)? = null

    suspend fun endpoint(context: Context): Endpoint {
        endpoint?.let { return it }
        return initLock.withLock {
            endpoint ?: build(context).also { endpoint = it }
        }
    }

    private suspend fun build(context: Context): Endpoint = withContext(Dispatchers.IO) {
        IrohAndroid.installAndroidContext(context.applicationContext)
        val store = IdentityStore(context)
        val ep = Endpoint.bind(
            EndpointOptions(
                preset = if (store.relayUrl != null) presetMinimal() else presetN0(),
                secretKey = store.secretKey,
                alpns = listOf(ALPN),
                relayMode = store.relayUrl?.let { customRelay(it, store.relayToken) },
            ),
        )
        Log.i(TAG, "iroh endpoint up: ${ep.id()} relay=${store.relayUrl ?: "n0"}")
        ep
    }

    private fun customRelay(url: String, token: String?) = RelayMode.custom(
        RelayMap.empty().apply {
            insert(RelayConfig(url = url, quicPort = QUIC_PORT, authToken = token))
        },
    )

    suspend fun reset(context: Context) {
        val app = context.applicationContext
        val resume = dispatcher
        stopServing()
        withContext(Dispatchers.IO) {
            initLock.withLock {
                endpoint?.close()
                endpoint = null
            }
        }
        resume?.let { startServing(app, it) }
    }

    suspend fun awaitRelay(context: Context): Boolean {
        val ep = endpoint(context)
        return withTimeoutOrNull(RELAY_TIMEOUT_MS) { ep.online() } != null
    }

    fun startServing(context: Context, dispatch: suspend (String, Body) -> String) {
        if (serveJob?.isActive == true) return
        val app = context.applicationContext
        dispatcher = dispatch
        serveJob = serveScope.launch {
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
            }
        }
    }

    fun stopServing() {
        serveJob?.cancel()
        dispatcher = null
    }

    private suspend fun handle(context: Context, conn: Connection, dispatch: suspend (String, Body) -> String) {
        val allowed = IdentityStore(context).peerId
        val remote = conn.remoteId().toString()
        if (allowed != remote) {
            Log.w(TAG, "rejected peer $remote (paired with ${allowed ?: "nobody"})")
            conn.close(1, "unknown peer".toByteArray())
            return
        }
        val biConnection = conn.acceptBi()
        val recv = biConnection.recv()
        val (header, rest) = readHeader { recv.read(CHUNK.toUInt()) }
        val respJson = dispatch(String(header, Charsets.UTF_8)) { out ->
            out.write(rest)
            while (true) {
                val chunk = recv.read(CHUNK.toUInt())
                if (chunk.isEmpty()) break
                out.write(chunk)
            }
        }
        val send = biConnection.send()
        send.writeAll(respJson.toByteArray(Charsets.UTF_8))
        send.finish()
        conn.closed()
    }

    suspend fun dialPeer(context: Context, reqJson: String, body: InputStream? = null): Boolean {
        val store = IdentityStore(context)
        val peerId = store.peerId ?: run {
            Log.w(TAG, "no peer paired — cannot push")
            return false
        }
        var conn: Connection? = null
        return try {
            val ep = endpoint(context)
            val addr = EndpointAddr(EndpointId.fromString(peerId), store.relayUrl, emptyList())
            val connected = ep.connect(addr, ALPN).also { conn = it }
            val bi = connected.openBi()
            val send = bi.send()
            send.writeAll((if (body != null) "$reqJson\n" else reqJson).toByteArray(Charsets.UTF_8))
            body?.let { input ->
                val buf = ByteArray(CHUNK)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    send.writeAll(buf.copyOf(n))
                }
            }
            send.finish()
            bi.recv().readToEnd(MAX_FRAME.toUInt())
            connected.close(0, "ok".toByteArray())
            true
        } catch (e: Exception) {
            conn?.close(1, "aborted".toByteArray())
            Log.w(TAG, "dial peer failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }
}
