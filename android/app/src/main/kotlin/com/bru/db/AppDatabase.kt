package com.bru.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MessageLog::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messages(): MessageLogDao
}
