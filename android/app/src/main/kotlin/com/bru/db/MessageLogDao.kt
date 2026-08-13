package com.bru.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MessageLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<MessageLog>): List<Long>

    @Query("SELECT MAX(provider_id) FROM message_log")
    suspend fun maxProviderId(): Long?

    @Query("SELECT MAX(seq) FROM message_log")
    suspend fun headCursor(): Long?

    @Query("SELECT * FROM message_log WHERE seq > :since ORDER BY seq ASC LIMIT :limit")
    suspend fun since(since: Long, limit: Int): List<MessageLog>
}
