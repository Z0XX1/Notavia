package com.example.notavia.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotaviaDatabaseMigrationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var migratedDatabase: NotaviaDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        migratedDatabase?.close()
        migratedDatabase = null
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrateFromVersionOneToSixPreservesNotesAndAddsDefaults() {
        createVersionOneDatabase()

        val db = openMigratedDatabase()

        assertEquals("Legacy note", db.readString("title", "id = 1"))
        assertEquals(NoteCategories.DEFAULT, db.readString("category", "id = 1"))
        assertEquals(NotePriority.NONE.storageValue, db.readString("priority", "id = 1"))
        assertTrue(db.readLong("deadlineAt", "id = 1") == null)
        assertEquals(NoteType.NOTE.storageValue, db.readString("type", "id = 1"))
        assertEquals(1L, db.readLong("isPinned", "id = 1"))
    }

    @Test
    fun migrateFromVersionFiveToSixConvertsCategoryValuesAndKeepsRoomDefaults() {
        createVersionFiveDatabase()

        val db = openMigratedDatabase()

        assertEquals(NoteCategories.DEFAULT, db.readString("category", "id = 1"))
        assertEquals(NoteCategories.PERSONAL, db.readString("category", "id = 2"))
        assertEquals(NoteCategories.STUDY, db.readString("category", "id = 3"))
        assertEquals(NoteCategories.WORK, db.readString("category", "id = 4"))
        assertEquals(NoteCategories.IDEAS, db.readString("category", "id = 5"))
        assertEquals("health", db.readString("category", "id = 6"))

        db.execSQL(
            """
            INSERT INTO notes (title, content, createdAt, updatedAt, isPinned)
            VALUES ('Default row', 'Default body', 100, 100, 0)
            """.trimIndent(),
        )
        assertEquals(NoteCategories.DEFAULT, db.readString("category", "title = 'Default row'"))
        assertEquals(NotePriority.NONE.storageValue, db.readString("priority", "title = 'Default row'"))
        assertEquals(NoteType.NOTE.storageValue, db.readString("type", "title = 'Default row'"))
        assertTrue(db.readLong("deadlineAt", "title = 'Default row'") == null)
    }

    private fun createVersionOneDatabase() {
        val db = openLegacyDatabase()
        try {
            db.execSQL(
                """
                CREATE TABLE notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    isPinned INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO notes (id, title, content, createdAt, updatedAt, isPinned)
                VALUES (1, 'Legacy note', 'Legacy content', 10, 20, 1)
                """.trimIndent(),
            )
            db.version = 1
        } finally {
            db.close()
        }
    }

    private fun createVersionFiveDatabase() {
        val db = openLegacyDatabase()
        try {
            db.execSQL(
                """
                CREATE TABLE notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    category TEXT NOT NULL DEFAULT '$OLD_DEFAULT_CATEGORY',
                    priority TEXT NOT NULL DEFAULT '${NotePriority.NONE.storageValue}',
                    deadlineAt INTEGER DEFAULT NULL,
                    type TEXT NOT NULL DEFAULT '${NoteType.NOTE.storageValue}',
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    isPinned INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            insertVersionFiveNote(db, 1, OLD_DEFAULT_CATEGORY)
            insertVersionFiveNote(db, 2, OLD_PERSONAL_CATEGORY)
            insertVersionFiveNote(db, 3, OLD_STUDY_CATEGORY)
            insertVersionFiveNote(db, 4, "Work")
            insertVersionFiveNote(db, 5, "Ideas")
            insertVersionFiveNote(db, 6, "health")
            db.version = 5
        } finally {
            db.close()
        }
    }

    private fun insertVersionFiveNote(db: SQLiteDatabase, id: Long, category: String) {
        db.execSQL(
            """
            INSERT INTO notes (
                id,
                title,
                content,
                category,
                priority,
                deadlineAt,
                type,
                createdAt,
                updatedAt,
                isPinned
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                id,
                "Note $id",
                "Content $id",
                category,
                NotePriority.NONE.storageValue,
                null,
                NoteType.NOTE.storageValue,
                id,
                id,
                0,
            ),
        )
    }

    private fun openLegacyDatabase(): SQLiteDatabase {
        val databaseFile = context.getDatabasePath(TEST_DATABASE)
        databaseFile.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
    }

    private fun openMigratedDatabase(): SupportSQLiteDatabase {
        migratedDatabase = Room.databaseBuilder(
            context,
            NotaviaDatabase::class.java,
            TEST_DATABASE,
        )
            .addMigrations(*NotaviaDatabase.ALL_MIGRATIONS)
            .build()
        return migratedDatabase!!.openHelper.writableDatabase
    }

    private fun SupportSQLiteDatabase.readString(column: String, whereClause: String): String {
        val cursor = query("SELECT $column FROM notes WHERE $whereClause LIMIT 1")
        try {
            assertTrue(cursor.moveToFirst())
            return cursor.getString(0)
        } finally {
            cursor.close()
        }
    }

    private fun SupportSQLiteDatabase.readLong(column: String, whereClause: String): Long? {
        val cursor = query("SELECT $column FROM notes WHERE $whereClause LIMIT 1")
        try {
            assertTrue(cursor.moveToFirst())
            return if (cursor.isNull(0)) null else cursor.getLong(0)
        } finally {
            cursor.close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "notavia-migration-test"
        const val OLD_DEFAULT_CATEGORY = "\u0411\u0435\u0437 \u043a\u0430\u0442\u0435\u0433\u043e\u0440\u0438\u0438"
        const val OLD_PERSONAL_CATEGORY = "\u041b\u0438\u0447\u043d\u043e\u0435"
        const val OLD_STUDY_CATEGORY = "\u0423\u0447\u0451\u0431\u0430"
    }
}
