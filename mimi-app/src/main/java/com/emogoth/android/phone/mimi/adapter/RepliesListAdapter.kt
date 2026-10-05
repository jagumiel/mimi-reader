package com.emogoth.android.phone.mimi.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.app.MimiApplication
import com.emogoth.android.phone.mimi.event.ReplyClickEvent
import com.emogoth.android.phone.mimi.fourchan.FourChanEndpoints
import com.emogoth.android.phone.mimi.model.OutsideLink
import com.emogoth.android.phone.mimi.util.GlideApp
import com.emogoth.android.phone.mimi.util.MimiUtil
import com.emogoth.android.phone.mimi.util.MediaContract
import com.emogoth.android.phone.mimi.view.LongClickLinkMovementMethod
import com.mimireader.chanlib.models.ArchivedChanPost
import com.mimireader.chanlib.models.ChanPost
import com.mimireader.chanlib.models.ChanThread
import java.util.Locale

// final List<ChanPost> replies, final List<OutsideLink> links, final ChanThread thread
class RepliesListAdapter(val replies: List<ChanPost>, private val links: List<OutsideLink>, val thread: ChanThread) : RecyclerView.Adapter<RepliesViewHolder>() {
    companion object {
        const val POST_TYPE = 0
        const val LINK_TYPE = 1
    }

    private val boardName: String
    private val flagUrl: String
    private val trollUrl: String
    private val thumbUrlMap: MutableMap<Long, String>
    private var timeMap: Array<CharSequence>

    var linkClickListener: ((OutsideLink) -> Unit)? = null
    var thumbClickListener: ((ChanPost) -> Unit)? = null
    var repliesTextClickListener: ((ReplyClickEvent) -> Unit)? = null
    var goToPostListener: ((ChanPost) -> Unit)? = null

