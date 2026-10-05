package com.emogoth.android.phone.mimi.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * Reconstructed version 21 database used to exercise the legacy 21 -> 22 migration.
 *
 * The repository history starts at database version 22 and contains no exported version 21
 * schema. This fixture is therefore derived from the source and target tables in
 * [MimiDatabase.MIGRATION_21_22], rather than presented as an original Room schema export.
 */
internal object Version21DatabaseFixture {
    fun create(context: Context, databaseName: String) {
        val databaseFile = context.getDatabasePath(databaseName)
        val databaseDirectory = databaseFile.parentFile
        check(databaseDirectory != null && (databaseDirectory.isDirectory || databaseDirectory.mkdirs())) {
            "Could not create the database directory for $databaseName"
        }

        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.beginTransaction()
            try {
                createSchema(database)
                insertUserData(database)
                database.version = 21
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    private fun createSchema(database: SQLiteDatabase) {
        VERSION_21_SCHEMA.forEach(database::execSQL)
    }

    private fun insertUserData(database: SQLiteDatabase) {
        database.execSQL(
            """INSERT INTO History
                (id, order_id, thread_id, board_path, user_name, last_access, post_tim,
                 post_text, watched, thread_size, post_replies, thread_removed,
                 last_read_position, unread_count)
                VALUES (1, NULL, 12345, 'g', NULL, NULL, NULL, 'saved thread', NULL,
                        NULL, NULL, NULL, NULL, NULL)"""
        )
        database.execSQL(
            """INSERT INTO thread_posts
                (id, thread_id, post_id, closed, sticky, comment, file_width, file_height,
                 thumb_width, thumb_height, epoch, file_size, resto, bump_limit,
                 image_limit, reply_count, image_count, omitted_posts, omitted_image,
                 spoiler, custom_spoiler)
                VALUES (1, 12345, 12346, NULL, NULL, 'saved reply', NULL, NULL, NULL,
                        NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                        NULL, NULL)"""
        )
        database.execSQL(
            """INSERT INTO Boards
                (id, board_name, board_path, access_count, post_count, board_category,
                 last_accessed, favorite, nsfw, per_page, pages, visible, order_index,
                 max_file_size)
                VALUES (1, 'Technology', 'g', NULL, NULL, NULL, NULL, 1, NULL, NULL,
                        NULL, NULL, NULL, NULL)"""
        )
        database.execSQL(
            """INSERT INTO catalog_posts
                (id, post_id, closed, sticky, file_width, file_height, thumb_width,
                 thumb_height, epoch, file_size, resto, bump_limit, image_limit,
                 reply_count, image_count, omitted_posts, omitted_image, spoiler,
                 custom_spoiler)
                VALUES (1, 999, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                        NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)"""
        )
        database.execSQL(
            """INSERT INTO posts (id, thread_id, post_id, board_path, post_time)
                VALUES (1, 12345, 12346, 'g', NULL)"""
        )
        database.execSQL(
            """INSERT INTO post_filter (id, name, filter, board, highlight)
                VALUES (1, 'hide spam', 'spam', 'g', NULL)"""
        )
        database.execSQL(
            """INSERT INTO post_options (id, option, last_used, used_count)
                VALUES (1, 'sage', NULL, NULL)"""
        )
        database.execSQL(
            """INSERT INTO hidden_threads (id, board_name, thread_id, time, sticky)
                VALUES (1, 'g', 54321, NULL, NULL)"""
        )
        database.execSQL(
            """INSERT INTO archives (id, uid, name, domain, https, software, board, reports)
                VALUES (1, NULL, 'Archive', 'archive.example', NULL, NULL, 'g', NULL)"""
        )
        database.execSQL(
            """INSERT INTO archived_posts
                (id, post_id, thread_id, board_name, media_link, thumb_link,
                 archive_name, archive_domain)
                VALUES (1, 12346, 12345, 'g', 'https://archive.example/file.jpg', NULL,
                        'Archive', 'archive.example')"""
        )
    }

    private val VERSION_21_SCHEMA = listOf(
        """CREATE TABLE History (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            order_id INTEGER, thread_id INTEGER, board_path TEXT, user_name TEXT,
            last_access INTEGER, post_tim TEXT, post_text TEXT, watched INTEGER,
            thread_size INTEGER, post_replies TEXT, thread_removed INTEGER,
            last_read_position INTEGER, unread_count INTEGER)""",
        """CREATE TABLE thread_posts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            thread_id INTEGER, post_id INTEGER, closed INTEGER, sticky INTEGER,
            readable_time TEXT, author TEXT, comment TEXT, subject TEXT,
            old_filename TEXT, new_filename TEXT, file_ext TEXT, file_width INTEGER,
            file_height INTEGER, thumb_width INTEGER, thumb_height INTEGER, epoch INTEGER,
            md5 TEXT, file_size INTEGER, resto INTEGER, bump_limit INTEGER,
            image_limit INTEGER, semantic_url TEXT, reply_count INTEGER,
            image_count INTEGER, omitted_posts INTEGER, omitted_image INTEGER, email TEXT,
            tripcode TEXT, author_id TEXT, capcode TEXT, country TEXT, country_name TEXT,
            troll_country TEXT, spoiler INTEGER, custom_spoiler INTEGER)""",
        """CREATE TABLE Boards (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            board_name TEXT, board_path TEXT, access_count INTEGER, post_count INTEGER,
            board_category INTEGER, last_accessed INTEGER, favorite INTEGER, nsfw INTEGER,
            per_page INTEGER, pages INTEGER, visible INTEGER, order_index INTEGER,
            max_file_size INTEGER)""",
        """CREATE TABLE catalog_posts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            post_id INTEGER, closed INTEGER, sticky INTEGER, readable_time TEXT,
            author TEXT, comment TEXT, subject TEXT, old_filename TEXT, new_filename TEXT,
            file_ext TEXT, file_width INTEGER, file_height INTEGER, thumb_width INTEGER,
            thumb_height INTEGER, epoch INTEGER, md5 TEXT, file_size INTEGER, resto INTEGER,
            bump_limit INTEGER, image_limit INTEGER, semantic_url TEXT, reply_count INTEGER,
            image_count INTEGER, omitted_posts INTEGER, omitted_image INTEGER, email TEXT,
            tripcode TEXT, author_id TEXT, capcode TEXT, country TEXT, country_name TEXT,
            troll_country TEXT, spoiler INTEGER, custom_spoiler INTEGER)""",
        """CREATE TABLE posts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            thread_id INTEGER, post_id INTEGER, board_path TEXT, post_time INTEGER)""",
        """CREATE TABLE post_filter (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT, filter TEXT, board TEXT, highlight INTEGER)""",
        """CREATE TABLE post_options (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            option TEXT, last_used INTEGER, used_count INTEGER)""",
        """CREATE TABLE hidden_threads (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            board_name TEXT, thread_id INTEGER, time INTEGER, sticky INTEGER)""",
        """CREATE TABLE archives (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            uid INTEGER, name TEXT, domain TEXT, https INTEGER, software TEXT,
            board TEXT, reports INTEGER)""",
        """CREATE TABLE archived_posts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            post_id INTEGER, thread_id INTEGER, board_name TEXT, media_link TEXT,
            thumb_link TEXT, archive_name TEXT, archive_domain TEXT)"""
    )
}
