package com.qigai.neon.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        ConnectorEntity::class,
        HandsTraceEntity::class,
        ScheduledTaskEntity::class,
        TaskReceiptEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun connectorDao(): ConnectorDao
    abstract fun handsTraceDao(): HandsTraceDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun taskReceiptDao(): TaskReceiptDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "neon_agent.db",
            ).fallbackToDestructiveMigration().build()
    }
}
