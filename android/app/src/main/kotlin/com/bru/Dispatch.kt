package com.bru

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

const val AGENT_VERSION = "1.0.0"

suspend fun dispatch(context: Context, reqJson: String): String {
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
            .put("headCursor", 0L)
            .toString()

        "messages" -> JSONObject()
            .put("messages", JSONArray())
            .put("cursor", req.optLong("since", 0L))
            .put("hasMore", false)
            .toString()

        else -> errorJson("unsupported op")
    }
}

private fun errorJson(message: String): String =
    JSONObject().put("error", message).toString()
