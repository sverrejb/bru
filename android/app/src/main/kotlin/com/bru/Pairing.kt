package com.bru

import android.content.Context

object Pairing {
    data class Params(val peerId: String, val peerName: String?) {
        val label: String get() = peerName?.takeIf { it.isNotBlank() } ?: "${peerId.take(12)}…"
    }

    fun parse(input: String): Params? {
        val frag = input.substringAfter("#", input).trim()
        val kv = frag.split("&")
            .filter { it.contains("=") }
            .associate { it.substringBefore("=") to it.substringAfter("=") }
        val id = kv["d"]?.trim() ?: return null
        if (!ID_RE.matches(id)) return null
        return Params(id, kv["n"]?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun apply(context: Context, input: String): Params? {
        val p = parse(input) ?: return null
        val store = IdentityStore(context)
        store.peerId = p.peerId
        store.peerName = p.peerName
        BruService.start(context)
        return p
    }

    fun clear(context: Context) {
        val store = IdentityStore(context)
        store.peerId = null
        store.peerName = null
        BruService.stop(context)
    }

    private val ID_RE = Regex("^[0-9a-fA-F]{64}$")
}
