package works.bru.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MessageLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<MessageLog>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOne(row: MessageLog): Long

    @Query("SELECT MAX(provider_id) FROM message_log")
    suspend fun maxProviderId(): Long?

    @Query("SELECT seq FROM message_log WHERE status != 'pending' ORDER BY seq DESC LIMIT 1")
    suspend fun headCursor(): Long?

    @Query(
        "SELECT * FROM message_log WHERE seq > :since AND status != 'pending' " +
            "ORDER BY seq ASC LIMIT :limit",
    )
    suspend fun since(since: Long, limit: Int): List<MessageLog>

    @Query("SELECT * FROM message_log WHERE seq = :seq")
    suspend fun bySeq(seq: Long): MessageLog?

    @Query("SELECT * FROM message_log WHERE client_id = :clientId LIMIT 1")
    suspend fun byClientId(clientId: String): MessageLog?

    @Query("SELECT * FROM message_log WHERE status = 'pending' AND date >= :minDate ORDER BY seq ASC")
    suspend fun pendingSince(minDate: Long): List<MessageLog>

    @Query("DELETE FROM message_log WHERE seq = :seq")
    suspend fun deleteBySeq(seq: Long)
}
