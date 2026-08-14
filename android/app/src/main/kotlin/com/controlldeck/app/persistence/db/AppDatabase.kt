package com.controlldeck.app.persistence.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DashboardEntity::class,
        WidgetEntity::class,
        GroupEntity::class,
        PairedDeviceEntity::class,
        AppRegistryEntryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dashboardDao(): DashboardDao
    abstract fun pairedDeviceDao(): PairedDeviceDao
    abstract fun appRegistryDao(): AppRegistryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "controlldeck.db",
            ).build().also { instance = it }
        }
    }
}