    init {
        val context: Context = MimiApplication.instance.applicationContext
        flagUrl = MimiUtil.https() + context.getString(R.string.flag_int_link)
        trollUrl = MimiUtil.https() + context.getString(R.string.flag_pol_link)
        thumbUrlMap = HashMap()

        FourChanEndpoints.Image

        boardName = thread.boardName
        timeMap = Array(replies.size) {
            val post = replies[it]

            val dateString = DateUtils.getRelativeTimeSpanString(
                    post.time * 1000L,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE)

            if (MediaContract.hasRemoteMedia(post)) {
                thumbUrlMap[post.no] = if (post is ArchivedChanPost)
                    post.thumbLink
                            ?: MimiUtil.https() + context.getString(R.string.thumb_link) + context.getString(R.string.thumb_path, boardName, post.tim)
                else
                    MimiUtil.https() + context.getString(R.string.thumb_link) + context.getString(R.string.thumb_path, boardName, post.tim)
            }

            dateString
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position >= replies.size) {
            LINK_TYPE
        } else {
            POST_TYPE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RepliesViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view: View
        return if (viewType == POST_TYPE) {
            view = inflater.inflate(R.layout.reply_post_item, parent, false)
            ChanPostViewHolder(view, thumbClickListener, repliesTextClickListener, goToPostListener)
        } else {
            view = inflater.inflate(R.layout.reply_link_item, parent, false)
            LinkViewHolder(view, linkClickListener)
        }
    }

    override fun getItemCount(): Int {
        return replies.size + links.size
    }

    override fun onBindViewHolder(holder: RepliesViewHolder, position: Int) {
        if (replies.isEmpty()) {
            return
        }

        if (holder is ChanPostViewHolder) {
            val postItem = replies[position]
            val time = timeMap[position]
            val thumbUrl = thumbUrlMap[postItem.no]
            holder.bind(PostData(postItem, time, thumbUrl))
        } else if (holder is LinkViewHolder) {
            val pos = position - replies.size
            val link = links[pos]
            holder.bind(link)
        }
    }
}

class ChanPostViewHolder(private val v: View,
                         private val thumbClickListener: ((ChanPost) -> Unit)?,
                         private val repliesTextClickListener: ((ReplyClickEvent) -> Unit)?,
                         private val goToPostListener: ((ChanPost) -> Unit)?) : RepliesViewHolder(v) {
    companion object {
        val LOG_TAG = ChanPostViewHolder::class.java.simpleName
    }

    var threadId: TextView = v.findViewById(R.id.thread_id)
    var thumbnailContainer: ViewGroup = v.findViewById(R.id.thumbnail_container)
    var userName: TextView = v.findViewById(R.id.user_name)
    var postTime: TextView = v.findViewById(R.id.timestamp)
    var userId: TextView = v.findViewById(R.id.user_id)
    var tripCode: TextView = v.findViewById(R.id.tripcode)

    //    var subject: TextView = v.findViewById(R.id.subject)
    var comment: TextView = v.findViewById(R.id.comment)
    var thumbUrl: ImageView = v.findViewById(R.id.thumbnail)
    var menuButton: ImageView? = null
    var postContainer: ViewGroup? = null
    var gotoPost: TextView = v.findViewById(R.id.goto_post)
    var flagIcon: ImageView = v.findViewById(R.id.flag_icon)
    var repliesText: TextView = v.findViewById(R.id.replies_number)

    init {
        comment.movementMethod = LongClickLinkMovementMethod.getInstance()
    }

    override fun bind(item: Any) {
        if (item is PostData) {
            val postItem = item.post
            val country: String?
            val flagUrl: String?
            if (postItem.country == null) {
                country = postItem.trollCountry
                flagUrl = if (country != null) {
                    FourChanEndpoints.Troll + country.lowercase(Locale.ROOT) + ".gif"
                } else {
                    null
                }
            } else {
                country = postItem.country
                flagUrl = if (country != null) {
                    FourChanEndpoints.Flag + country.lowercase(Locale.ROOT) + ".gif"
                } else {
                    null
                }
            }

            if (country != null) {
                Log.i(LOG_TAG, "flag url=$flagUrl")
                flagIcon.visibility = View.VISIBLE
                MimiUtil.loadImageWithFallback(v.context, flagIcon, flagUrl, null, R.drawable.placeholder_image, null)
            } else {
                flagIcon.visibility = View.GONE
            }

            gotoPost.setOnClickListener {
                goToPostListener?.invoke(postItem)
            }

            threadId.text = postItem.no.toString()
            userName.text = postItem.displayedName
            postTime.text = item.time
            repliesText.text = v.resources.getQuantityString(R.plurals.replies_plural, postItem.repliesFrom.size, postItem.repliesFrom.size)
            if (postItem.repliesFrom.isEmpty()) {
                repliesText.setOnClickListener(null)
            } else {
                repliesText.setOnClickListener {
                    repliesTextClickListener?.invoke(ReplyClickEvent(postItem))
                }
            }

            comment.text = postItem.comment ?: ""

            if (item.thumbUrl != null) {
                thumbnailContainer.visibility = View.VISIBLE
                GlideApp.with(v.context)
                        .load(item.thumbUrl)
                        .placeholder(R.drawable.placeholder_image)
                        .into(thumbUrl)

                thumbnailContainer.setOnClickListener {
                    thumbClickListener?.invoke(postItem)
                }
            } else {
                thumbnailContainer.visibility = View.GONE
                GlideApp.with(v.context).clear(thumbUrl)
            }
        }

    }

}

class LinkViewHolder(itemView: View, private val clickListener: ((OutsideLink) -> Unit)?) : RepliesViewHolder(itemView) {
    private val linkText: TextView = itemView.findViewById(R.id.link_text)

    @SuppressLint("SetTextI18n")
    override fun bind(item: Any) {
        if (item is OutsideLink) {
            if (!TextUtils.isEmpty(item.threadId)) {
                linkText.text = "/${item.boardName}/${item.threadId}"
            } else {
                linkText.text = "/${item.boardName}/"
            }
            itemView.setOnClickListener {
                clickListener?.invoke(item)
            }
        }
    }

}

abstract class RepliesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(item: Any)
}

data class PostData(val post: ChanPost, val time: CharSequence?, val thumbUrl: String?)
