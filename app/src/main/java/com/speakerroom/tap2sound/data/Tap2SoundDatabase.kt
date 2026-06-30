package com.speakerroom.tap2sound.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Speaker::class], version = 1, exportSchema = false)
abstract class Tap2SoundDatabase : RoomDatabase() {
    abstract fun speakerDao(): SpeakerDao

    companion object {
        @Volatile
        private var INSTANCE: Tap2SoundDatabase? = null

        fun getInstance(context: Context): Tap2SoundDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    Tap2SoundDatabase::class.java,
                    "tap2sound.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
