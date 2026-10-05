package com.emogoth.android.phone.mimi.adapter

import android.app.Activity
import android.content.*
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.view.*
import android.widget.*
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.collection.LongSparseArray
import androidx.core.content.res.ResourcesCompat
import androidx.core.util.Pair
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.GalleryActivity2.Companion.start
import com.emogoth.android.phone.mimi.app.MimiApplication.Companion.instance
import com.emogoth.android.phone.mimi.async.ProcessThreadTask
import com.emogoth.android.phone.mimi.db.UserPostTableConnection.addPost
import com.emogoth.android.phone.mimi.db.UserPostTableConnection.removePost
import com.emogoth.android.phone.mimi.dialog.RepliesDialog
import com.emogoth.android.phone.mimi.dialog.RepliesDialog.Companion.newInstance
import com.emogoth.android.phone.mimi.interfaces.ReplyMenuClickListener
import com.emogoth.android.phone.mimi.interfaces.ThumbnailClickListener
import com.emogoth.android.phone.mimi.util.GlideApp
import com.emogoth.android.phone.mimi.util.MimiPrefs.Companion.imageSpoilersEnabled
import com.emogoth.android.phone.mimi.util.MimiUtil
import com.emogoth.android.phone.mimi.util.MediaContract
import com.emogoth.android.phone.mimi.view.LongClickLinkMovementMethod
import com.emogoth.android.phone.mimi.view.gallery.GalleryPagerAdapter.Companion.getPostsWithImages
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import com.mimireader.chanlib.models.ArchivedChanPost
import com.mimireader.chanlib.models.ArchivedChanThread
import com.mimireader.chanlib.models.ChanPost
import com.mimireader.chanlib.models.ChanThread
import com.mimireader.chanlib.util.ChanUtil
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.*

class ThreadListAdapter(thread: ChanThread, fragmentManager: FragmentManager) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), Filterable, RecyclerViewFastScroller.OnPopupTextUpdate {
    private val flagUrl: String
    private val trollUrl: String
    private val pinDrawable: VectorDrawableCompat?
    private val lockDrawable: VectorDrawableCompat?
    private val fragmentManager: FragmentManager
    private val items: MutableList<ChanPost> = ArrayList()
    private val userPosts: MutableList<Long> = ArrayList()
    private var boardName: String
    private var threadId: Long
    private lateinit var timeMap: Array<CharSequence?>
    private var thumbnailClickListener: ThumbnailClickListener? = null
    private var replyMenuClickListener: ReplyMenuClickListener? = null
    private var defaultPostBackground = 0
    private var highlightPostBackground = 0
    private var highlightTextBackground = 0
    private var selectedTextBackground = 0
    private var lastPosition = 0

    //    private List<Long> userPostList;
    private val repliesText: MutableList<String> = ArrayList()
    private val imagesText: MutableList<String> = ArrayList()
    private lateinit var colorList: IntArray
    private val thumbUrlMap = LongSparseArray<String>()
    private val fullImageUrlMap = LongSparseArray<String>()
    private var foundPosts: LinkedHashMap<Long, TextSearchResult> = LinkedHashMap()
    private var postFilter: PostFilter? = null
    private var foundPos = FIND_NO_RESULTS
    private var filterUpdateCallback: OnFilterUpdateCallback? = null
    private val imageSpoilersEnabled: Boolean
    private val customSpoilerUrl: String
    private val spoilerUrl: String
    private fun setupThread() {
        timeMap = arrayOfNulls(items.size)
        colorList = IntArray(items.size)
        //        this.userPostList = ThreadRegistry.getInstance().getUserPosts(boardName, threadId);
        val context = instance.applicationContext
        for (i in items.indices) {
            val post = items[i]
            val dateString = DateUtils.getRelativeTimeSpanString(
                    post.time * 1000L,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE)
            if (MediaContract.hasRemoteMedia(post)) {
                thumbUrlMap.put(post.no, MimiUtil.https() + context.getString(R.string.thumb_link) + context.getString(R.string.thumb_path, boardName, post.tim))
                fullImageUrlMap.put(post.no, MimiUtil.https() + context.getString(R.string.image_link) + context.getString(R.string.full_image_path, boardName, post.tim, MediaContract.normalizedExtension(post.ext)))
            }
            repliesText.add(context.resources.getQuantityString(R.plurals.replies_plural, post.repliesFrom.size, post.repliesFrom.size))
            imagesText.add(context.resources.getQuantityString(R.plurals.image_plural, post.images, post.images))
            timeMap[i] = dateString
            if (!TextUtils.isEmpty(post.id)) {
                colorList[i] = ChanUtil.calculateColorBase(post.id)
            }
            if (post.fsize > 0 && MediaContract.hasRemoteMedia(post)) {
                post.humanReadableFileSize = MimiUtil.humanReadableByteCount(post.fsize.toLong(), true) + " " + MediaContract.normalizedExtension(post.ext).substring(1).toUpperCase(Locale.getDefault())
            }
        }
    }

