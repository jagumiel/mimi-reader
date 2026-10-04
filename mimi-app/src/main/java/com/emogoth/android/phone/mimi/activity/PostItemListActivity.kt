package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.*
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.GalleryActivity2.Companion.start
import com.emogoth.android.phone.mimi.activity.PostItemListActivity
import com.emogoth.android.phone.mimi.db.BoardTableConnection.fetchBoard
import com.emogoth.android.phone.mimi.db.DatabaseUtils.applySingleSchedulers
import com.emogoth.android.phone.mimi.db.HistoryTableConnection
import com.emogoth.android.phone.mimi.db.HistoryTableConnection.fetchHistory
import com.emogoth.android.phone.mimi.db.models.History
import com.emogoth.android.phone.mimi.databinding.ActivityPostitemListBinding
import com.emogoth.android.phone.mimi.fragment.BoardItemListFragment
import com.emogoth.android.phone.mimi.fragment.HistoryFragment
import com.emogoth.android.phone.mimi.fragment.MimiFragmentBase
import com.emogoth.android.phone.mimi.fragment.PostItemsListFragment
import com.emogoth.android.phone.mimi.interfaces.*
import com.emogoth.android.phone.mimi.model.ThreadInfo
import com.emogoth.android.phone.mimi.util.*
import com.mimireader.chanlib.models.ChanBoard
import com.mimireader.chanlib.models.ChanPost
import io.reactivex.disposables.Disposable
import java.io.File
import java.util.*

/**
 * An activity representing a list of PostItems. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a [com.emogoth.android.phone.mimi.activity.PostItemDetailActivity] representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 *
 *
 * The activity makes heavy use of fragments. The list of items is a
 * [com.emogoth.android.phone.mimi.fragment.BoardItemListFragment] and the item details
 * (if present) is a [com.emogoth.android.phone.mimi.fragment.ThreadPagerFragment].
 *
 *
 * This activity also implements the required
 * [com.emogoth.android.phone.mimi.interfaces.BoardItemClickListener] interface
 * to listen for item selections.
 */
class PostItemListActivity : MimiActivity(), BoardItemClickListener, View.OnClickListener, ThumbnailClickListener, GalleryMenuItemClickListener, IToolbarContainer {
    private lateinit var binding: ActivityPostitemListBinding
    private val fab_add_content get() = binding.fabAddContent
    private val mimi_toolbar get() = binding.mimiToolbar
    private val appbar get() = binding.appbar

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var mTwoPane = false
    private var listType = BOARD_LIST_ID
    private var boardName: String? = null
    private var currentFragment: MimiFragmentBase? = null
    private var boardTitle: String? = null

    //    private var addContentFab: FloatingActionButton? = null
    private var fragmentList: Stack<MimiFragmentBase>? = null
    private var homeFragment: MimiFragmentBase? = null
    private var openPage = Pages.NONE

