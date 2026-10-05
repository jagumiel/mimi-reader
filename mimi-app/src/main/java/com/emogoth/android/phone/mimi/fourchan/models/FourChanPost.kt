package com.emogoth.android.phone.mimi.fourchan.models

import android.content.Context
import com.emogoth.android.phone.mimi.fourchan.FourChanCommentParser
import com.emogoth.android.phone.mimi.util.MimiUtil
import com.emogoth.android.phone.mimi.util.MediaContract
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import com.mimireader.chanlib.interfaces.PostConverter
import com.mimireader.chanlib.models.ChanPost
import java.util.*

class FourChanPost : PostConverter {
    @SerializedName("no")
    @Expose
    var no = 0

    @SerializedName("closed")
    @Expose
    var closed = 0

    @SerializedName("sticky")
    @Expose
    var sticky = 0

    @SerializedName("now")
    @Expose
    var now: String? = null

    @SerializedName("name")
    @Expose
    var name: String? = null

    @SerializedName("com")
    @Expose
    var com: String? = null

    @SerializedName("sub")
    @Expose
    var sub: String? = null

    //    private Spanned subject;
    @SerializedName("filename")
    @Expose
    var filename: String? = null

    @SerializedName("ext")
    @Expose
    var ext: String? = null

    @SerializedName("w")
    @Expose
    var w = 0

    @SerializedName("h")
    @Expose
    var h = 0

    @SerializedName("tn_w")
    @Expose
    var tnW = 0

    @SerializedName("tn_h")
    @Expose
    var tnH = 0

    @SerializedName("tim")
    @Expose
    var tim: String? = null

    @SerializedName("time")
    @Expose
    var time = 0

    @SerializedName("md5")
    @Expose
    var md5: String? = null

    @SerializedName("fsize")
    @Expose
    var fileSize = 0

    @SerializedName("resto")
    @Expose
    var resto = 0

    @SerializedName("bumplimit")
    @Expose
    var bumplimit = 0

    @SerializedName("imagelimit")
    @Expose
    var imagelimit = 0

    @SerializedName("semantic_url")
    @Expose
    var semanticUrl: String? = null

    @SerializedName("replies")
    @Expose
    var replies = 0

    @SerializedName("images")
    @Expose
    var images = 0

    @SerializedName("omitted_posts")
    @Expose
    var omittedPosts = 0

    @SerializedName("omitted_images")
    @Expose
    var omittedImages = 0

    @SerializedName("last_replies")
    @Expose
    var lastReplies: List<FourChanPost> = ArrayList()

    @Expose
    var email: String? = null

    @Expose
    var trip: String? = null

    @Expose
    var id: String? = null

    @Expose
    var capcode: String? = null

    @SerializedName("country")
    @Expose
    var country: String? = null

    @SerializedName("country_name")
    @Expose
    var countryName: String? = null

    @SerializedName("troll_country")
    @Expose
    var trollCountry: String? = null

    @SerializedName("spoiler")
    @Expose
    private val spoiler = 0

    @SerializedName("custom_spoiler")
    @Expose
    private val customSpoiler = 0
    var comment: CharSequence? = null

    //    public Spanned getSubject() {
    //        return subject;
    //    }
    //
    //    public void setSubject(Spanned subject) {
    //        this.subject = subject;
    //    }
    override fun toPost(): ChanPost {
        val post = ChanPost()
        post.no = no.toLong()
        post.isClosed = closed == 1
        post.isSticky = sticky == 1
        post.bumplimit = bumplimit
        post.com = com
        post.sub = sub
        post.name = name
        post.ext = MediaContract.normalizedExtension(ext).ifEmpty { null }
        post.filename = filename
        post.fsize = fileSize
        post.height = h
        post.width = w
        post.thumbnailHeight = tnH
        post.thumbnailWidth = tnW
        post.imagelimit = imagelimit
        post.images = images
        post.replies = replies
        post.resto = resto
        post.omittedImages = omittedImages
        post.omittedPosts = omittedPosts
        post.semanticUrl = semanticUrl
        post.md5 = md5
        post.tim = tim
        post.time = time.toLong()
        post.email = email
        post.trip = trip
        post.id = id
        post.capcode = capcode
        post.country = country
        post.countryName = countryName
        post.trollCountry = trollCountry
        post.spoiler = spoiler
        post.customSpoiler = customSpoiler
        return post
    }

    fun processComment(context: Context?, boardName: String?, threadId: Long) {
        if (com != null) {
            val parserBuilder = FourChanCommentParser.Builder()
            parserBuilder.setContext(context)
                    .setBoardName(boardName)
                    .setThreadId(threadId)
                    .setComment(com)
                    .setQuoteColor(MimiUtil.getInstance().quoteColor)
                    .setReplyColor(MimiUtil.getInstance().replyColor)
                    .setHighlightColor(MimiUtil.getInstance().highlightColor)
                    .setLinkColor(MimiUtil.getInstance().linkColor)
            comment = parserBuilder.build().parse()
        }
    }
}