    private fun initMetadataDrawables(): Pair<VectorDrawableCompat?, VectorDrawableCompat?> {
        val theme = instance.theme
        val res = instance.resources
        val drawableColor = if (MimiUtil.getInstance().theme == MimiUtil.THEME_LIGHT) R.color.md_grey_800 else R.color.md_green_50
        val pin: VectorDrawableCompat?
        val lock: VectorDrawableCompat?
        pin = VectorDrawableCompat.create(res, R.drawable.ic_pin, theme)
        lock = VectorDrawableCompat.create(res, R.drawable.ic_lock, theme)
        pin?.setTint(res.getColor(drawableColor, res.newTheme()))
        lock?.setTint(res.getColor(drawableColor, res.newTheme()))
        return Pair.create(pin, lock)
    }

    val nextFoundStringPosition: Int
        get() {
            if (foundPosts.size == 0) {
                return FIND_NO_RESULTS
            }
            foundPos = if (foundPos == foundPosts.size - 1) 0 else foundPos + 1
            return getListPositionFromResultPosition(foundPos)
        }
    val prevFoundStringPosition: Int
        get() {
            if (foundPosts.size == 0) {
                return FIND_NO_RESULTS
            }
            foundPos = if (foundPos == 0) foundPosts.size - 1 else foundPos - 1
            return getListPositionFromResultPosition(foundPos)
        }

    private fun getListPositionFromResultPosition(pos: Int): Int {
        if (pos < 0) {
            return -1
        }
        val resultList: List<Long> = ArrayList(foundPosts.keys)
        val foundPostId = resultList[pos]
        return MimiUtil.findPostPositionById(foundPostId, items)
    }

