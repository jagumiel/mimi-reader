package com.emogoth.android.phone.mimi.dialog

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.GalleryActivity2
import com.emogoth.android.phone.mimi.activity.PostItemDetailActivity
import com.emogoth.android.phone.mimi.activity.PostItemListActivity
import com.emogoth.android.phone.mimi.adapter.RepliesListAdapter
import com.emogoth.android.phone.mimi.db.DatabaseUtils
import com.emogoth.android.phone.mimi.db.HistoryTableConnection
import com.emogoth.android.phone.mimi.db.PostTableConnection
import com.emogoth.android.phone.mimi.db.models.History
import com.emogoth.android.phone.mimi.interfaces.GoToPostListener
import com.emogoth.android.phone.mimi.model.OutsideLink
import com.emogoth.android.phone.mimi.util.Extras
import com.emogoth.android.phone.mimi.util.RxUtil
import com.emogoth.android.phone.mimi.view.gallery.GalleryPagerAdapter
import com.emogoth.android.phone.mimi.viewmodel.ChanDataSource
import com.mimireader.chanlib.models.ChanPost
import com.mimireader.chanlib.models.ChanThread
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers

class RepliesDialogContentFragment : Fragment() {
    private var listView: RecyclerView? = null

    private var boardName: String = ""
    private var threadId: Long = 0
    private var replies: ArrayList<String> = ArrayList()
    private var outsideLinks: List<OutsideLink> = ArrayList()
    private var thread: ChanThread = ChanThread()
    private var id: Long = 0
    private var repliesSubscription: Disposable? = null
    private var loadRepliesSubscription: Disposable? = null

    var updateReplies: ((ChanPost) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.dialog_replies_content, container, false)
        listView = v.findViewById(R.id.replies_list)

        arguments?.let { extractExtras(it) }

        if (threadId > 0) {
            loadRepliesSubscription = PostTableConnection.fetchThread(threadId)
                    .map(PostTableConnection.mapDbPostsToChanThread(boardName, threadId))
                    .compose(DatabaseUtils.applySingleSchedulers())
                    .subscribe({
                        thread = it
                        loadReplies()
                    }, {
                        Toast.makeText(activity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                        Log.e("RepliesContent", "Caught exception", it)
                    })
        }

        return v
    }

    private fun loadReplies() {
        RxUtil.safeUnsubscribe(repliesSubscription)
        val dataSource = ChanDataSource()
        repliesSubscription = dataSource.watchThread(boardName, threadId)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .zipWith(HistoryTableConnection.fetchPost(boardName, threadId).toFlowable(), BiFunction { chanThread: ChanThread, history: History -> Pair(history, chanThread) })
                .flatMap { (_, second) ->
                    val posts: ArrayList<ChanPost> = ArrayList()
                    for (reply in replies) {
                        for (post in second.posts) {
                            if (post.no == reply.toInt().toLong()) {
                                posts.add(post)
                            }
                        }
                    }
                    Flowable.just(posts)
                }
                .first(ArrayList())
                .subscribe { posts: List<ChanPost> ->
                    val adapter = RepliesListAdapter(posts, outsideLinks, thread)
                    adapter.linkClickListener = { outsideLink: OutsideLink ->
                        val intent: Intent
                        val id = outsideLink.threadId
                        if (id != null && TextUtils.isDigitsOnly(id)) {
                            intent = Intent(activity, PostItemDetailActivity::class.java)
                            intent.putExtra(Extras.EXTRAS_THREAD_ID, java.lang.Long.valueOf(id))
                            intent.putExtra(Extras.EXTRAS_SINGLE_THREAD, true)
                        } else {
                            intent = Intent(activity, PostItemListActivity::class.java)
                        }
                        intent.putExtra(Extras.EXTRAS_BOARD_NAME, outsideLink.boardName)
                        activity?.startActivity(intent)
                    }
                    adapter.repliesTextClickListener = { (post) ->
                        if (post != null) {
                            updateReplies?.invoke(post)
                        }
                    }
                    adapter.thumbClickListener = { chanPost: ChanPost ->
                        if (activity != null) {
                            val postsWithImages = GalleryPagerAdapter.getPostsWithImages(posts)
                            val ids = LongArray(postsWithImages.size)
                            for (i in postsWithImages.indices) {
                                ids[i] = postsWithImages[i].no
                            }
                            val act = activity
                            if (act != null) {
                                GalleryActivity2.start(act, GalleryActivity2.GALLERY_TYPE_PAGER, chanPost.no, boardName, thread.threadId, ids)
                            }
                        }
                    }
                    adapter.goToPostListener = {
                        if (parentFragment is GoToPostListener) {
                            val l: GoToPostListener = parentFragment as GoToPostListener
                            l.goToPost(it.no)
                        }
                    }
                    listView?.layoutManager = LinearLayoutManager(activity)
                    listView?.adapter = adapter
                }
    }

    private fun extractExtras(bundle: Bundle?) {
        if (bundle == null) {
            return
        }

        if (bundle.containsKey(Extras.EXTRAS_BOARD_NAME)) {
            boardName = bundle.getString(Extras.EXTRAS_BOARD_NAME) ?: ""
        }
        if (bundle.containsKey(Extras.EXTRAS_POST_ID)) {
            id = bundle.getLong(Extras.EXTRAS_POST_ID)
        }
        replies = if (bundle.containsKey(Extras.EXTRAS_POST_LIST)) {
            bundle.getStringArrayList(Extras.EXTRAS_POST_LIST) ?: ArrayList()
        } else {
            ArrayList()
        }
        outsideLinks = if (bundle.containsKey(Extras.EXTRAS_OUTSIDE_LINK_LIST)) {
            bundle.getParcelableArrayList(Extras.EXTRAS_OUTSIDE_LINK_LIST) ?: ArrayList()
        } else {
            emptyList()
        }
//        if (bundle.containsKey(Extras.EXTRAS_SINGLE_THREAD)) {
//            thread = bundle.getParcelable(Extras.EXTRAS_SINGLE_THREAD) ?: ChanThread()
//        }
        if (bundle.containsKey(Extras.EXTRAS_THREAD_ID)) {
            val id = bundle.getLong(Extras.EXTRAS_THREAD_ID)
            if (thread.threadId == -1L) {
                thread = ChanThread(boardName, id, ArrayList())
            }
            threadId = id
        }
    }

    override fun onDestroy() {
        RxUtil.safeUnsubscribe(loadRepliesSubscription)
        RxUtil.safeUnsubscribe(repliesSubscription)
        super.onDestroy()
    }
}