    //    private var appBarLayout: AppBarLayout? = null
    private var boardInfoSubscription: Disposable? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostitemListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fragmentList = Stack()
//        appBarLayout = findViewById(R.id.appbar)
//        addContentFab = findViewById(R.id.fab_add_content)
        fab_add_content.setOnClickListener(View.OnClickListener { v: View ->
            if (currentFragment is ContentInterface) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val centerX = Math.round((v.right - v.left).toFloat() / 2.0f)
                    val centerY = Math.round((v.bottom - v.top).toFloat() / 2.0f)
                    val radius = v.right - v.left
                    ViewAnimationUtils.createCircularReveal(v, centerX, centerY, radius.toFloat(), radius * 2.toFloat()).start()
                }
                (currentFragment as ContentInterface).addContent()
            }
        })
        fab_add_content.hide()
        if (mimi_toolbar != null) {
            mimi_toolbar.setNavigationOnClickListener(this)
            toolbar = mimi_toolbar
        }
        val extras = intent.extras
        if (extras != null) {
            boardName = extras.getString(Extras.EXTRAS_BOARD_NAME)
            if (extras.containsKey(Extras.EXTRAS_LIST_TYPE)) {
                listType = extras.getInt(Extras.EXTRAS_LIST_TYPE)
            }
            if (extras.containsKey(Extras.OPEN_PAGE)) {
                val page = extras.getString(Extras.OPEN_PAGE)
                if (!TextUtils.isEmpty(page)) {
                    openPage = Pages.valueOf(page!!)
                }
            }
        }

        if (findViewById<View?>(R.id.postitem_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-large and
            // res/values-sw600dp). If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true

            // In two-pane mode, list items should be given the
            // 'activated' state when touched.
//            ((BoardItemListFragment) getSupportFragmentManager()
//                    .findFragmentById(R.id.postitem_list))
//                    .setActivateOnItemClick(true);
        }
        initDrawers(R.id.nav_drawer, R.id.nav_drawer_container, listType == BOARD_LIST_ID)
        if (savedInstanceState == null) {
            createDrawers(R.id.nav_drawer)
            initFragments()
            if (boardName != null) {
                RxUtil.safeUnsubscribe(boardInfoSubscription)
                boardInfoSubscription = fetchBoard(boardName!!)
                        .compose(applySingleSchedulers())
                        .subscribe { board: ChanBoard ->
                            if (!TextUtils.isEmpty(board.name)) {
                                onBoardItemClick(board, false)
                            }
                        }
            }
        }
        if (openPage != Pages.NONE) {
            val intent = Intent(this, PostItemListActivity::class.java)
            val args = Bundle()
            if (openPage == Pages.BOOKMARKS) {
                args.putInt(Extras.EXTRAS_LIST_TYPE, BOOKMARKS_ID)
                intent.putExtras(args)
            }
            startActivity(intent)
        }
    }

    private fun initFragments() {
        val ft = supportFragmentManager.beginTransaction()
        if (listType == BOARD_LIST_ID) {
            val fragment = BoardItemListFragment()
            ft.add(R.id.postitem_list, fragment, TAG_BOARD_LIST)
            ft.commit()
            fragment.setActivateOnItemClick(true)
            homeFragment = fragment
            currentFragment = fragment
        } else if (listType == BOOKMARKS_ID) {
            val fragment = HistoryFragment()
            val args = Bundle()
            args.putInt(Extras.EXTRAS_HISTORY_QUERY_TYPE, HistoryTableConnection.BOOKMARKS)
            args.putInt(Extras.EXTRAS_VIEWING_HISTORY, VIEWING_BOOKMARKS)
            fragment.arguments = args
            ft.add(R.id.postitem_list, fragment, TAG_BOARD_LIST)
            ft.commit()
            homeFragment = fragment
            currentFragment = fragment
        } else if (listType == HISTORY_ID) {
            val fragment = HistoryFragment()
            val args = Bundle()
            args.putInt(Extras.EXTRAS_HISTORY_QUERY_TYPE, HistoryTableConnection.HISTORY)
            args.putInt(Extras.EXTRAS_VIEWING_HISTORY, VIEWING_HISTORY)
            fragment.arguments = args
            ft.add(R.id.postitem_list, fragment, TAG_BOARD_LIST)
            ft.commit()
            homeFragment = fragment
            currentFragment = fragment
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                toggleNavDrawer()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("rotated", true)
        super.onSaveInstanceState(outState)
    }

    override fun onBoardItemClick(board: ChanBoard, saveBackStack: Boolean) {
        val arguments = Bundle()
        arguments.putString(Extras.EXTRAS_BOARD_NAME, board.name)
        arguments.putString(Extras.EXTRAS_BOARD_TITLE, board.title)
        arguments.putBoolean(Extras.EXTRAS_TWOPANE, mTwoPane)
        val fragment = PostItemsListFragment()
        fragment.arguments = arguments
        //        fragment.setBoards(boards);
        val fm = supportFragmentManager
        val ft = fm.beginTransaction()
        val catalogItemsFragment = fm.findFragmentByTag(TAG_POST_LIST)
        if (catalogItemsFragment != null) {
            ft.remove(catalogItemsFragment)
        }
        if (currentFragment != null) {
            ft.hide(currentFragment!!)
        }
        ft.add(R.id.postitem_list, fragment, TAG_POST_LIST)
        if (saveBackStack) {
            ft.addToBackStack(null)
        }
        ft.commit()
        currentFragment = fragment
        setFabVisibility(currentFragment?.showFab() ?: false)
    }

    override fun onBackPressed() {
        val handled: Boolean
        //        if(isDrawerOpen(ToggleDrawerEvent.DrawerType.BOOKMARKS)) {
//            toggleBookmarkDrawer();
//            handled = true;
//        } else
        handled = if (currentFragment != null && currentFragment!!.userVisibleHint) {
            currentFragment!!.onBackPressed()
        } else {
            false
        }
        if (!handled) {
//            final int i = getSupportFragmentManager().getBackStackEntryCount() - 1;
//            currentFragment = (MimiFragmentBase) getSupportFragmentManager().findFragmentById(id);
            if (fragmentList != null && fragmentList!!.size > 0) {
                currentFragment = fragmentList!!.pop()
            } else {
                currentFragment = homeFragment
                if (currentFragment != null) {
                    setFabVisibility(currentFragment!!.showFab())
                }
            }
            if (currentFragment is BoardItemListFragment) {
                currentFragment?.onResume()
                currentFragment?.initMenu()
                setExpandedToolbar(true, true)
            }
            super.onBackPressed()
            supportInvalidateOptionsMenu()

//            if (getToolbar() != null) {
//                getToolbar().setVisibility(View.VISIBLE);
//            }
        }
    }

    private fun setFabVisibility(visible: Boolean) {
        if (fab_add_content.isShown && !visible) {
            fab_add_content.hide()
        } else if (!fab_add_content.isShown && visible) {
            fab_add_content.show()
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        return true
    }

    override fun getCacheDir(): File {
        return MimiUtil.getInstance().cacheDir
    }

    override fun onGalleryMenuItemClick(boardPath: String, threadId: Long) {
        start(this, GalleryActivity2.GALLERY_TYPE_GRID, 0, boardPath, threadId, LongArray(0))
    }

    override fun onStop() {
        super.onStop()
        RxUtil.safeUnsubscribe(boardInfoSubscription)
    }

    override fun setExpandedToolbar(expanded: Boolean, animate: Boolean) {
        if (appbar != null) {
            appbar.setExpanded(expanded, animate)
        }
    }

    override val pageName: String
        get() = "post_list"

    override fun onHistoryItemClicked(boardName: String, threadId: Long, boardTitle: String, position: Int, watched: Boolean) {
        val sub = fetchHistory(true)
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
                    val clazz = if (resources.getBoolean(R.bool.two_pane)) {
                        PostItemListActivity::class.java
                    } else {
                        PostItemDetailActivity::class.java
                    }
                    val intent = Intent(this@PostItemListActivity, clazz)
                    intent.putExtras(args)
                    startActivity(intent)
                }
    }

    override fun onClick(v: View) {
        if (listType == BOARD_LIST_ID) {
            toggleNavDrawer()
        } else {
            finish()
        }
    }

    override fun onPostItemClick(v: View?, posts: List<ChanPost>, position: Int, boardTitle: String, boardName: String, threadId: Long) {
        openThread(posts, position, boardName, boardTitle)
    }

    fun openThread(posts: List<ChanPost>, position: Int, boardName: String, boardTitle: String) {
        this.boardName = boardName
        this.boardTitle = boardTitle
        val threadList = ArrayList<ThreadInfo>(posts.size)
        for (post in posts) {
            val threadInfo = ThreadInfo(post.no, boardName, boardTitle, false)
            threadList.add(threadInfo)
        }
        val detailIntent = Intent(this, PostItemDetailActivity::class.java)
        detailIntent.putExtra(Extras.EXTRAS_THREAD_LIST, threadList)
        detailIntent.putExtra(Extras.EXTRAS_POSITION, position)
        startActivity(detailIntent)
    }

    override fun onHomeButtonClicked() {
        val intent = Intent(this, PostItemListActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun openHistoryPage(watched: Boolean) {
        val bookmarkId = if (watched) {
            BOOKMARKS_ID
        } else {
            HISTORY_ID
        }
        val intent = Intent(this, PostItemListActivity::class.java)
        val args = Bundle()
        args.putInt(Extras.EXTRAS_LIST_TYPE, bookmarkId)
        intent.putExtras(args)
        startActivity(intent)
    }

    companion object {
        private val LOG_TAG = PostItemListActivity::class.java.simpleName
        private const val LOG_DEBUG = true
        const val BOARD_LIST_ID = 0
        const val BOOKMARKS_ID = 1
        const val HISTORY_ID = 2
        private const val TAG_BOARD_LIST = "board_list_fragment"
        private const val TAG_POST_LIST = "post_list_fragment"
        private const val TAG_THREAD_DETAIL = "thread_detail_fragment"
    }
}
