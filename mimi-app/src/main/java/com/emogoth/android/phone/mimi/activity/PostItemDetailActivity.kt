package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.PostItemDetailActivity
import com.emogoth.android.phone.mimi.activity.PostItemListActivity
import com.emogoth.android.phone.mimi.db.DatabaseUtils.applySingleSchedulers
import com.emogoth.android.phone.mimi.db.HistoryTableConnection.fetchHistory
import com.emogoth.android.phone.mimi.db.models.History
import com.emogoth.android.phone.mimi.fragment.MimiFragmentBase
import com.emogoth.android.phone.mimi.fragment.ThreadDetailFragment
import com.emogoth.android.phone.mimi.fragment.ThreadPagerFragment
import com.emogoth.android.phone.mimi.interfaces.ContentInterface
import com.emogoth.android.phone.mimi.interfaces.IToolbarContainer
import com.emogoth.android.phone.mimi.interfaces.ReplyClickListener
import com.emogoth.android.phone.mimi.interfaces.ThreadSelectedListener
import com.emogoth.android.phone.mimi.model.ThreadInfo
import com.emogoth.android.phone.mimi.util.Extras
import com.emogoth.android.phone.mimi.util.MimiUtil
import com.emogoth.android.phone.mimi.util.RxUtil
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.novoda.simplechromecustomtabs.SimpleChromeCustomTabs
import io.reactivex.disposables.Disposable
import java.io.File

/**
 * An activity representing a single PostItem detail screen. This
 * activity is only used on handset devices. On tablet-size devices,
 * item details are presented side-by-side with a list of items
 * in a [PostItemListActivity].
 *
 *
 * This activity is mostly just a 'shell' activity containing nothing
 * more than a [com.emogoth.android.phone.mimi.fragment.ThreadPagerFragment].
 */
