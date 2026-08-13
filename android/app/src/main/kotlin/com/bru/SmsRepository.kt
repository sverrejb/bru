package com.bru

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.room.withTransaction
import com.bru.db.AppDatabase
import com.bru.db.MessageLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val PER_THREAD_LIMIT = 100
internal const val RECONCILE_WINDOW_MS = 24 * 60 * 60 * 1000L

data class SendOutcome(val seq: Long, val status: String, val isNew: Boolean)

internal fun tempThreadId(address: String): Long =
    Int.MIN_VALUE.toLong() + address.hashCode()

internal fun takeThreadSlot(seen: MutableMap<Long, Int>, threadId: Long): Boolean {
    val n = seen.getOrDefault(threadId, 0)
    if (n >= PER_THREAD_LIMIT) return false
    seen[threadId] = n + 1
    return true
}

class SmsRepository(
    private val db: AppDatabase,
    private val resolver: ContentResolver,
) {
    private val dao = db.messages()
    private val mutex = Mutex()

    suspend fun ingestNew(): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val provider = query(dao.maxProviderId() ?: 0L)
            if (provider.isEmpty()) return@withLock 0
            db.withTransaction {
                val pending = dao
                    .pendingSince(System.currentTimeMillis() - RECONCILE_WINDOW_MS)
                    .toMutableList()
                val rows = provider.map { row ->
                    val match = if (row.direction == "out") {
                        pending.lastOrNull { it.address == row.address && it.body == row.body }
                    } else {
                        null
                    }
                    if (match == null) {
                        row
                    } else {
                        pending.remove(match)
                        dao.deleteBySeq(match.seq)
                        row.copy(clientId = match.clientId)
                    }
                }
                dao.insertAll(rows).count { it != -1L }
            }
        }
    }

    suspend fun getOrCreatePending(clientId: String, to: String, body: String): SendOutcome =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                dao.byClientId(clientId)?.let {
                    return@withLock SendOutcome(it.seq, it.status, false)
                }
                val seq = dao.insertOne(
                    MessageLog(
                        clientId = clientId,
                        threadId = tempThreadId(to),
                        address = to,
                        body = body,
                        date = System.currentTimeMillis(),
                        direction = "out",
                        status = "pending",
                    ),
                )
                SendOutcome(seq, "pending", true)
            }
        }

    suspend fun markFailed(seq: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            db.withTransaction {
                val row = dao.bySeq(seq) ?: return@withTransaction
                dao.deleteBySeq(seq)
                dao.insertOne(row.copy(seq = 0, status = "failed"))
            }
        }
    }

    suspend fun headCursor(): Long = dao.headCursor() ?: 0L

    suspend fun messages(since: Long, limit: Int): List<MessageLog> = dao.since(since, limit)

    private fun query(sinceId: Long): List<MessageLog> {
        val capped = sinceId == 0L
        val perThread = HashMap<Long, Int>()
        val nameCache = HashMap<String, String?>()
        val out = ArrayList<MessageLog>()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
        )
        val selection = "${Telephony.Sms._ID} > ? AND ${Telephony.Sms.TYPE} IN " +
            "(${Telephony.Sms.MESSAGE_TYPE_INBOX}, ${Telephony.Sms.MESSAGE_TYPE_SENT})"
        val args = arrayOf(sinceId.toString())
        val sort = "${Telephony.Sms._ID} DESC"

        resolver.query(Telephony.Sms.CONTENT_URI, projection, selection, args, sort)?.use { c ->
            val iId = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val iThread = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val iType = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)

            while (c.moveToNext()) {
                val threadId = c.getLong(iThread)
                if (capped && !takeThreadSlot(perThread, threadId)) continue
                val rawAddress = c.getString(iAddr)
                val direction = PhoneNumbers.directionFromType(c.getInt(iType))
                out += MessageLog(
                    providerId = c.getLong(iId),
                    threadId = threadId,
                    address = PhoneNumbers.normalizeE164(rawAddress) ?: "",
                    displayName = contactName(rawAddress, nameCache),
                    body = c.getString(iBody) ?: "",
                    date = c.getLong(iDate),
                    direction = direction,
                    status = if (direction == "in") "received" else "sent",
                )
            }
        }
        out.reverse()
        return out
    }

    private fun contactName(address: String?, cache: MutableMap<String, String?>): String? {
        if (address.isNullOrBlank() || !address.any { it.isDigit() }) return null
        if (cache.containsKey(address)) return cache[address]
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address),
        )
        val name = resolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
        cache[address] = name
        return name
    }
}
