package com.bru.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_log",
    indices = [
        Index(value = ["provider_id"], unique = true),
        Index(value = ["client_id"], unique = true),
    ],
)
data class MessageLog(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    @ColumnInfo(name = "provider_id") val providerId: Long? = null,
    @ColumnInfo(name = "client_id") val clientId: String? = null,
    @ColumnInfo(name = "thread_id") val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val direction: String,
    val status: String,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
)
