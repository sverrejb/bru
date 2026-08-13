package com.bru

import android.content.Context
import android.util.Log
import com.bru.db.MessageLog
import org.json.JSONArray
import org.json.JSONObject

const val AGENT_VERSION = "1.0.0"

suspend fun dispatch(
    context: Context,
    repo: SmsRepository,
    sender: SmsSender,
    reqJson: String,
): String {
    val req = try {
        JSONObject(reqJson)
    } catch (e: Exception) {
        return errorJson("bad request")
    }
    return when (req.optString("op")) {
        "health" -> JSONObject()
            .put("status", "ok")
            .put("deviceName", WakeNotifier.deviceName(context))
            .put("agentVersion", AGENT_VERSION)
            .put("headCursor", repo.headCursor())
            .toString()

        "messages" -> {
            val since = req.optLong("since", 0L)
            val limit = req.optInt("limit", 500).coerceIn(1, 2000)
            val rows = try {
                repo.messages(since, limit)
            } catch (e: Exception) {
                Log.w("bru", "messages query failed: ${e.javaClass.simpleName}")
                emptyList()
            }
            val arr = JSONArray()
            rows.forEach { arr.put(messageJson(it)) }
            JSONObject()
                .put("messages", arr)
                .put("cursor", rows.lastOrNull()?.seq ?: since)
                .put("hasMore", rows.size == limit)
                .toString()
        }

        "send" -> {
            val to = req.optString("to")
            val body = req.optString("body")
            val clientId = req.optString("clientId")
            if (to.isBlank() || body.isBlank() || clientId.isBlank()) {
                errorJson("to, body and clientId are required")
            } else {
                JSONObject().put("status", send(repo, sender, to, body, clientId)).toString()
            }
        }

        else -> errorJson("unsupported op")
    }
}

private suspend fun send(
    repo: SmsRepository,
    sender: SmsSender,
    to: String,
    body: String,
    clientId: String,
): String {
    val target = PhoneNumbers.normalizeE164(to) ?: to
    val outcome = repo.getOrCreatePending(clientId, target, body)
    if (!outcome.isNew) return outcome.status
    return try {
        sender.send(outcome.seq, target, body)
        "pending"
    } catch (e: Exception) {
        Log.w("bru", "send dispatch failed: ${e.javaClass.simpleName}")
        repo.markFailed(outcome.seq)
        "failed"
    }
}

private fun messageJson(row: MessageLog): JSONObject = JSONObject()
    .put("seq", row.seq)
    .put("threadId", row.threadId)
    .put("address", row.address)
    .put("displayName", row.displayName ?: JSONObject.NULL)
    .put("body", row.body)
    .put("date", row.date)
    .put("direction", row.direction)
    .put("status", row.status)
    .put("clientId", row.clientId ?: JSONObject.NULL)

private fun errorJson(message: String): String =
    JSONObject().put("error", message).toString()
