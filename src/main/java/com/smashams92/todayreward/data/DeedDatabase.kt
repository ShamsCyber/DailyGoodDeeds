package com.smashams92.todayreward.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GoodDeedEntity::class], version = 1)
abstract class DeedDatabase : RoomDatabase() {

    abstract fun deedDao(): DeedDao

    companion object {
        @Volatile
        private var INSTANCE: DeedDatabase? = null

        fun getDatabase(context: Context): DeedDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DeedDatabase::class.java,
                    "deeds.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
