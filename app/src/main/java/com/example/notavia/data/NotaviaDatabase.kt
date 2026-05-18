package com.example.notavia.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Note::class], version = 1, exportSchema = false)
abstract class NotaviaDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NotaviaDatabase? = null

        fun getDatabase(context: Context): NotaviaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotaviaDatabase::class.java,
                    "notavia_database",
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
