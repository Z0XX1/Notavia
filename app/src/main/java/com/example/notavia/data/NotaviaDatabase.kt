package com.example.notavia.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


@Database(entities = [Note::class], version = 6, exportSchema = true)
abstract class NotaviaDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NotaviaDatabase? = null


        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notes ADD COLUMN category TEXT NOT NULL DEFAULT '${NoteCategories.DEFAULT}'",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notes ADD COLUMN priority TEXT NOT NULL DEFAULT '${NotePriority.NONE.storageValue}'",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN deadlineAt INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notes ADD COLUMN type TEXT NOT NULL DEFAULT '${NoteType.NOTE.storageValue}'",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                replaceCategory(db, "\u0411\u0435\u0437 \u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u0438", NoteCategories.DEFAULT)
                replaceCategory(db, "\u0412\u0441\u0435", NoteCategories.ALL)
                replaceCategory(db, "\u041b\u0438\u0447\u043d\u043e\u0435", NoteCategories.PERSONAL)
                replaceCategory(db, "\u0423\u0447\u0435\u0431\u0430", NoteCategories.STUDY)
                replaceCategory(db, "\u0423\u0447\u0451\u0431\u0430", NoteCategories.STUDY)
                replaceCategory(db, "\u0420\u0430\u0431\u043e\u0442\u0430", NoteCategories.WORK)
                replaceCategory(db, "\u0418\u0434\u0435\u0438", NoteCategories.IDEAS)
                replaceCategory(db, "No category", NoteCategories.DEFAULT)
                replaceCategory(db, "All", NoteCategories.ALL)
                replaceCategory(db, "Personal", NoteCategories.PERSONAL)
                replaceCategory(db, "Study", NoteCategories.STUDY)
                replaceCategory(db, "Work", NoteCategories.WORK)
                replaceCategory(db, "Ideas", NoteCategories.IDEAS)
            }
        }


        fun getDatabase(context: Context): NotaviaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotaviaDatabase::class.java,
                    "notavia_database",
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private fun replaceCategory(db: SupportSQLiteDatabase, oldValue: String, newValue: String) {
            db.execSQL(
                "UPDATE notes SET category = REPLACE(category, ?, ?)",
                arrayOf(oldValue, newValue),
            )
        }
    }
}
