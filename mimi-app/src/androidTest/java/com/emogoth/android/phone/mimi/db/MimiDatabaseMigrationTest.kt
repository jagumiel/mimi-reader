package com.emogoth.android.phone.mimi.db

import androidx.room.testing.MigrationTestHelper
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MimiDatabaseMigrationTest {
    companion object {
        private const val TEST_DATABASE = "mimi-migration-test"
    }

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MimiDatabase::class.java,
        emptyList<AutoMigrationSpec>(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Before
    fun deleteStaleDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun deleteTestDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrate22To23PreservesUserDataAndUpdatesCatalogSchema() {
        migrationHelper.createDatabase(TEST_DATABASE, 22).apply {
            insertVersion22UserData(this)
            close()
        }

        val database = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            23,
            true,
            MimiDatabase.MIGRATION_22_23
        )

        assertSingleTextValue(
            database,
            "SELECT post_text FROM History WHERE thread_id = 12345",
            "saved thread"
        )
        assertSingleLongValue(
            database,
            "SELECT favorite FROM Boards WHERE board_path = 'g'",
            1L
        )
        assertSingleTextValue(
            database,
            "SELECT filter FROM post_filters WHERE name = 'hide spam'",
            "spam"
        )
        assertSingleLongValue(
            database,
            "SELECT thread_id FROM hidden_threads WHERE board_name = 'g'",
            54321L
        )

        // Catalog entries are disposable API cache data. Version 23 intentionally clears
        // them before changing uniqueness from post_id to (board_name, post_id).
        assertSingleLongValue(database, "SELECT COUNT(*) FROM catalog_posts", 0L)
        assertCatalogIndices(database)

        insertVersion23CatalogPost(database, id = 1, boardName = "g", postId = 999)
        insertVersion23CatalogPost(database, id = 2, boardName = "wg", postId = 999)
        assertSingleLongValue(
            database,
            "SELECT COUNT(*) FROM catalog_posts WHERE post_id = 999",
            2L
        )

        database.close()
    }

    @Test
    fun migrate21To23PreservesLegacyUserDataAndNormalizesNulls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Version21DatabaseFixture.create(context, TEST_DATABASE)

        val database = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            23,
            true,
            MimiDatabase.MIGRATION_21_22,
            MimiDatabase.MIGRATION_22_23
        )

        assertSingleTextValue(
            database,
            "SELECT post_text FROM History WHERE thread_id = 12345",
            "saved thread"
        )
        assertSingleTextValue(
            database,
            "SELECT user_name FROM History WHERE thread_id = 12345",
            "Anonymous"
        )
        assertSingleLongValue(
            database,
            "SELECT last_access FROM History WHERE thread_id = 12345",
            0L
        )
        assertSingleTextValue(
            database,
            "SELECT comment FROM thread_posts WHERE post_id = 12346",
            "saved reply"
        )
        assertSingleTextValue(
            database,
            "SELECT board_name FROM thread_posts WHERE post_id = 12346",
            "Unknown"
        )
        assertSingleLongValue(database, "SELECT favorite FROM Boards WHERE board_path = 'g'", 1L)
        assertSingleLongValue(database, "SELECT access_count FROM Boards WHERE board_path = 'g'", 0L)
        assertSingleTextValue(
            database,
            "SELECT filter FROM post_filters WHERE name = 'hide spam'",
            "spam"
        )
        assertSingleLongValue(
            database,
            "SELECT highlight FROM post_filters WHERE name = 'hide spam'",
            0L
        )
        assertSingleLongValue(
            database,
            "SELECT thread_id FROM hidden_threads WHERE board_name = 'g'",
            54321L
        )
        assertSingleLongValue(database, "SELECT post_time FROM posts WHERE post_id = 12346", 0L)
        assertSingleLongValue(database, "SELECT used_count FROM post_options WHERE option = 'sage'", 0L)
        assertSingleLongValue(database, "SELECT https FROM archives WHERE board = 'g'", 1L)
        assertSingleLongValue(
            database,
            "SELECT post_id FROM archived_posts WHERE board_name = 'g'",
            12346L
        )

        assertSingleLongValue(database, "SELECT COUNT(*) FROM catalog_posts", 0L)
        assertSingleLongValue(database, "SELECT COUNT(*) FROM refresh_queue", 0L)
        assertCatalogIndices(database)

        database.close()
    }

    private fun insertVersion22UserData(database: SupportSQLiteDatabase) {
        database.execSQL(
            """INSERT INTO History
                (id, order_id, thread_id, board_path, user_name, last_access, post_tim,
                 post_text, watched, thread_size, post_replies, thread_removed,
                 last_read_position, unread_count)
                VALUES (1, 7, 12345, 'g', 'Anonymous', 1700000000, '1234567890',
                        'saved thread', 1, 42, '12346', 0, 8, 3)"""
        )
        database.execSQL(
            """INSERT INTO Boards
                (id, board_name, board_path, access_count, post_count, board_category,
                 last_accessed, favorite, nsfw, per_page, pages, visible, order_index,
                 max_file_size)
                VALUES (1, 'Technology', 'g', 5, 100, 0, 1700000000, 1, 0, 15, 10, 1, 2,
                        4194304)"""
        )
        database.execSQL(
            """INSERT INTO post_filters (id, name, filter, board, highlight)
                VALUES (1, 'hide spam', 'spam', 'g', 0)"""
        )
        database.execSQL(
            """INSERT INTO hidden_threads (id, board_name, thread_id, time, sticky)
                VALUES (1, 'g', 54321, 1700000000, 0)"""
        )
        database.execSQL(
            """INSERT INTO catalog_posts
                (id, post_id, closed, sticky, file_width, file_height, thumb_width,
                 thumb_height, epoch, file_size, resto, bump_limit, image_limit,
                 reply_count, image_count, omitted_posts, omitted_image, spoiler,
                 custom_spoiler)
                VALUES (1, 999, 0, 0, 640, 480, 250, 188, 1700000000, 1024, 0, 0, 0,
                        10, 1, 0, 0, 0, 0)"""
        )
    }

    private fun insertVersion23CatalogPost(
        database: SupportSQLiteDatabase,
        id: Long,
        boardName: String,
        postId: Long
    ) {
        database.execSQL(
            """INSERT INTO catalog_posts
                (id, board_name, post_id, closed, sticky, file_width, file_height,
                 thumb_width, thumb_height, epoch, file_size, resto, bump_limit,
                 image_limit, reply_count, image_count, omitted_posts, omitted_image,
                 spoiler, custom_spoiler)
                VALUES (?, ?, ?, 0, 0, 640, 480, 250, 188, 1700000000, 1024, 0, 0, 0,
                        10, 1, 0, 0, 0, 0)""",
            arrayOf<Any>(id, boardName, postId)
        )
    }

    private fun assertCatalogIndices(database: SupportSQLiteDatabase) {
        var oldIndexPresent = false
        var compositeIndexPresent = false
        database.query("PRAGMA index_list('catalog_posts')").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                when (cursor.getString(nameColumn)) {
                    "index_catalog_posts_post_id" -> oldIndexPresent = true
                    "index_catalog_posts_board_name_post_id" -> compositeIndexPresent = true
                }
            }
        }

        assertFalse(oldIndexPresent)
        assertTrue(compositeIndexPresent)

        val columns = mutableListOf<String>()
        database.query("PRAGMA index_info('index_catalog_posts_board_name_post_id')").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameColumn)
            }
        }
        assertEquals(listOf("board_name", "post_id"), columns)
    }

    private fun assertSingleTextValue(
        database: SupportSQLiteDatabase,
        query: String,
        expected: String
    ) {
        database.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expected, cursor.getString(0))
            assertFalse(cursor.moveToNext())
        }
    }

    private fun assertSingleLongValue(
        database: SupportSQLiteDatabase,
        query: String,
        expected: Long
    ) {
        database.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expected, cursor.getLong(0))
            assertFalse(cursor.moveToNext())
        }
    }
}