class PostItemDetailActivity : MimiActivity(), View.OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener, IToolbarContainer {
    private var fromUrl = false
    private var singleThread = false
    private var boardName: String? = null
    private var threadFragment: MimiFragmentBase? = null
    private var appBarLayout: AppBarLayout? = null
    private var addContentFab: FloatingActionButton? = null
    private var fetchHistorySubscription: Disposable? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_postitem_detail)
        val toolbar: Toolbar? = findViewById<View>(R.id.mimi_toolbar) as Toolbar
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(this)
            //            toolbar.setSubtitleTextColor(getResources().getColor(R.color.toolbar_subtitle_color));
            this.toolbar = toolbar
        }
        appBarLayout = findViewById<View>(R.id.appbar) as AppBarLayout
        addContentFab = findViewById<View>(R.id.fab_add_content) as FloatingActionButton
        addContentFab?.setOnClickListener {
            if (threadFragment is ContentInterface) {
                (threadFragment as ContentInterface).addContent()
            }
        }
        val extras = intent.extras
        if (extras != null) {
            if (extras.containsKey(Extras.EXTRAS_BOARD_NAME)) {
                boardName = extras.getString(Extras.EXTRAS_BOARD_NAME)
            }
            var position = -1
            if (extras.containsKey(Extras.EXTRAS_POSITION)) {
                position = extras.getInt(Extras.EXTRAS_POSITION, -1)
            }
            if (extras.containsKey(Extras.EXTRAS_BOARD_TITLE)) {
                title = extras.getString(Extras.EXTRAS_BOARD_TITLE)
            }
            if (extras.containsKey(Extras.EXTRAS_FROM_URL)) {
                fromUrl = true
            }
            if (!extras.containsKey(Extras.EXTRAS_THREAD_LIST)) {
                if (position >= 0) {
                    val threadList: ArrayList<ThreadInfo> = intent.getParcelableArrayListExtra(Extras.EXTRAS_THREAD_LIST)
                            ?: ArrayList()
                    if (!TextUtils.isEmpty(threadList[position].boardTitle)) {
                        title = threadList[position].boardTitle
                    }
                }
                singleThread = true
            }
        }

        initDrawers(R.id.nav_drawer, R.id.nav_drawer_container, false)
        createDrawers(R.id.nav_drawer)


        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            val arguments = intent.extras
            threadFragment = if (singleThread) {
                ThreadDetailFragment()
            } else {
                ThreadPagerFragment()
            }
            threadFragment?.arguments = arguments
            supportFragmentManager.beginTransaction()
                    .add(R.id.postitem_detail_container, threadFragment!!, POST_DETAIL_FRAGMENT_TAG)
                    .commit()
        } else {
            threadFragment = supportFragmentManager.findFragmentByTag(POST_DETAIL_FRAGMENT_TAG) as MimiFragmentBase?
        }
    }

    override fun setExpandedToolbar(expanded: Boolean, animate: Boolean) {
        if (appBarLayout != null) {
            appBarLayout?.setExpanded(expanded, animate)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            toggleNavDrawer()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        val handled = if (threadFragment != null) {
            threadFragment?.onBackPressed() == true
        } else {
            false
        }
        if (!handled) {
            super.onBackPressed()
            invalidateOptionsMenu()
        }
    }

    override fun getCacheDir(): File {
        return MimiUtil.getInstance().cacheDir
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {}
    override fun onResume() {
        super.onResume()
        SimpleChromeCustomTabs.getInstance().connectTo(this)
        //        setNavigationIconWithBadge(0, ThreadRegistry.getInstance().getUnreadCount());
    }

    override fun onPause() {
        SimpleChromeCustomTabs.getInstance().disconnectFrom(this)
        super.onPause()
        RxUtil.safeUnsubscribe(fetchHistorySubscription)
    }

    override val pageName: String
        get() = "post_detail"

    override fun onHistoryItemClicked(boardName: String, threadId: Long, boardTitle: String, position: Int, watched: Boolean) {
        if (threadFragment is ThreadSelectedListener && threadFragment?.isAdded == true) {
            (threadFragment as ThreadSelectedListener).onThreadSelected(boardName, threadId, position)
        } else {
            RxUtil.safeUnsubscribe(fetchHistorySubscription)
            fetchHistorySubscription = fetchHistory(true)
                    .compose(applySingleSchedulers())
                    .subscribe { bookmarks: List<History> ->
                        val args = Bundle()
                        args.putBoolean(Extras.EXTRAS_USE_BOOKMARKS, true)
                        args.putInt(Extras.EXTRAS_VIEWING_HISTORY, VIEWING_BOOKMARKS)
                        if (!TextUtils.isEmpty(boardTitle)) {
                            args.putString(Extras.EXTRAS_BOARD_NAME, boardTitle)
                        }
                        args.putString(Extras.EXTRAS_BOARD_NAME, boardName)
                        args.putLong(Extras.EXTRAS_THREAD_ID, threadId)
                        args.putInt(Extras.EXTRAS_POSITION, position)
                        val threadList = ArrayList<ThreadInfo>(bookmarks.size)
                        for (post in bookmarks) {
                            val threadInfo = ThreadInfo(post.threadId, post.boardName, "", post.watched)
                            threadList.add(threadInfo)
                        }
                        args.putParcelableArrayList(Extras.EXTRAS_THREAD_LIST, threadList)

                        val intent = Intent(this@PostItemDetailActivity, PostItemListActivity::class.java)
                        intent.putExtras(args)
                        startActivity(intent)
                    }
        }
    }

    override fun onReplyClicked(boardName: String, threadId: Long, id: Long, replies: List<String>) {
        if (threadFragment is ReplyClickListener) {
            val frag = threadFragment as ReplyClickListener
            frag.onReplyClicked(boardName, threadId, id, replies)
        }
    }

    override fun onClick(v: View) {
        // This ID represents the Home or Up button. In the case of this
        // activity, the Up button is shown. Use NavUtils to allow users
        // to navigate up one level in the application structure. For
        // more details, see the Navigation pattern on Android Design:
        //
        // http://developer.android.com/design/patterns/navigation.html#up-vs-back
        //
        val listIntent = Intent(this, PostItemListActivity::class.java)
        if (NavUtils.shouldUpRecreateTask(this, listIntent)) {
            val taskStack = TaskStackBuilder.create(this)
            taskStack.addNextIntent(listIntent)
            taskStack.startActivities()
        } else {
            NavUtils.navigateUpTo(this, listIntent)
        }
    }

    override fun onHomeButtonClicked() {
        NavUtils.navigateUpTo(this, Intent(this, PostItemListActivity::class.java))
    }

    override fun openHistoryPage(watched: Boolean) {
        val bookmarkId = if (watched) {
            PostItemListActivity.BOOKMARKS_ID
        } else {
            PostItemListActivity.HISTORY_ID
        }
        val intent = Intent(this, PostItemListActivity::class.java)
        val args = Bundle()
        args.putInt(Extras.EXTRAS_LIST_TYPE, bookmarkId)
        intent.putExtras(args)
        startActivity(intent)
    }

    companion object {
        private val LOG_TAG = PostItemDetailActivity::class.java.simpleName
        const val POST_DETAIL_FRAGMENT_TAG = "POST_ITEM_DETAIL_FRAGMENT"
        private const val LOG_DEBUG = true
    }
}
