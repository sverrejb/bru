package works.bru.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MessageLog::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messages(): MessageLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_message_log_provider_id")
                db.execSQL(
                    "DELETE FROM message_log WHERE seq NOT IN " +
                        "(SELECT MIN(seq) FROM message_log " +
                        "GROUP BY address, date, body, direction)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_message_log_address_date_body_direction " +
                        "ON message_log (address, date, body, direction)",
                )
            }
        }
    }
}
