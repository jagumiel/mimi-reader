package com.emogoth.android.phone.mimi.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.emogoth.android.phone.mimi.app.MimiApplication
import com.emogoth.android.phone.mimi.db.dao.*
import com.emogoth.android.phone.mimi.db.models.*

@Database(entities = [
    History::class,
    Post::class,
    Board::class,
    CatalogPost::class,
    UserPost::class,
    Filter::class,
    PostOption::class,
    HiddenThread::class,
    Archive::class,
    ArchivedPost::class,
    RefreshQueue::class],
        version = 23)
abstract class MimiDatabase : RoomDatabase() {
    companion object {
        const val HISTORY_TABLE = "History"
        const val POSTS_TABLE = "thread_posts"
        const val CATALOG_TABLE = "catalog_posts"
        const val BOARDS_TABLE = "Boards"
        const val USER_POSTS_TABLE = "posts"
        const val HIDDEN_THREADS_TABLE = "hidden_threads"
        const val FILTERS_TABLE = "post_filters"
        const val POST_OPTIONS_TABLE = "post_options"
        const val ARCHIVES_TABLE = "archives"
        const val ARCHIVED_POSTS_TABLE = "archived_posts"
        const val REFRESH_QUEUE_TABLE = "refresh_queue"

        private var instance: MimiDatabase? = null

        @JvmStatic
        fun getInstance(): MimiDatabase? {
            if (instance == null) {
                synchronized(MimiDatabase::class) {
                    instance = Room.databaseBuilder(MimiApplication.instance.applicationContext, MimiDatabase::class.java, "mimi.db")
                            .addMigrations(MIGRATION_8_9)
                            .addMigrations(MIGRATION_15_16)
                            .addMigrations(MIGRATION_16_17)
                            .addMigrations(MIGRATION_17_18)
                            .addMigrations(MIGRATION_20_21)
                            .addMigrations(MIGRATION_21_22)
                            .addMigrations(MIGRATION_22_23)
                            .build()
                }
            }

            return instance
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE user_posts")
            }

        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE thread_posts ADD COLUMN troll_country VARCHAR")
                database.execSQL("ALTER TABLE catalog_posts ADD COLUMN troll_country VARCHAR")
            }

        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `History` ADD COLUMN last_read_position INT NOT NULL DEFAULT(0)")
            }

        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `History` ADD COLUMN unread_count INT NOT NULL DEFAULT(0)")
            }

        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE thread_posts ADD COLUMN spoiler INT NOT NULL DEFAULT(0)")
                database.execSQL("ALTER TABLE thread_posts ADD COLUMN custom_spoiler INT NOT NULL DEFAULT(0)")
                database.execSQL("ALTER TABLE catalog_posts ADD COLUMN spoiler INT NOT NULL DEFAULT(0)")
                database.execSQL("ALTER TABLE catalog_posts ADD COLUMN custom_spoiler INT NOT NULL DEFAULT(0)")
            }

        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {

                // History
                database.execSQL("UPDATE `History` SET `last_access` = 0 WHERE `last_access` IS NULL")
                database.execSQL("UPDATE `History` SET `thread_removed` = 0 WHERE `thread_removed` IS NULL")
                database.execSQL("UPDATE `History` SET `last_read_position` = 0 WHERE `last_read_position` IS NULL")
                database.execSQL("UPDATE `History` SET `thread_size` = 0 WHERE `thread_size` IS NULL")
                database.execSQL("UPDATE `History` SET `unread_count` = 0 WHERE `unread_count` IS NULL")
                database.execSQL("UPDATE `History` SET `thread_id` = 0 WHERE `thread_id` IS NULL")
                database.execSQL("UPDATE `History` SET `watched` = 0 WHERE `watched` IS NULL")
                database.execSQL("UPDATE `History` SET `order_id` = 0 WHERE `order_id` IS NULL")

                database.execSQL("UPDATE `History` SET `post_replies` = '' WHERE `post_replies` IS NULL")
                database.execSQL("UPDATE `History` SET `post_tim` = '' WHERE `post_tim` IS NULL")
                database.execSQL("UPDATE `History` SET `user_name` = 'Anonymous' WHERE `user_name` IS NULL")
                database.execSQL("UPDATE `History` SET `board_path` = '' WHERE `board_path` IS NULL")
                database.execSQL("UPDATE `History` SET `post_text` = '' WHERE `post_text` IS NULL")

                database.execSQL("CREATE TEMPORARY TABLE HistoryTemp(`id` INTEGER PRIMARY KEY AUTOINCREMENT, `order_id` INTEGER NOT NULL, `thread_id` INTEGER NOT NULL, `board_path` TEXT NOT NULL, `user_name` TEXT NOT NULL, `last_access` INTEGER NOT NULL, `post_tim` TEXT NOT NULL, `post_text` TEXT NOT NULL, `watched` INTEGER NOT NULL, `thread_size` INTEGER NOT NULL, `post_replies` TEXT NOT NULL, `thread_removed` INTEGER NOT NULL, `last_read_position` INTEGER NOT NULL, `unread_count` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `HistoryTemp` SELECT `id`, `order_id`, `thread_id`, `board_path`, `user_name`, `last_access`, `post_tim`, `post_text`, `watched`, `thread_size`, `post_replies`, `thread_removed`, `last_read_position`, `unread_count` FROM History WHERE `thread_id` > 0")
                database.execSQL("DROP TABLE `History`")

                database.execSQL("CREATE TABLE IF NOT EXISTS `History` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `order_id` INTEGER NOT NULL, `thread_id` INTEGER NOT NULL, `board_path` TEXT NOT NULL, `user_name` TEXT NOT NULL, `last_access` INTEGER NOT NULL, `post_tim` TEXT NOT NULL, `post_text` TEXT NOT NULL, `watched` INTEGER NOT NULL, `thread_size` INTEGER NOT NULL, `post_replies` TEXT NOT NULL, `thread_removed` INTEGER NOT NULL, `last_read_position` INTEGER NOT NULL, `unread_count` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_History_id` ON `History` (`id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_History_board_path` ON `History` (`board_path`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_History_thread_id` ON `History` (`thread_id`)")

                database.execSQL("INSERT OR IGNORE INTO `History` SELECT `id`, `order_id`, `thread_id`, `board_path`, `user_name`, `last_access`, `post_tim`, `post_text`, `watched`, `thread_size`, `post_replies`, `thread_removed`, `last_read_position`, `unread_count` FROM `HistoryTemp`")
                database.execSQL("DROP TABLE `HistoryTemp`")


                // Thread Posts
                database.execSQL("UPDATE `thread_posts` SET `thread_id` = 0 WHERE `thread_id` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `post_id` = 0 WHERE `post_id` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `closed` = 0 WHERE `closed` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `sticky` = 0 WHERE `sticky` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `file_width` = 0 WHERE `file_width` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `file_height` = 0 WHERE `file_height` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `thumb_width` = 0 WHERE `thumb_width` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `thumb_height` = 0 WHERE `thumb_height` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `epoch` = 0 WHERE `epoch` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `file_size` = 0 WHERE `file_size` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `resto` = 0 WHERE `resto` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `bump_limit` = 0 WHERE `bump_limit` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `image_limit` = 0 WHERE `image_limit` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `reply_count` = 0 WHERE `reply_count` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `image_count` = 0 WHERE `image_count` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `omitted_posts` = 0 WHERE `omitted_posts` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `omitted_image` = 0 WHERE `omitted_image` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `spoiler` = 0 WHERE `spoiler` IS NULL")
                database.execSQL("UPDATE `thread_posts` SET `custom_spoiler` = 0 WHERE `custom_spoiler` IS NULL")

                database.execSQL("ALTER TABLE thread_posts ADD COLUMN board_name TEXT DEFAULT 'Unknown' NOT NULL")

                database.execSQL("CREATE TEMPORARY TABLE `thread_posts_temp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `thread_id` INTEGER NOT NULL, `board_name` TEXT NOT NULL, `post_id` INTEGER NOT NULL, `closed` INTEGER NOT NULL, `sticky` INTEGER NOT NULL, `readable_time` TEXT, `author` TEXT, `comment` TEXT, `subject` TEXT, `old_filename` TEXT, `new_filename` TEXT, `file_ext` TEXT, `file_width` INTEGER NOT NULL, `file_height` INTEGER NOT NULL, `thumb_width` INTEGER NOT NULL, `thumb_height` INTEGER NOT NULL, `epoch` INTEGER NOT NULL, `md5` TEXT, `file_size` INTEGER NOT NULL, `resto` INTEGER NOT NULL, `bump_limit` INTEGER NOT NULL, `image_limit` INTEGER NOT NULL, `semantic_url` TEXT, `reply_count` INTEGER NOT NULL, `image_count` INTEGER NOT NULL, `omitted_posts` INTEGER NOT NULL, `omitted_image` INTEGER NOT NULL, `email` TEXT, `tripcode` TEXT, `author_id` TEXT, `capcode` TEXT, `country` TEXT, `country_name` TEXT, `troll_country` TEXT, `spoiler` INTEGER NOT NULL, `custom_spoiler` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `thread_posts_temp` SELECT `id`, `thread_id`, `board_name`, `post_id`, `closed`, `sticky`, `readable_time`, `author`, `comment`, `subject`, `old_filename`, `new_filename`, `file_ext`, `file_width`, `file_height`, `thumb_width`, `thumb_height`, `epoch`, `md5` TEXT, `file_size`, `resto`, `bump_limit`, `image_limit`, `semantic_url`, `reply_count`, `image_count`, `omitted_posts`, `omitted_image`, `email`, `tripcode`, `author_id`, `capcode`, `country`, `country_name`, `troll_country`, `spoiler`, `custom_spoiler` FROM `thread_posts`")
                database.execSQL("DROP TABLE thread_posts")

                database.execSQL("CREATE TABLE IF NOT EXISTS `thread_posts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `thread_id` INTEGER NOT NULL, `board_name` TEXT NOT NULL, `post_id` INTEGER NOT NULL, `closed` INTEGER NOT NULL, `sticky` INTEGER NOT NULL, `readable_time` TEXT, `author` TEXT, `comment` TEXT, `subject` TEXT, `old_filename` TEXT, `new_filename` TEXT, `file_ext` TEXT, `file_width` INTEGER NOT NULL, `file_height` INTEGER NOT NULL, `thumb_width` INTEGER NOT NULL, `thumb_height` INTEGER NOT NULL, `epoch` INTEGER NOT NULL, `md5` TEXT, `file_size` INTEGER NOT NULL, `resto` INTEGER NOT NULL, `bump_limit` INTEGER NOT NULL, `image_limit` INTEGER NOT NULL, `semantic_url` TEXT, `reply_count` INTEGER NOT NULL, `image_count` INTEGER NOT NULL, `omitted_posts` INTEGER NOT NULL, `omitted_image` INTEGER NOT NULL, `email` TEXT, `tripcode` TEXT, `author_id` TEXT, `capcode` TEXT, `country` TEXT, `country_name` TEXT, `troll_country` TEXT, `spoiler` INTEGER NOT NULL, `custom_spoiler` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_thread_posts_id` ON `thread_posts` (`id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_thread_posts_post_id` ON `thread_posts` (`post_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_thread_posts_thread_id` ON `thread_posts` (`thread_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_thread_posts_board_name` ON `thread_posts` (`board_name`)")

                database.execSQL("INSERT OR IGNORE INTO `thread_posts` SELECT `id`, `thread_id`, `board_name`, `post_id`, `closed`, `sticky`, `readable_time`, `author`, `comment`, `subject`, `old_filename`, `new_filename`, `file_ext`, `file_width`, `file_height`, `thumb_width`, `thumb_height`, `epoch`, `md5` TEXT, `file_size`, `resto`, `bump_limit`, `image_limit`, `semantic_url`, `reply_count`, `image_count`, `omitted_posts`, `omitted_image`, `email`, `tripcode`, `author_id`, `capcode`, `country`, `country_name`, `troll_country`, `spoiler`, `custom_spoiler` FROM `thread_posts_temp`")
                database.execSQL("DROP TABLE `thread_posts_temp`")


                // Boards
                database.execSQL("UPDATE `Boards` SET `access_count` = 0 WHERE `access_count` IS NULL")
                database.execSQL("UPDATE `Boards` SET `post_count` = 0 WHERE `post_count` IS NULL")
                database.execSQL("UPDATE `Boards` SET `board_category` = 0 WHERE `board_category` IS NULL")
                database.execSQL("UPDATE `Boards` SET `last_accessed` = 0 WHERE `last_accessed` IS NULL")
                database.execSQL("UPDATE `Boards` SET `favorite` = 0 WHERE `favorite` IS NULL")
                database.execSQL("UPDATE `Boards` SET `nsfw` = 0 WHERE `nsfw` IS NULL")
                database.execSQL("UPDATE `Boards` SET `per_page` = 0 WHERE `per_page` IS NULL")
                database.execSQL("UPDATE `Boards` SET `pages` = 0 WHERE `pages` IS NULL")
                database.execSQL("UPDATE `Boards` SET `visible` = 0 WHERE `visible` IS NULL")
                database.execSQL("UPDATE `Boards` SET `order_index` = 0 WHERE `order_index` IS NULL")
                database.execSQL("UPDATE `Boards` SET `max_file_size` = 0 WHERE `max_file_size` IS NULL")

                database.execSQL("UPDATE `Boards` SET `board_name` = '' WHERE `board_name` IS NULL")
                database.execSQL("UPDATE `Boards` SET `board_path` = '' WHERE `board_path` IS NULL")

                database.execSQL("CREATE TEMPORARY TABLE `BoardsTemp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `board_name` TEXT NOT NULL, `board_path` TEXT NOT NULL, `access_count` INTEGER NOT NULL, `post_count` INTEGER NOT NULL, `board_category` INTEGER NOT NULL, `last_accessed` INTEGER NOT NULL, `favorite` INTEGER NOT NULL, `nsfw` INTEGER NOT NULL, `per_page` INTEGER NOT NULL, `pages` INTEGER NOT NULL, `visible` INTEGER NOT NULL, `order_index` INTEGER NOT NULL, `max_file_size` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `BoardsTemp` SELECT `id`, `board_name`, `board_path`, `access_count`, `post_count`, `board_category`, `last_accessed`, `favorite`, `nsfw`, `per_page`, `pages`, `visible`, `order_index`, `max_file_size` From `Boards`")
                database.execSQL("DROP TABLE `Boards`")

                database.execSQL("CREATE TABLE IF NOT EXISTS `Boards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `board_name` TEXT NOT NULL, `board_path` TEXT NOT NULL, `access_count` INTEGER NOT NULL, `post_count` INTEGER NOT NULL, `board_category` INTEGER NOT NULL, `last_accessed` INTEGER NOT NULL, `favorite` INTEGER NOT NULL, `nsfw` INTEGER NOT NULL, `per_page` INTEGER NOT NULL, `pages` INTEGER NOT NULL, `visible` INTEGER NOT NULL, `order_index` INTEGER NOT NULL, `max_file_size` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Boards_board_path` ON `Boards` (`board_path`)")

                database.execSQL("INSERT OR IGNORE INTO `Boards` SELECT `id`, `board_name`, `board_path`, `access_count`, `post_count`, `board_category`, `last_accessed`, `favorite`, `nsfw`, `per_page`, `pages`, `visible`, `order_index`, `max_file_size` From `BoardsTemp`")
                database.execSQL("DROP TABLE `BoardsTemp`")


                // Catalog
                database.execSQL("UPDATE `catalog_posts` SET `post_id` = 0 WHERE `post_id` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `closed` = 0 WHERE `closed` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `sticky` = 0 WHERE `sticky` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `file_width` = 0 WHERE `file_width` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `file_height` = 0 WHERE `file_height` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `thumb_width` = 0 WHERE `thumb_width` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `thumb_height` = 0 WHERE `thumb_height` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `epoch` = 0 WHERE `epoch` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `file_size` = 0 WHERE `file_size` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `resto` = 0 WHERE `resto` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `bump_limit` = 0 WHERE `bump_limit` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `image_limit` = 0 WHERE `image_limit` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `reply_count` = 0 WHERE `reply_count` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `image_count` = 0 WHERE `image_count` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `omitted_posts` = 0 WHERE `omitted_posts` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `omitted_image` = 0 WHERE `omitted_image` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `spoiler` = 0 WHERE `spoiler` IS NULL")
                database.execSQL("UPDATE `catalog_posts` SET `custom_spoiler` = 0 WHERE `custom_spoiler` IS NULL")

                database.execSQL("CREATE TEMPORARY TABLE `catalog_posts_temp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `post_id` INTEGER NOT NULL, `closed` INTEGER NOT NULL, `sticky` INTEGER NOT NULL, `readable_time` TEXT, `author` TEXT, `comment` TEXT, `subject` TEXT, `old_filename` TEXT, `new_filename` TEXT, `file_ext` TEXT, `file_width` INTEGER NOT NULL, `file_height` INTEGER NOT NULL, `thumb_width` INTEGER NOT NULL, `thumb_height` INTEGER NOT NULL, `epoch` INTEGER NOT NULL, `md5` TEXT, `file_size` INTEGER NOT NULL, `resto` INTEGER NOT NULL, `bump_limit` INTEGER NOT NULL, `image_limit` INTEGER NOT NULL, `semantic_url` TEXT, `reply_count` INTEGER NOT NULL, `image_count` INTEGER NOT NULL, `omitted_posts` INTEGER NOT NULL, `omitted_image` INTEGER NOT NULL, `email` TEXT, `tripcode` TEXT, `author_id` TEXT, `capcode` TEXT, `country` TEXT, `country_name` TEXT, `troll_country` TEXT, `spoiler` INTEGER NOT NULL, `custom_spoiler` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `catalog_posts_temp` SELECT `id`, `post_id`, `closed`, `sticky`, `readable_time`, `author`, `comment`, `subject`, `old_filename`, `new_filename`, `file_ext`, `file_width`, `file_height`, `thumb_width`, `thumb_height`, `epoch`, `md5`, `file_size`, `resto`, `bump_limit`, `image_limit`, `semantic_url`, `reply_count`, `image_count`, `omitted_posts`, `omitted_image`, `email`, `tripcode`, `author_id`, `capcode`, `country`, `country_name`, `troll_country`, `spoiler`, `custom_spoiler` FROM `catalog_posts`")
                database.execSQL("DROP TABLE `catalog_posts`")

                database.execSQL("CREATE TABLE IF NOT EXISTS `catalog_posts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `post_id` INTEGER NOT NULL, `closed` INTEGER NOT NULL, `sticky` INTEGER NOT NULL, `readable_time` TEXT, `author` TEXT, `comment` TEXT, `subject` TEXT, `old_filename` TEXT, `new_filename` TEXT, `file_ext` TEXT, `file_width` INTEGER NOT NULL, `file_height` INTEGER NOT NULL, `thumb_width` INTEGER NOT NULL, `thumb_height` INTEGER NOT NULL, `epoch` INTEGER NOT NULL, `md5` TEXT, `file_size` INTEGER NOT NULL, `resto` INTEGER NOT NULL, `bump_limit` INTEGER NOT NULL, `image_limit` INTEGER NOT NULL, `semantic_url` TEXT, `reply_count` INTEGER NOT NULL, `image_count` INTEGER NOT NULL, `omitted_posts` INTEGER NOT NULL, `omitted_image` INTEGER NOT NULL, `email` TEXT, `tripcode` TEXT, `author_id` TEXT, `capcode` TEXT, `country` TEXT, `country_name` TEXT, `troll_country` TEXT, `spoiler` INTEGER NOT NULL, `custom_spoiler` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_catalog_posts_post_id` ON `catalog_posts` (`post_id`)")

                database.execSQL("INSERT OR IGNORE INTO `catalog_posts` SELECT `id`, `post_id`, `closed`, `sticky`, `readable_time`, `author`, `comment`, `subject`, `old_filename`, `new_filename`, `file_ext`, `file_width`, `file_height`, `thumb_width`, `thumb_height`, `epoch`, `md5`, `file_size`, `resto`, `bump_limit`, `image_limit`, `semantic_url`, `reply_count`, `image_count`, `omitted_posts`, `omitted_image`, `email`, `tripcode`, `author_id`, `capcode`, `country`, `country_name`, `troll_country`, `spoiler`, `custom_spoiler` FROM `catalog_posts_temp`")
                database.execSQL("DROP TABLE `catalog_posts_temp`")


                // User Posts
                database.execSQL("UPDATE `posts` SET `thread_id` = 0 WHERE `thread_id` IS NULL")
                database.execSQL("UPDATE `posts` SET `post_id` = 0 WHERE `post_id` IS NULL")
                database.execSQL("UPDATE `posts` SET `post_time` = 0 WHERE `post_time` IS NULL")

                database.execSQL("CREATE TEMPORARY TABLE `posts_temp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `thread_id` INTEGER NOT NULL, `post_id` INTEGER NOT NULL, `board_path` TEXT, `post_time` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `posts_temp` SELECT `id`, `thread_id`, `post_id`, `board_path`, `post_time` FROM `posts`")
                database.execSQL("DROP TABLE `posts`")

                database.execSQL("CREATE TABLE IF NOT EXISTS `posts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `thread_id` INTEGER NOT NULL, `post_id` INTEGER NOT NULL, `board_path` TEXT, `post_time` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_posts_id` ON `posts` (`id`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_posts_post_id` ON `posts` (`post_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_posts_thread_id` ON `posts` (`thread_id`)")

                database.execSQL("INSERT OR IGNORE INTO `posts` SELECT `id`, `thread_id`, `post_id`, `board_path`, `post_time` FROM `posts_temp`")
                database.execSQL("DROP TABLE `posts_temp`")


                // Filters (Renaming table from post_filter to post_filters)
                database.execSQL("UPDATE `post_filter` SET `highlight` = 0 WHERE `highlight` IS NULL")

                database.execSQL("CREATE TEMPORARY TABLE `post_filters_temp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `name` TEXT NOT NULL, `filter` TEXT NOT NULL, `board` TEXT NOT NULL, `highlight` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `post_filters_temp` SELECT `id`, `name`, `filter`, `board`, `highlight` FROM `post_filter` WHERE `name` IS NOT NULL AND `filter` IS NOT NULL AND `board` IS NOT NULL")
                database.execSQL("DROP TABLE `post_filter`")

                database.execSQL("CREATE TABLE IF NOT EXISTS `post_filters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `name` TEXT NOT NULL, `filter` TEXT NOT NULL, `board` TEXT NOT NULL, `highlight` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_post_filters_id` ON `post_filters` (`id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_post_filters_board` ON `post_filters` (`board`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_post_filters_name` ON `post_filters` (`name`)")

                database.execSQL("INSERT OR IGNORE INTO `post_filters` SELECT `id`, `name`, `filter`, `board`, `highlight` FROM post_filters_temp")
                database.execSQL("DROP TABLE `post_filters_temp`")


                // Post Options
                database.execSQL("UPDATE `post_options` SET `last_used` = 0 WHERE `last_used` IS NULL")
                database.execSQL("UPDATE `post_options` SET `used_count` = 0 WHERE `used_count` IS NULL")
                database.execSQL("UPDATE `post_options` SET `option` = '' WHERE `option` IS NULL")

                database.execSQL("CREATE TEMPORARY TABLE `post_options_temp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `option` TEXT NOT NULL, `last_used` INTEGER NOT NULL, `used_count` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `post_options_temp` SELECT `id`, `option`, `last_used`, `used_count` FROM `post_options` WHERE `option` IS NOT NULL")
                database.execSQL("DROP TABLE `post_options`")

                database.execSQL("CREATE TABLE IF NOT EXISTS `post_options` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `option` TEXT NOT NULL, `last_used` INTEGER NOT NULL, `used_count` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_post_options_id` ON `post_options` (`id`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_post_options_option` ON `post_options` (`option`)")

                database.execSQL("INSERT INTO `post_options` SELECT `id`, `option`, `last_used`, `used_count` FROM `post_options_temp`")
                database.execSQL("DROP TABLE `post_options_temp`")


                // Hidden Threads
                database.execSQL("UPDATE `hidden_threads` SET `thread_id` = 0 WHERE `thread_id` IS NULL")
                database.execSQL("UPDATE `hidden_threads` SET `time` = 0 WHERE `time` IS NULL")
                database.execSQL("UPDATE `hidden_threads` SET `sticky` = 0 WHERE `sticky` IS NULL")

                database.execSQL("CREATE TEMPORARY TABLE `hidden_threads_temp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `board_name` TEXT NOT NULL, `thread_id` INTEGER NOT NULL, `time` INTEGER NOT NULL, `sticky` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `hidden_threads_temp` SELECT `id`, `board_name`, `thread_id`, `time`, `sticky` FROM `hidden_threads` WHERE `board_name` IS NOT NULL")
                database.execSQL("DROP TABLE `hidden_threads`")

                database.execSQL("CREATE TABLE IF NOT EXISTS `hidden_threads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `board_name` TEXT NOT NULL, `thread_id` INTEGER NOT NULL, `time` INTEGER NOT NULL, `sticky` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_hidden_threads_id` ON `hidden_threads` (`id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_hidden_threads_board_name` ON `hidden_threads` (`board_name`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_hidden_threads_thread_id` ON `hidden_threads` (`thread_id`)")

                database.execSQL("INSERT OR IGNORE INTO `hidden_threads` SELECT `id`, `board_name`, `thread_id`, `time`, `sticky` FROM `hidden_threads_temp`")
                database.execSQL("DROP TABLE `hidden_threads_temp`")


                // Archives
                database.execSQL("UPDATE `archives` SET `uid` = 0 WHERE `uid` IS NULL")
                database.execSQL("UPDATE `archives` SET `https` = 1 WHERE `https` IS NULL")
                database.execSQL("UPDATE `archives` SET `reports` = 0 WHERE `reports` IS NULL")

                database.execSQL("CREATE TEMPORARY TABLE `archives_temp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `uid` INTEGER NOT NULL, `name` TEXT, `domain` TEXT, `https` INTEGER NOT NULL, `software` TEXT, `board` TEXT NOT NULL, `reports` INTEGER NOT NULL)")
                database.execSQL("INSERT INTO `archives_temp` SELECT `id`, `uid`, `name`, `domain`, `https`, `software`, `board`, `reports` FROM `archives` WHERE `board` IS NOT NULL")
                database.execSQL("DROP TABLE `archives`")

                database.execSQL("CREATE TABLE IF NOT EXISTS `archives` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `uid` INTEGER NOT NULL, `name` TEXT, `domain` TEXT, `https` INTEGER NOT NULL, `software` TEXT, `board` TEXT NOT NULL, `reports` INTEGER NOT NULL)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_archives_board` ON `archives` (`board`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_archives_domain` ON `archives` (`domain`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_archives_uid` ON `archives` (`uid`)")

                database.execSQL("INSERT OR IGNORE INTO `archives` SELECT `id`, `uid`, `name`, `domain`, `https`, `software`, `board`, `reports` FROM `archives_temp`")
                database.execSQL("DROP TABLE `archives_temp`")


                // Archived Posts
                database.execSQL("UPDATE `archived_posts` SET `post_id` = 0 WHERE `post_id` IS NULL")
                database.execSQL("UPDATE `archived_posts` SET `thread_id` = 0 WHERE `thread_id` IS NULL")

                database.execSQL("CREATE TEMPORARY TABLE `archived_posts_temp` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `post_id` INTEGER NOT NULL, `thread_id` INTEGER NOT NULL, `board_name` TEXT NOT NULL, `media_link` TEXT, `thumb_link` TEXT, `archive_name` TEXT, `archive_domain` TEXT)")
                database.execSQL("INSERT INTO `archived_posts_temp` SELECT `id`, `post_id`, `thread_id`, `board_name`, `media_link`, `thumb_link`, `archive_name`, `archive_domain` FROM `archived_posts` WHERE `board_name` IS NOT NULL")
                database.execSQL("DROP TABLE `archived_posts`")

                database.execSQL("CREATE TABLE IF NOT EXISTS `archived_posts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `post_id` INTEGER NOT NULL, `thread_id` INTEGER NOT NULL, `board_name` TEXT NOT NULL, `media_link` TEXT, `thumb_link` TEXT, `archive_name` TEXT, `archive_domain` TEXT)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_archived_posts_post_id` ON `archived_posts` (`post_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_archived_posts_thread_id` ON `archived_posts` (`thread_id`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_archived_posts_board_name` ON `archived_posts` (`board_name`)")

                database.execSQL("INSERT OR IGNORE INTO `archived_posts` SELECT `id`, `post_id`, `thread_id`, `board_name`, `media_link`, `thumb_link`, `archive_name`, `archive_domain` FROM `archived_posts_temp`")
                database.execSQL("DROP TABLE `archived_posts_temp`")

                // Refresh Queue

                // This table should not exist in DB version 21 so dropping it should have no impact, but keeping it can cause a migration error during development
                database.execSQL("DROP TABLE IF EXISTS `refresh_queue`")

                database.execSQL("CREATE TABLE IF NOT EXISTS `refresh_queue` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `history_id` INTEGER, `thread_size` INTEGER NOT NULL, `reply_count` INTEGER NOT NULL, `last_refresh` INTEGER NOT NULL, FOREIGN KEY(`history_id`) REFERENCES `History`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_refresh_queue_id` ON `refresh_queue` (`id`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_refresh_queue_history_id` ON `refresh_queue` (`history_id`)")
            }

        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `catalog_posts` ADD COLUMN `board_name` TEXT NOT NULL DEFAULT ''")
                database.execSQL("DROP INDEX IF EXISTS `index_catalog_posts_post_id`")
                database.execSQL("DELETE FROM `catalog_posts`")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_catalog_posts_board_name_post_id` ON `catalog_posts` (`board_name`, `post_id`)")
            }
        }
    }

    abstract fun history(): HistoryAccess
    abstract fun boards(): BoardAccess
    abstract fun posts(): PostAccess
    abstract fun catalog(): CatalogAccess
    abstract fun userPosts(): UserPostAccess
    abstract fun hiddenThreads(): HiddenThreadAccess
    abstract fun filters(): FilterAccess
    abstract fun postOptions(): PostOptionAccess
    abstract fun archives(): ArchiveAccess
    abstract fun archivedPosts(): ArchivedPostAccess
    abstract fun refreshQueue(): RefreshQueueAccess
}