    fun setUserPosts(posts: List<Long>) {
        userPosts.clear()
        userPosts.addAll(posts)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v: View
        return when (viewType) {
            VIEW_TOP_LIST_ITEM -> {
                v = LayoutInflater.from(parent.context).inflate(R.layout.thread_first_post_item, parent, false)
                FirstPostViewHolder(v)
            }
            VIEW_NORMAL_LIST_ITEM -> {
                v = LayoutInflater.from(parent.context).inflate(R.layout.thread_post_item, parent, false)
                ThreadPostViewHolder(v)
            }
            else -> {
                v = LayoutInflater.from(parent.context).inflate(R.layout.header_layout, parent, false)
                ThreadPostViewHolder(v)
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemCount(): Int {
        return items.size
    }

    private fun reset(posts: List<ChanPost>) {
        thumbUrlMap.clear()
        fullImageUrlMap.clear()
        repliesText.clear()
        imagesText.clear()
        items.clear()
        items.addAll(posts)
        setupThread()
    }

    fun setThread(thread: ChanThread) {
        if (TextUtils.isEmpty(boardName) || threadId <= 0) {
            boardName = thread.boardName
            threadId = thread.threadId
            reset(thread.posts)
            notifyDataSetChanged()
            return
        }
        if (thread.posts.size > items.size && items.size > 1 || thread is ArchivedChanThread) {
            reset(thread.posts)
            notifyDataSetChanged()
        } else if (items.size <= 1) {
            reset(thread.posts)
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            VIEW_TOP_LIST_ITEM
        } else {
            VIEW_NORMAL_LIST_ITEM
        }
    }

    override fun onBindViewHolder(vh: RecyclerView.ViewHolder, position: Int) {
        val startTime = System.currentTimeMillis()
        val viewHolder = vh as ViewHolder
        val postItem = items[position] ?: return
        if (lastPosition < position) {
            lastPosition = position
        }
        val searchResult = getSearchResultByPostId(postItem.no)
        //        final int postPosition = getListPositionFromResultPosition(foundPos);
//        final boolean selected = foundPos >= 0 && position == postPosition;
        // setting this dynamically causes the app to crash
        val selected = false
        val usersPost = userPosts.contains(postItem.no)
        if (viewHolder.postContainer != null) {
            if (usersPost) {
                viewHolder.postContainer?.setBackgroundResource(highlightPostBackground)
            } else {
                viewHolder.postContainer?.setBackgroundResource(defaultPostBackground)
            }
        }
        viewHolder.thumbnailContainer.setOnClickListener { v: View? ->
            if (thumbnailClickListener != null) {
                val postsWithImages = getPostsWithImages(items)
                val position1 = postsWithImages.indexOf(postItem)
                thumbnailClickListener?.onThumbnailClick(postsWithImages, threadId, position1, boardName)
            }
        }
        val thumbUrl: String?
        val imageUrl: String?
        if (items[position] is ArchivedChanPost) {
            thumbUrl = (postItem as ArchivedChanPost).thumbLink
            imageUrl = postItem.mediaLink
        } else {
            thumbUrl = if (!imageSpoilersEnabled) {
                thumbUrlMap[postItem.no]
            } else if (items[position].spoiler > 0 && items[position].customSpoiler > 0) {
                String.format(customSpoilerUrl, boardName, items[position].customSpoiler)
            } else if (items[position].spoiler > 0) {
                spoilerUrl
            } else {
                thumbUrlMap[postItem.no]
            }
            imageUrl = fullImageUrlMap[postItem.no]
        }
        val country: String?
        val url: String?
        if (postItem.country == null) {
            country = postItem.trollCountry
            url = if (country != null) {
                trollUrl + country.toLowerCase(Locale.getDefault()) + ".gif"
            } else {
                null
            }
        } else {
            country = postItem.country
            url = if (country != null) {
                flagUrl + country.toLowerCase(Locale.getDefault()) + ".gif"
            } else {
                null
            }
        }

        val flagIcon = viewHolder.flagIcon
        if (flagIcon != null) {
            if (country != null) {
                flagIcon.visibility = View.VISIBLE
                flagIcon.setOnClickListener { v: View ->
                    val builder = MaterialAlertDialogBuilder(v.context)
                    builder.setMessage(postItem.countryName)
                            .setCancelable(true)
                            .show()
                            .setCanceledOnTouchOutside(true)
                }
                Glide.with(flagIcon as View)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(flagIcon)
            } else {
                flagIcon.visibility = View.GONE
                flagIcon.setOnClickListener(null)
                GlideApp.with(flagIcon as View).clear(flagIcon)
            }
        }

        val lockIcon = viewHolder.lockIcon
        if (lockIcon != null) {
            if (postItem.isClosed) {
                lockIcon.setImageDrawable(lockDrawable)
                lockIcon.visibility = View.VISIBLE
            } else {
                lockIcon.visibility = View.GONE
            }
        }

        val pinIcon = viewHolder.pinIcon
        if (pinIcon != null) {
            if (postItem.isSticky) {
                pinIcon.setImageDrawable(pinDrawable)
                pinIcon.visibility = View.VISIBLE
            } else {
                pinIcon.visibility = View.GONE
            }
        }

        viewHolder.menuButton.setOnClickListener { v: View ->
            val menu = PopupMenu(v.context, v)
            if (position == 0) {
                menu.inflate(R.menu.thread_first_post_menu)
            } else {
                menu.inflate(R.menu.thread_menu)
            }
            val claimMenuItem = menu.menu.findItem(R.id.claim_menu_item)
            val unclaimMenuItem = menu.menu.findItem(R.id.unclaim_menu_item)
            claimMenuItem.isVisible = !usersPost
            unclaimMenuItem.isVisible = usersPost
            val imageInfoItem = menu.menu.findItem(R.id.image_info_menu_item)
            if (imageUrl == null && imageInfoItem != null) {
                imageInfoItem.isVisible = false
            }
            menu.setOnMenuItemClickListener { menuItem: MenuItem ->
                val context = instance.applicationContext
                val boardLinkUrl = "https://${context.getString(R.string.board_link)}"
                val path = context.getString(R.string.raw_thread_path, boardName, threadId)
                val link: String
                link = if (postItem.no == threadId) {
                    boardLinkUrl + path
                } else {
                    boardLinkUrl + path + "#q" + postItem.no
                }
                when (menuItem.itemId) {
                    R.id.reply_menu_item -> {
                        if (replyMenuClickListener != null) {
                            replyMenuClickListener?.onReply(v, postItem.no)
                        }
                        return@setOnMenuItemClickListener true
                    }
                    R.id.quote_menu_item -> {
                        if (replyMenuClickListener != null) {
                            replyMenuClickListener?.onQuote(v, postItem)
                        }
                        return@setOnMenuItemClickListener true
                    }
                    R.id.claim_menu_item -> {
                        setPostOwner(postItem, true)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.unclaim_menu_item -> {
                        setPostOwner(postItem, false)
                        return@setOnMenuItemClickListener true
                    }
                    R.id.copy_text -> {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("hread info", items[position].comment)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener true
                    }
                    R.id.image_info_menu_item -> {
                        if (viewHolder.menuButton.context is Activity) {
                            val activity = viewHolder.menuButton.context as Activity
                            createImageInfoDialog(activity, postItem, imageUrl)
                        }
                        return@setOnMenuItemClickListener true
                    }
                    R.id.report_menu -> {
                        if (viewHolder.menuButton.context is Activity) {
                            val activity = viewHolder.menuButton.context as Activity
                            val reportUrl = "https://" + context.getString(R.string.sys_link)
                            val reportPath = context.getString(R.string.report_path, boardName, items[position].no)
                            val reportIntent = Intent(Intent.ACTION_VIEW, Uri.parse(reportUrl + reportPath))
                            activity.startActivity(reportIntent)
                        }
                        return@setOnMenuItemClickListener true
                    }
                    R.id.copy_link_menu -> {
                        val linkClipboard = instance.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val linkClip = ClipData.newPlainText("thread link", link)
                        linkClipboard.setPrimaryClip(linkClip)
                        Toast.makeText(context, R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                        return@setOnMenuItemClickListener true
                    }
                    R.id.open_link_menu -> {
                        if (viewHolder.menuButton.context is Activity) {
                            val activity = viewHolder.menuButton.context as Activity
                            val openLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                            activity.startActivity(openLinkIntent)
                        }
                        return@setOnMenuItemClickListener true
                    }
                    else -> return@setOnMenuItemClickListener false
                }
            }
            menu.show()
        }
        viewHolder.threadId.text = postItem.no.toString()
        viewHolder.userName.text = postItem.displayedName
        viewHolder.postTime.text = timeMap[position]
        if (!TextUtils.isEmpty(postItem.subject)) {
            val data: MutableList<Pair<Int, Int>>? = searchResult.textLocation[TextLocation.LOCATION_SUBJECT]
            if (data != null) {
                val span = SpannableStringBuilder(postItem.subject)
                for (integerIntegerPair in data) {
                    span.setSpan(BackgroundColorSpan(if (selected) selectedTextBackground else highlightTextBackground), integerIntegerPair.first, integerIntegerPair.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                viewHolder.subject.text = span
            } else {
                viewHolder.subject.text = postItem.subject
            }
            viewHolder.subject.visibility = View.VISIBLE
        } else {
            viewHolder.subject.visibility = View.GONE
        }
        if (!TextUtils.isEmpty(postItem.id)) {
            viewHolder.userId.setBackgroundColor(colorList[position])
            viewHolder.userId.text = postItem.id
            viewHolder.userId.visibility = View.VISIBLE
            viewHolder.userId.setOnClickListener { v: View ->
                if (v.context is AppCompatActivity) {
                    newInstance(ChanThread(boardName, threadId, items), postItem.id ?: "").show(fragmentManager, RepliesDialog.DIALOG_TAG)
                }
            }
        } else {
            viewHolder.userId.visibility = View.GONE
        }
        if (!TextUtils.isEmpty(postItem.trip)) {
            viewHolder.tripCode.text = postItem.trip
            viewHolder.tripCode.visibility = View.VISIBLE
        } else {
            viewHolder.tripCode.visibility = View.GONE
        }
        if (postItem.comment != null) {
            val data: List<Pair<Int, Int>>? = searchResult.textLocation[TextLocation.LOCATION_COMMENT]
            if (data != null) {
                val span = SpannableStringBuilder(postItem.comment)
                for (integerIntegerPair in data) {
                    span.setSpan(BackgroundColorSpan(if (selected) selectedTextBackground else highlightTextBackground), integerIntegerPair.first, integerIntegerPair.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                viewHolder.comment.text = span
            } else {
                viewHolder.comment.text = postItem.comment
            }
            viewHolder.comment.customSelectionActionModeCallback = object : ActionMode.Callback {
                val QUOTE_MENU_ID = 10109
                override fun onCreateActionMode(actionMode: ActionMode, menu: Menu): Boolean {
                    menu.add(1, QUOTE_MENU_ID, Menu.NONE, R.string.quote_menu_item).setIcon(R.drawable.ic_content_new)
                    return true
                }

                override fun onPrepareActionMode(actionMode: ActionMode, menu: Menu): Boolean {
                    return false
                }

                override fun onActionItemClicked(actionMode: ActionMode, menuItem: MenuItem): Boolean {
                    if (menuItem.itemId == QUOTE_MENU_ID) {
                        val start = viewHolder.comment.selectionStart
                        val end = viewHolder.comment.selectionEnd
                        replyMenuClickListener?.onQuoteSelection(viewHolder.comment, postItem, start, end)
                    }
                    actionMode.finish()
                    return true
                }

                override fun onDestroyActionMode(actionMode: ActionMode) {}
            }
        } else {
            viewHolder.comment.text = ""
        }
        if (viewHolder.replyButton != null) {
            if (postItem.isClosed || items[position] is ArchivedChanPost) {
                viewHolder.replyButton?.visibility = View.GONE
            } else {
                viewHolder.replyButton?.visibility = View.VISIBLE
                viewHolder.replyButton?.setOnClickListener { v: View? ->
                    if (replyMenuClickListener != null) {
                        replyMenuClickListener?.onReply(v, postItem.no)
                    }
                }
            }
        }
        if (postItem.repliesFrom.size > 0 || viewHolder.galleryImageCount != null && postItem.images > 0) {
            viewHolder.replyCount.text = repliesText[position]
            viewHolder.replyCount.setOnClickListener { v: View ->
                if (v.context is AppCompatActivity) {
                    val activity = viewHolder.menuButton.context as AppCompatActivity
                    newInstance(ChanThread(boardName, threadId, items), postItem).show(fragmentManager, RepliesDialog.DIALOG_TAG)
                }
            }
            if (viewHolder.galleryImageCount != null) {
                viewHolder.galleryImageCount?.text = imagesText[position]
                viewHolder.galleryImageCount?.setOnClickListener { v: View -> start(v.context, 0, postItem.no, boardName, postItem.no, LongArray(0)) }
                viewHolder.galleryImageCount?.visibility = View.VISIBLE
            }
            viewHolder.replyCount.visibility = View.VISIBLE
        } else {
            viewHolder.replyCount.visibility = View.INVISIBLE
            if (viewHolder.galleryImageCount != null) {
                viewHolder.galleryImageCount?.visibility = View.INVISIBLE
            }
        }
        if (viewHolder.replyContainer != null) {
            if (viewHolder.replyCount.visibility != View.VISIBLE && viewHolder.galleryImageCount == null) {
                viewHolder.replyContainer?.visibility = View.GONE
            } else {
                viewHolder.replyContainer?.visibility = View.VISIBLE
            }
        }
        viewHolder.thumbnailContainer.visibility = View.INVISIBLE
        if (postItem.filename != null && postItem.filename != "") {
            if (viewHolder.thumbnailInfoContainer != null) {
                viewHolder.thumbnailInfoContainer?.visibility = View.VISIBLE
                val info: String
                info = if (postItem.ext != null) {
                    (MimiUtil.humanReadableByteCount(postItem.fsize.toLong(), true)
                            + " "
                            + postItem.ext?.toUpperCase(Locale.getDefault())?.substring(1))
                } else {
                    MimiUtil.humanReadableByteCount(postItem.fsize.toLong(), true)
                }
                viewHolder.fileExt.text = info
            }
            viewHolder.thumbnailContainer.visibility = View.VISIBLE
            GlideApp.with(viewHolder.thumbnailContainer)
                    .load(thumbUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(viewHolder.thumbUrl)
        } else {
            viewHolder.thumbnailContainer.visibility = View.GONE
            if (viewHolder.thumbnailInfoContainer != null) {
                viewHolder.thumbnailInfoContainer?.visibility = View.GONE
            }
            Glide.with(viewHolder.thumbUrl.context).clear(viewHolder.thumbUrl)
        }
        val endTime = System.currentTimeMillis()
        val delta = endTime - startTime
        Log.d(LOG_TAG, "onBindViewHolder took " + delta + "ms")
    }

    private fun setPostOwner(post: ChanPost, claim: Boolean) {
        val id = post.no
        if (claim) {
            addPost(boardName, threadId, id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object : SingleObserver<Boolean> {
                        override fun onSubscribe(d: Disposable) {
                            // no op
                        }

                        override fun onSuccess(success: Boolean) {
                            if (!success) {
                                onError(Exception("Error claiming thread. Please try again."))
                                return
                            }
                            userPosts.add(id)
                            processPostReplies(post)
                        }

                        override fun onError(e: Throwable) {
                            Log.e(LOG_TAG, "Error claiming thread", e)
                            Toast.makeText(instance.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
                        }
                    })
        } else {
            removePost(boardName, threadId, id)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object : SingleObserver<Boolean> {
                        override fun onSubscribe(d: Disposable) {
                            // no op
                        }

                        override fun onSuccess(success: Boolean) {
                            if (!success) {
                                onError(Exception("Error unclaiming thread. Please try again."))
                                return
                            }
                            val index = userPosts.indexOf(id)
                            if (index >= 0) {
                                userPosts.removeAt(index)
                                processPostReplies(post)
                            } else {
                                onError(Exception("Error unclaiming thread. Please try again."))
                            }
                        }

                        override fun onError(e: Throwable) {
                            Log.e(LOG_TAG, "Error unclaiming thread", e)
                            Toast.makeText(instance.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
                        }
                    })
        }
    }

    private fun processPostReplies(post: ChanPost) {
        val postPosition = MimiUtil.findPostPositionById(post.no, items)
        for (reply in post.repliesFrom) {
            val i = MimiUtil.findPostPositionById(reply.no, items)
            if (i >= 0) {
                val p = ProcessThreadTask.updatePost(items[i], userPosts, boardName, 0)
                items[i] = p
                notifyItemChanged(i)
            }
        }
        notifyItemChanged(postPosition)
    }

    private fun createImageInfoDialog(activity: Activity, postItem: ChanPost, imageUrl: String?) {
        val inflater = LayoutInflater.from(activity)
        val root = inflater.inflate(R.layout.dialog_image_info_buttons, null, false)
        val fileName = root.findViewById<View>(R.id.file_name) as TextView
        val dimensions = root.findViewById<View>(R.id.dimensions) as TextView
        val iqdb = root.findViewById<View>(R.id.iqdb_button)
        val google = root.findViewById<View>(R.id.google_button)
        val saucenao = root.findViewById<View>(R.id.saucenao_button)
        val yandex = root.findViewById<View>(R.id.yandex_button)
        val builder = MaterialAlertDialogBuilder(activity)
        val dialog: AlertDialog
        val fileNameWithExtension = postItem.filename + postItem.ext
        fileName.text = fileNameWithExtension
        val fileDimensions = "${postItem.width} x ${postItem.height}"
        dimensions.text = fileDimensions
        builder.setTitle(R.string.image_info)
                .setView(root)
                .setPositiveButton(R.string.exit) { dialog1: DialogInterface, which: Int -> dialog1.dismiss() }
        dialog = builder.create()
        iqdb.setOnClickListener { v: View? -> openImageSearch(activity, R.string.iqdb_image_search_link, imageUrl, dialog) }
        google.setOnClickListener { v: View? -> openImageSearch(activity, R.string.google_image_search_link, imageUrl, dialog) }
        saucenao.setOnClickListener { v: View? -> openImageSearch(activity, R.string.saucenao_image_search_link, imageUrl, dialog) }
        yandex.setOnClickListener { v: View? -> openImageSearch(activity, R.string.yandex_image_search_link, imageUrl, dialog) }
        dialog.show()
    }

    private fun openImageSearch(act: Activity?, @StringRes baseSearchUrl: Int, imageUrl: String?, dialog: DialogInterface) {
        if (act != null) {
            val url: String
            try {
                url = act.getString(baseSearchUrl, URLEncoder.encode(imageUrl, "UTF-8"))
                val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                act.startActivity(searchIntent)
                dialog.dismiss()
            } catch (e: UnsupportedEncodingException) {
                Toast.makeText(act, R.string.error_opening_search_link, Toast.LENGTH_SHORT).show()
                Log.e(LOG_TAG, "Error opening search link", e)
            }
        }
    }

    fun clearFilter() {
        foundPosts.clear()
        foundPos = FIND_NO_RESULTS
        notifyDataSetChanged()
        if (filterUpdateCallback != null) {
            filterUpdateCallback?.onFilterUpdated("", 0)
        }
    }

    val filterCount: Int
        get() = foundPosts?.size ?: 0

    override fun getFilter(): Filter {
        if (postFilter == null) {
            postFilter = PostFilter(items)
        }
        val pf = postFilter
        return pf as Filter
    }

    private inner class PostFilter(private val posts: List<ChanPost>) : Filter() {
        override fun performFiltering(searchStr: CharSequence): FilterResults {
            Log.d(LOG_TAG, "Filtering on: $searchStr")
            val startTime = System.currentTimeMillis()
            val results = FilterResults()
            if (searchStr.isEmpty()) {
                results.count = posts.size
                results.values = LinkedHashMap<Int, TextSearchResult>()
            } else {
                val constraint = searchStr.toString().toLowerCase(Locale.getDefault())
                val resultMap = LinkedHashMap<Long, TextSearchResult>()
                for (chanPost in items) {
                    val result = TextSearchResult()
                    result.searchStr = constraint
                    result.postId = chanPost.no
                    var textLocation: TextLocation
                    var start = 0
                    var end = 0
                    while (start > -1) {
                        if (chanPost.name != null && chanPost.name?.toLowerCase(Locale.getDefault())?.substring(end)?.contains(constraint) == true) {
                            start = chanPost.name?.toLowerCase(Locale.getDefault())?.indexOf(result.searchStr, end) ?: -1
                            textLocation = TextLocation.LOCATION_NAME
                            end = start + constraint.length
                            var data = result.textLocation[textLocation]
                            if (data == null) {
                                data = ArrayList()
                            }
                            data.add(Pair(start, end))
                            result.textLocation[textLocation] = data
                        } else {
                            start = -1
                        }
                    }
                    start = 0
                    end = 0
                    while (start > -1) {
                        if (chanPost.subject != null && chanPost.subject.toString().toLowerCase(Locale.getDefault()).substring(end).contains(constraint)) {
                            start = chanPost.subject.toString().toLowerCase(Locale.getDefault()).indexOf(result.searchStr, end)
                            textLocation = TextLocation.LOCATION_SUBJECT
                            end = start + constraint.length
                            var data = result.textLocation[textLocation]
                            if (data == null) {
                                data = ArrayList()
                            }
                            data.add(Pair(start, end))
                            result.textLocation[textLocation] = data
                        } else {
                            start = -1
                        }
                    }
                    start = 0
                    end = 0
                    while (start > -1) {
                        if (chanPost.comment != null && chanPost.comment.toString().toLowerCase(Locale.getDefault()).substring(end).contains(constraint)) {
                            start = chanPost.comment.toString().toLowerCase(Locale.getDefault()).indexOf(result.searchStr, end)
                            textLocation = TextLocation.LOCATION_COMMENT
                            end = start + constraint.length
                            var data = result.textLocation[textLocation]
                            if (data == null) {
                                data = ArrayList()
                            }
                            data.add(Pair(start, end))
                            result.textLocation[textLocation] = data
                        } else {
                            start = -1
                        }
                    }
                    if (result.textLocation.isNotEmpty()) {
                        resultMap[chanPost.no] = result
                    }
                }
                results.count = resultMap.size
                results.values = resultMap
            }
            val endTime = System.currentTimeMillis()
            val delta = endTime - startTime
            Log.d(LOG_TAG, "Filtering took " + delta + "ms")
            return results
        }

        override fun publishResults(constraint: CharSequence, results: FilterResults) {
            val startTime = System.currentTimeMillis()
            val textSearchResults = results.values as LinkedHashMap<Long, TextSearchResult>
            foundPos = if (textSearchResults.size > 0) {
                -1
            } else {
                FIND_NO_RESULTS
            }
            foundPosts = LinkedHashMap(textSearchResults)
            val endTime = System.currentTimeMillis()
            val delta = endTime - startTime
            Log.d(LOG_TAG, "Publishing results took " + delta + "ms")
            notifyDataSetChanged()
            if (filterUpdateCallback != null) {
                filterUpdateCallback?.onFilterUpdated(constraint.toString(), foundPosts?.size ?: 0)
            }
        }

//        init {
//            if (posts == null) {
//                posts = ArrayList()
//            }
//        }
    }

    private fun getSearchResultByPostId(postId: Long): TextSearchResult {
        return if (foundPosts.size == 0) {
            TextSearchResult()
        } else {
            val fp = foundPosts
            val result = fp[postId]
            fp[postId] ?: return TextSearchResult()
        }
    }

    enum class TextLocation {
        LOCATION_NAME, LOCATION_SUBJECT, LOCATION_COMMENT, NONE
    }

    inner class TextSearchResult {
        var searchStr = ""
        var postId = -1L
        var textLocation: MutableMap<TextLocation, MutableList<Pair<Int, Int>>> = EnumMap(TextLocation::class.java)
        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false
            val result = o as TextSearchResult
            return postId == result.postId
        }

        override fun hashCode(): Int {
            return (postId xor (postId ushr 32)).toInt()
        }
    }

    abstract class ViewHolder(root: View) : RecyclerView.ViewHolder(root) {
        var postContainer: ViewGroup? = root.findViewById(R.id.post_container)

        @JvmField
        var threadId: TextView = root.findViewById(R.id.thread_id)

        @JvmField
        var thumbnailContainer: ViewGroup = root.findViewById(R.id.thumbnail_container)
        var thumbnailInfoContainer: ViewGroup? = root.findViewById(R.id.thumbnail_info_container)

        @JvmField
        var userName: TextView = root.findViewById(R.id.user_name)

        @JvmField
        var postTime: TextView = root.findViewById(R.id.timestamp)
        var userId: TextView = root.findViewById(R.id.user_id)
        var tripCode: TextView = root.findViewById(R.id.tripcode)
        var subject: TextView = root.findViewById(R.id.subject)

        @JvmField
        var comment: TextView = root.findViewById(R.id.comment)
        var replyCount: TextView = root.findViewById(R.id.replies_number)
        var replyButton: TextView? = root.findViewById(R.id.reply_button)
        var galleryImageCount: TextView? = root.findViewById(R.id.image_count)
        var thumbUrl: ImageView = root.findViewById(R.id.thumbnail)
        var fileExt: TextView = root.findViewById(R.id.file_ext)
        var menuButton: View = root.findViewById(R.id.menu_button)
        var replyContainer: View? = root.findViewById(R.id.replies_row)

        @JvmField
        var flagIcon: ImageView? = root.findViewById(R.id.flag_icon)
        var lockIcon: ImageView? = root.findViewById(R.id.lock_icon)
        var pinIcon: ImageView? = root.findViewById(R.id.pin_icon)

        init {
            comment.movementMethod = LongClickLinkMovementMethod.getInstance()
        }
    }

    private class FirstPostViewHolder(root: View) : ViewHolder(root)
    class ThreadPostViewHolder(root: View) : ViewHolder(root)

    fun setOnThumbnailClickListener(listener: ThumbnailClickListener?) {
        thumbnailClickListener = listener
    }

    fun setOnReplyMenuClickListener(listener: ReplyMenuClickListener?) {
        replyMenuClickListener = listener
    }

    fun setOnFilterUpdateCallback(callback: OnFilterUpdateCallback?) {
        filterUpdateCallback = callback
    }

    interface OnFilterUpdateCallback {
        fun onFilterUpdated(filteredString: String?, count: Int)
    }

    companion object {
        private val LOG_TAG = ThreadListAdapter::class.java.simpleName
        const val VIEW_TOP_LIST_ITEM = 11
        const val VIEW_NORMAL_LIST_ITEM = 12
        private const val FIND_NO_RESULTS = -2
    }

    init {
        items.addAll(thread.posts)
        boardName = thread.boardName
        threadId = thread.threadId
        val context = instance.applicationContext
        this.fragmentManager = fragmentManager
        flagUrl = MimiUtil.https() + context.getString(R.string.flag_int_link)
        trollUrl = MimiUtil.https() + context.getString(R.string.flag_pol_link)
        if (MimiUtil.getInstance().theme == MimiUtil.THEME_LIGHT) {
            defaultPostBackground = R.color.row_item_background_light
            highlightPostBackground = R.color.post_highlight_light
            highlightTextBackground = ResourcesCompat.getColor(context.resources, R.color.text_highlight_background_light, context.theme)
            selectedTextBackground = ResourcesCompat.getColor(context.resources, R.color.text_select_background_light, context.theme)
        } else if (MimiUtil.getInstance().theme == MimiUtil.THEME_DARK) {
            defaultPostBackground = R.color.row_item_background_dark
            highlightPostBackground = R.color.post_highlight_dark
            highlightTextBackground = ResourcesCompat.getColor(context.resources, R.color.text_highlight_background_dark, context.theme)
            selectedTextBackground = ResourcesCompat.getColor(context.resources, R.color.text_select_background_dark, context.theme)
        } else {
            defaultPostBackground = R.color.row_item_background_black
            highlightPostBackground = R.color.post_highlight_black
            highlightTextBackground = ResourcesCompat.getColor(context.resources, R.color.text_highlight_background_black, context.theme)
            selectedTextBackground = ResourcesCompat.getColor(context.resources, R.color.text_select_background_black, context.theme)
        }
        val metadataDrawables = initMetadataDrawables()
        pinDrawable = metadataDrawables.first
        lockDrawable = metadataDrawables.second
        val appContext = instance.applicationContext
        imageSpoilersEnabled = imageSpoilersEnabled(appContext)
        spoilerUrl = MimiUtil.https() + appContext.getString(R.string.spoiler_link) + appContext.getString(R.string.spoiler_path)
        customSpoilerUrl = MimiUtil.https() + appContext.getString(R.string.spoiler_link) + appContext.getString(R.string.custom_spoiler_path)
        setupThread()
    }

    override fun onChange(position: Int): CharSequence {
        return "${position + 1} / ${items.size}"
    }
}
