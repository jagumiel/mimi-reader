package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.GalleryActivity2.Companion.start
import com.emogoth.android.phone.mimi.adapter.TabPagerAdapter
import com.emogoth.android.phone.mimi.autorefresh.RefreshScheduler2
import com.emogoth.android.phone.mimi.db.DatabaseUtils
import com.emogoth.android.phone.mimi.db.HistoryTableConnection
import com.emogoth.android.phone.mimi.databinding.ActivityTabsBinding
import com.emogoth.android.phone.mimi.fragment.MimiFragmentBase
import com.emogoth.android.phone.mimi.fragment.PostItemsListFragment
import com.emogoth.android.phone.mimi.interfaces.*
import com.emogoth.android.phone.mimi.util.Extras
import com.emogoth.android.phone.mimi.util.Pages
import com.emogoth.android.phone.mimi.util.RxUtil
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.TabLayoutOnPageChangeListener

import com.mimireader.chanlib.models.ChanBoard
import com.mimireader.chanlib.models.ChanPost
import com.novoda.simplechromecustomtabs.SimpleChromeCustomTabs
import io.reactivex.disposables.Disposable
import java.util.*

class TabsActivity : MimiActivity(), BoardItemClickListener, View.OnClickListener, PostItemClickListener, IToolbarContainer, GalleryMenuItemClickListener, TabEventListener {
    private lateinit var binding: ActivityTabsBinding
    private val tab_layout get() = binding.tabLayout
    private val tabs_pager get() = binding.tabsPager
    private val fab_add_content get() = binding.fabAddContent
    private val mimi_toolbar get() = binding.mimiToolbar
    private val appbar get() = binding.appbar

    private var tabPagerAdapter: TabPagerAdapter? = null
    private var currentFragment: MimiFragmentBase? = null
    private var closeTabOnBack = false
    override val pageName: String? = "tabs_activity"

    private var historyObserver: Disposable? = null

    companion object {
        const val LOG_TAG = "TabsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTabsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        closeTabOnBack = sp.getBoolean(getString(R.string.close_tab_on_back_pref), false)
        toolbar = mimi_toolbar
        mimi_toolbar.setNavigationOnClickListener { toggleNavDrawer() }
        val tabItems: ArrayList<TabPagerAdapter.TabItem>?
        if (savedInstanceState != null && savedInstanceState.containsKey("tabItems")) {
            tabItems = savedInstanceState.getParcelableArrayList("tabItems")
            tabPagerAdapter = TabPagerAdapter(supportFragmentManager, tabItems)
        } else {
            tabItems = null
            tabPagerAdapter = TabPagerAdapter(supportFragmentManager)
        }
        tabs_pager.setAdapter(tabPagerAdapter)
        tabs_pager.addOnPageChangeListener(TabLayoutOnPageChangeListener(tab_layout))
        tabs_pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                updateCurrentFragment(position)
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })
        updateCurrentFragment(tabs_pager.currentItem)

        // Hack to stop crashing
        // https://code.google.com/p/android/issues/detail?id=201827
