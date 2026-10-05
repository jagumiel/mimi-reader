package com.emogoth.android.phone.mimi.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emogoth.android.phone.mimi.db.MimiDatabase
import com.mimireader.chanlib.models.ChanPost

@Entity(tableName = MimiDatabase.CATALOG_TABLE, indices = [Index(value = [CatalogPost.BOARD_NAME, CatalogPost.POST_ID], unique = true)])
class CatalogPost() {

    constructor(boardName: String, other: ChanPost): this() {
        this.boardName = boardName
        postId = other.no
        closed = if (other.isClosed) 1 else 0
        sticky = if (other.isSticky) 1 else 0
        readableTime = other.now
        author = other.name
        comment = other.com
        subject = other.sub
        oldFilename = other.filename
        newFilename = other.tim
        fileExt = other.ext
        fileWidth = other.width
        fileHeight = other.height
        thumbnailWidth = other.thumbnailWidth
        thumbnailHeight = other.thumbnailHeight
        epoch = other.time.toInt()
        md5 = other.md5
        fileSize = other.fsize
        resto = other.resto
        bumplimit = other.bumplimit
        imagelimit = other.imagelimit
        semanticUrl = other.semanticUrl
        replyCount = other.replies
        imageCount = other.images
        omittedPosts = other.omittedPosts
        omittedImages = other.omittedImages
        email = other.email
        tripcode = other.trip
        authorId = other.id
        capcode = other.capcode
        country = other.country
        countryName = other.countryName
        trollCountry = other.trollCountry
        spoiler = other.spoiler
        customSpoiler = other.customSpoiler
    }

    companion object {
        const val ID = "id"
        const val BOARD_NAME = "board_name"
        const val POST_ID = "post_id"
        const val CLOSED = "closed"
        const val STICKY = "sticky"
        const val READABLE_TIME = "readable_time"
        const val AUTHOR = "author"
        const val COMMENT = "comment"
        const val SUBJECT = "subject"
        const val OLD_FILENAME = "old_filename"
        const val NEW_FILENAME = "new_filename"
        const val FILE_SIZE = "file_size"
        const val FILE_EXT = "file_ext"
        const val FILE_WIDTH = "file_width"
        const val FILE_HEIGHT = "file_height"
        const val THUMB_WIDTH = "thumb_width"
        const val THUMB_HEIGHT = "thumb_height"
        const val EPOCH = "epoch"
        const val MD5 = "md5"
        const val RESTO = "resto"
        const val BUMP_LIMIT = "bump_limit"
        const val IMAGE_LIMIT = "image_limit"
        const val SEMANTIC_URL = "semantic_url"
        const val REPLY_COUNT = "reply_count"
        const val IMAGE_COUNT = "image_count"
        const val OMITTED_POSTS = "omitted_posts"
        const val OMITTED_IMAGE = "omitted_image"
        const val EMAIL = "email"
        const val TRIPCODE = "tripcode"
        const val AUTHOR_ID = "author_id"
        const val CAPCODE = "capcode"
        const val COUNTRY = "country"
        const val COUNTRY_NAME = "country_name"
        const val TROLL_COUNTRY = "troll_country"
        const val SPOILER = "spoiler"
        const val CUSTOM_SPOILER = "custom_spoiler"
    }

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = ID)
    var id: Int? = null

    @ColumnInfo(name = BOARD_NAME)
    var boardName: String = ""

    @ColumnInfo(name = POST_ID)
    var postId: Long = 0
    @ColumnInfo(name = CLOSED)
    var closed: Int = 0
    @ColumnInfo(name = STICKY)
    var sticky: Int = 0
    @ColumnInfo(name = READABLE_TIME)
    var readableTime: String? = null
    @ColumnInfo(name = AUTHOR)
    var author: String? = null
    @ColumnInfo(name = COMMENT)
    var comment: String? = null
    @ColumnInfo(name = SUBJECT)
    var subject: String? = null
    @ColumnInfo(name = OLD_FILENAME)
    var oldFilename: String? = null
    @ColumnInfo(name = NEW_FILENAME)
    var newFilename: String? = null
    @ColumnInfo(name = FILE_EXT)
    var fileExt: String? = null
    @ColumnInfo(name = FILE_WIDTH)
    var fileWidth: Int = 0
    @ColumnInfo(name = FILE_HEIGHT)
    var fileHeight: Int = 0
    @ColumnInfo(name = THUMB_WIDTH)
    var thumbnailWidth: Int = 0
    @ColumnInfo(name = THUMB_HEIGHT)
    var thumbnailHeight: Int = 0
    @ColumnInfo(name = EPOCH)
    var epoch: Int = 0
    @ColumnInfo(name = MD5)
    var md5: String? = null
    @ColumnInfo(name = FILE_SIZE)
    var fileSize: Int = 0
    @ColumnInfo(name = RESTO)
    var resto: Int = 0
    @ColumnInfo(name = BUMP_LIMIT)
    var bumplimit: Int = 0
    @ColumnInfo(name = IMAGE_LIMIT)
    var imagelimit: Int = 0
    @ColumnInfo(name = SEMANTIC_URL)
    var semanticUrl: String? = null
    @ColumnInfo(name = REPLY_COUNT)
    var replyCount: Int = 0
    @ColumnInfo(name = IMAGE_COUNT)
    var imageCount: Int = 0
    @ColumnInfo(name = OMITTED_POSTS)
    var omittedPosts: Int = 0
    @ColumnInfo(name = OMITTED_IMAGE)
    var omittedImages: Int = 0
    @ColumnInfo(name = EMAIL)
    var email: String? = null
    @ColumnInfo(name = TRIPCODE)
    var tripcode: String? = null
    @ColumnInfo(name = AUTHOR_ID)
    var authorId: String? = null
    @ColumnInfo(name = CAPCODE)
    var capcode: String? = null
    @ColumnInfo(name = COUNTRY)
    var country: String? = null
    @ColumnInfo(name = COUNTRY_NAME)
    var countryName: String? = null
    @ColumnInfo(name = TROLL_COUNTRY)
    var trollCountry: String? = null
    @ColumnInfo(name = SPOILER)
    var spoiler: Int = 0
    @ColumnInfo(name = CUSTOM_SPOILER)
    var customSpoiler: Int = 0


    fun toPost(): ChanPost {
        val post = ChanPost()
        post.no = postId
        post.isClosed = closed == 1
        post.isSticky = sticky == 1
        post.bumplimit = bumplimit
        post.com = comment
        post.sub = subject
        post.name = author
        post.ext = fileExt
        post.filename = oldFilename
        post.fsize = fileSize
        post.height = fileHeight
        post.width = fileWidth
        post.thumbnailHeight = thumbnailHeight
        post.thumbnailWidth = thumbnailWidth
        post.imagelimit = imagelimit
        post.images = imageCount
        post.replies = replyCount
        post.resto = resto
        post.omittedImages = omittedImages
        post.omittedPosts = omittedPosts
        post.semanticUrl = semanticUrl
        post.md5 = md5
        post.tim = newFilename
        post.time = epoch.toLong()
        post.email = email
        post.trip = tripcode
        post.id = authorId
        post.capcode = capcode
        post.country = country
        post.countryName = countryName
        post.trollCountry = trollCountry
        post.spoiler = spoiler
        post.customSpoiler = customSpoiler
        return post
    }
}