//        TabLayout.Tab uselessTab;
//        for (int j = 0; j < 17; j++) {
//            uselessTab = tabLayout.newTab();
//        }
        tab_layout.setTabMode(TabLayout.MODE_SCROLLABLE)
        tab_layout.setupWithViewPager(tabs_pager)
        tab_layout.setTabsFromPagerAdapter(tabPagerAdapter)
        if (savedInstanceState != null && tabItems != null) {
            val count = tab_layout.getTabCount()
            for (i in 1 until count) {
                val tab = tab_layout.getTabAt(i)
                if (tab != null) {
                    val item = tabItems[i]
                    if (i == 1 && item.tabType == TabPagerAdapter.TabType.POSTS) {
                        tab.text = getTabTitle(item.title)
                    } else if (i == 1 && item.tabType == TabPagerAdapter.TabType.HISTORY) {
                        tab.text = item.title.toUpperCase()
                    } else {
                        val args = item.bundle
                        if (args != null) {
                            val threadId = args.getLong(Extras.EXTRAS_THREAD_ID, 0)
                            val boardName = args.getString(Extras.EXTRAS_BOARD_NAME, "")
                            val tabView = createTabView(threadId, boardName)
                            tab.customView = tabView
                            tab.select()
                        }
                    }
                }
            }
        }
        val boardsTab = tab_layout.getTabAt(0)
        boardsTab?.setText(R.string.boards)
        fab_add_content.setOnClickListener { v: View? ->
            if (currentFragment is ContentInterface) {
                (currentFragment as ContentInterface).addContent()
            }
        }

        initDrawers(R.id.nav_drawer, R.id.nav_drawer_container, true)
        createDrawers(R.id.nav_drawer)
        val extras = intent.extras
        var openPage = Pages.NONE
        if (extras != null && extras.containsKey(Extras.OPEN_PAGE)) {
            val page = extras.getString(Extras.OPEN_PAGE) ?: ""
            if (!TextUtils.isEmpty(page)) {
                openPage = Pages.valueOf(page)
            }
        }
        if (openPage == Pages.BOOKMARKS) {
            openHistoryPage(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (tabs_pager != null) {
            tabs_pager.clearOnPageChangeListeners()
        }
    }

    override fun onResume() {
        super.onResume()
        startDatabaseObserver()
        SimpleChromeCustomTabs.getInstance().connectTo(this)
        if (currentFragment != null) {
            setFabVisibility(currentFragment?.showFab() ?: false)
        }
    }

    override fun onPause() {
        RxUtil.safeUnsubscribe(historyObserver)
        SimpleChromeCustomTabs.getInstance().disconnectFrom(this)
        super.onPause()
    }

    override fun onReplyClicked(boardName: String, threadId: Long, id: Long, replies: List<String>) {
        if (currentFragment is ReplyClickListener) {
            val frag = currentFragment as ReplyClickListener
            frag.onReplyClicked(boardName, threadId, id, replies)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val tabItems = ArrayList(tabPagerAdapter?.items ?: emptyList())
        outState.putParcelableArrayList("tabItems", tabItems)
    }

    protected fun getTabTitle(boardName: String): String {
        return "/" + boardName.toUpperCase(Locale.getDefault()) + "/"
    }

    override fun onBoardItemClick(board: ChanBoard, saveBackStack: Boolean) {
        val arguments = Bundle()
        arguments.putString(Extras.EXTRAS_BOARD_NAME, board.name)
        arguments.putString(Extras.EXTRAS_BOARD_TITLE, board.title)
        arguments.putBoolean(Extras.EXTRAS_TWOPANE, false)
        arguments.putBoolean(Extras.EXTRAS_STICKY_AUTO_REFRESH, true)
        val tabItem = TabPagerAdapter.TabItem(TabPagerAdapter.TabType.POSTS, arguments, PostItemsListFragment.TAB_ID.toLong(), board.name, null)
        if (tabPagerAdapter?.count == 1) {
            val postListTab = tab_layout.newTab()
            postListTab.text = getTabTitle(board.name)
            tabPagerAdapter?.addItem(tabItem)
            tab_layout.addTab(postListTab)
        } else {
            val postListTab = tab_layout.getTabAt(1)
            if (postListTab != null) {
                val newTab = tab_layout.newTab()
                newTab.text = getTabTitle(board.name)
                tab_layout.removeTabAt(1)
                tab_layout.addTab(newTab, 1)
            }
            tabPagerAdapter?.setItemAtIndex(1, tabItem)
        }
        tabs_pager.setCurrentItem(1, true)
    }

    private fun updateCurrentFragment(position: Int) {
        tabs_pager.post {
            val fragment = tabPagerAdapter?.getActiveFragment(position) as? MimiFragmentBase
            currentFragment = fragment
            fragment?.initMenu()
            setFabVisibility(fragment?.showFab() ?: false)
        }
    }

    private fun setFabVisibility(shouldShow: Boolean) {
        if (fab_add_content.isShown && !shouldShow) {
            fab_add_content.hide()
        } else if (!fab_add_content.isShown && shouldShow) {
            fab_add_content.show()
        }
    }

    override fun setExpandedToolbar(expanded: Boolean, animate: Boolean) {
        appbar.setExpanded(expanded, animate)
    }

    override fun onClick(v: View) {}
    override fun onBackPressed() {
        var handled = false
        val pos = tabs_pager.currentItem

        if (currentFragment != null) {
            handled = currentFragment?.onBackPressed() ?: false
        }
        if (!handled && pos > 0) {
            handled = true
            if (closeTabOnBack && pos > 1) {
                closeTab(pos, false)
            } else {
                tabs_pager.setCurrentItem(pos - 1, true)
            }
        }
        if (!handled) {
            super.onBackPressed()
            invalidateOptionsMenu()
        }
    }

    override fun onPostItemClick(v: View?, posts: List<ChanPost>, position: Int, boardTitle: String, boardName: String, threadId: Long) {
        val threadTab = tab_layout.newTab()
        val threadTabItem: TabPagerAdapter.TabItem
        val args = Bundle()
        args.putLong(Extras.EXTRAS_THREAD_ID, threadId)
        args.putString(Extras.EXTRAS_BOARD_NAME, boardName)
        args.putString(Extras.EXTRAS_BOARD_TITLE, boardTitle)
        args.putBoolean(Extras.EXTRAS_STICKY_AUTO_REFRESH, true)
        if (posts.size > position) {
            args.putParcelable(Extras.EXTRAS_THREAD_FIRST_POST, posts[position])
        }
        val tabView = createTabView(threadId, boardName)
        threadTab.customView = tabView
        threadTab.select()
        threadTabItem = TabPagerAdapter.TabItem(TabPagerAdapter.TabType.THREAD, args, threadId, boardName, threadId.toString())
        val itemCount = tabPagerAdapter?.count ?: 0
        val pos = tabPagerAdapter?.addItem(threadTabItem) ?: 0
        if (pos < 0) {
            return
        } else if (pos >= itemCount) {
            tab_layout.addTab(threadTab)
        }
        tabs_pager.setCurrentItem(pos, true)
    }

    private fun createTabView(threadId: Long, boardName: String): View {
        val tabView = View.inflate(this, R.layout.tab_default, null)
        val title = tabView.findViewById<View>(R.id.title) as TextView
        val subTitle = tabView.findViewById<View>(R.id.subtitle) as TextView
        tabView.setOnLongClickListener { v ->
            val popupMenu = PopupMenu(this@TabsActivity, v)
            popupMenu.inflate(R.menu.tab_popup_menu)
            popupMenu.setOnMenuItemClickListener { item ->
                try {
                    if (item.itemId == R.id.close_tab) {
                        closeTab(threadId, boardName, true)
                    } else if (item.itemId == R.id.close_other_tabs) {
                        closeOtherTabs(tabPagerAdapter?.getPositionById(threadId) ?: -1)
                    }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Caught exception", e)
                }
                true
            }
            popupMenu.show()
            true
        }
        tabView.setOnClickListener { v: View? ->
            tabs_pager.setCurrentItem(tabPagerAdapter?.getIndex(threadId) ?: -1, false)
        }
        title.text = getTabTitle(boardName)
        subTitle.text = threadId.toString()
        return tabView
    }

    private fun closeOtherTabs(position: Int) {
        val size = tabPagerAdapter?.count ?: 0
        for (i in size - 1 downTo 2) {
            if (i != position) {
                Log.d(LOG_TAG, "Closing tab at position $i")
                val t = tabPagerAdapter?.getTab(i)
                if (t != null) {
                    val id = Integer.valueOf(t.subtitle)
                    closeTab(id.toLong(), t.title.toLowerCase(Locale.getDefault()).replace("/", ""), true)
                }
            }
        }
    }

    private fun closeTab(threadId: Long, boardName: String, showSnackbar: Boolean) {
        val pos = tabPagerAdapter?.getPositionById(threadId) ?: -1
        closeTab(pos, threadId, boardName, showSnackbar)
    }

    private fun closeTab(pos: Int, showSnackbar: Boolean) {
        closeTab(pos, -1, "", showSnackbar)
    }

    private fun closeTab(pos: Int, threadId: Long, boardName: String, showSnackbar: Boolean) {
        if (pos == -1) {
            return
        }

        try {
            val pagerPos = tabs_pager.currentItem
            val newPos: Int
            newPos = if (pos > pagerPos) {
                pagerPos
            } else {
                pagerPos - 1
            }
            var id = threadId
            var name = boardName
            if (threadId <= 0) {
                val item = tabPagerAdapter?.getTabItem(pos)
                if (item != null) {
                    val args = item.bundle
                    if (args != null) {
                        id = args.getLong(Extras.EXTRAS_THREAD_ID, -1)
                        name = args.getString(Extras.EXTRAS_BOARD_NAME, "") ?: ""
                    }
                }
            }
            if (newPos >= 0) {
                Log.d(LOG_TAG, "Removing tab: position=$pos")
                val sub = HistoryTableConnection.fetchPost(name, id)
                        .compose(DatabaseUtils.applySingleSchedulers())
                        .subscribe({
                            RefreshScheduler2.removeThread(name, id)
//                            if (it.watched) {
//                                RefreshScheduler.getInstance().removeThread(name, id)
//                            }
                        }, {
                            Log.e(LOG_TAG, "Caught exception", it)
                        })
                tab_layout.removeTabAt(pos)
                tabPagerAdapter?.removeItemAtIndex(pos)
                tabPagerAdapter?.notifyDataSetChanged()
                //                tabPager.setAdapter(tabPagerAdapter);
                tabs_pager.setCurrentItem(newPos, false)
                if (showSnackbar) {
                    Snackbar.make(tabs_pager, R.string.closing_tab, Snackbar.LENGTH_SHORT).show()
                }
            } else {
                Log.e(LOG_TAG, "Position is invalid: pos=$newPos")
            }
        } catch (e: Exception) {
            Snackbar.make(tabs_pager, R.string.error_occurred, Snackbar.LENGTH_SHORT).show()
            Log.e(LOG_TAG, "Error while closing tab", e)
            Log.e(LOG_TAG, "Caught exception", e)
        }
    }

    override fun onHomeButtonClicked() {
        tabs_pager.setCurrentItem(0, false)
    }

    override fun onHistoryItemClicked(boardName: String, threadId: Long, boardTitle: String, position: Int, watched: Boolean) {
        try {
            val board = ChanBoard()
            board.name = boardName
            board.title = boardTitle
            if (tabPagerAdapter?.count == 1) {
                onBoardItemClick(board, false)
            }
            onPostItemClick(null, emptyList(), position, boardTitle, boardName, threadId)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Caught exception", e)
            Toast.makeText(this@TabsActivity, R.string.error_opening_bookmark, Toast.LENGTH_SHORT).show()
        }
    }

    fun startDatabaseObserver() {
        historyObserver = HistoryTableConnection.observeHistory()
                .compose(DatabaseUtils.applySchedulers())
                .subscribe {
                    for (history in it) {
                        updateTabHighlight(history.boardName, history.threadId, history.unreadCount)
                    }
                }
    }

    fun updateTabHighlight(boardName: String, threadId: Long, unread: Int) {
        if (tabPagerAdapter != null && tab_layout != null) {
            val tabIndex = tabPagerAdapter?.getIndex(threadId) ?: -1
            if (tabIndex >= 0) {
                val t = tab_layout.getTabAt(tabIndex)
                if (t != null) {
                    val v = t.customView
                    if (v != null) {
                        val highlightView = v.findViewById<View>(R.id.highlight)
                        if (highlightView != null) {
                            if (unread > 0) {
                                highlightView.visibility = View.VISIBLE
                            } else {
                                highlightView.visibility = View.GONE
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onTabClosed(id: Long, boardName: String, boardTitle: String, closeOthers: Boolean) {
        if (closeOthers && tabPagerAdapter != null) {
            val position = tabPagerAdapter?.getPositionById(id) ?: -1
            closeOtherTabs(position)
        } else {
            closeTab(id, boardName, true)
        }
    }

    override fun onGalleryMenuItemClick(boardPath: String, threadId: Long) {
        start(this, 0, 0, boardPath, threadId, LongArray(0))
    }

    override fun openHistoryPage(watched: Boolean) {
        val args = Bundle()
        val historyFragmentName: String
        if (watched) {
            historyFragmentName = getString(R.string.bookmarks)
            args.putInt(Extras.EXTRAS_HISTORY_QUERY_TYPE, HistoryTableConnection.BOOKMARKS)
            args.putInt(Extras.EXTRAS_VIEWING_HISTORY, VIEWING_BOOKMARKS)
        } else {
            historyFragmentName = getString(R.string.history)
            args.putInt(Extras.EXTRAS_HISTORY_QUERY_TYPE, HistoryTableConnection.HISTORY)
            args.putInt(Extras.EXTRAS_VIEWING_HISTORY, VIEWING_HISTORY)
        }
        val tabItem = TabPagerAdapter.TabItem(TabPagerAdapter.TabType.HISTORY, args, PostItemsListFragment.TAB_ID.toLong(), historyFragmentName, null)
        if (tabPagerAdapter?.count == 1) {
            val postListTab = tab_layout.newTab()
            postListTab.text = historyFragmentName
            tabPagerAdapter?.addItem(tabItem)
            tab_layout.addTab(postListTab)
        } else {
            val postListTab = tab_layout.getTabAt(1)
            if (postListTab != null) {
                val newTab = tab_layout.newTab()
                newTab.text = historyFragmentName
                tab_layout.removeTabAt(1)
                tab_layout.addTab(newTab, 1)
            }
            tabPagerAdapter?.setItemAtIndex(1, tabItem)
        }
        tabs_pager.setCurrentItem(1, false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val page = intent.getStringExtra(Extras.OPEN_PAGE)
        if (!TextUtils.isEmpty(page)) {
            try {
                val pageEnum = Pages.valueOf(page ?: "BOARDS")
                val watched: Boolean
                watched = pageEnum == Pages.BOOKMARKS
                openHistoryPage(watched)
            } catch (ignore: Exception) {
                // no op
            }
        }
    }

    override fun onCreateActionMode(p0: ActionMode?, p1: Menu?): Boolean {
        tabs_pager.isEnabled = false
        return true
    }

    override fun onDestroyActionMode(p0: ActionMode?) {
        tabs_pager.isEnabled = true
    }
}
