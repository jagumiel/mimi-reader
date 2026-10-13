package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import androidx.slidingpanelayout.widget.SlidingPaneLayout.PanelSlideListener
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.GalleryActivity2.Companion.start
import com.emogoth.android.phone.mimi.db.HistoryTableConnection
import com.emogoth.android.phone.mimi.fragment.*
import com.emogoth.android.phone.mimi.interfaces.*
import com.emogoth.android.phone.mimi.util.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mimireader.chanlib.models.ChanBoard
import com.mimireader.chanlib.models.ChanPost
import com.novoda.simplechromecustomtabs.SimpleChromeCustomTabs

class SlidingPanelActivity : MimiActivity(), BoardItemClickListener, View.OnClickListener, ThumbnailClickListener, GalleryMenuItemClickListener, IToolbarContainer {
    private var listType = BOARD_LIST_ID
    private var boardName: String? = null
    private var boardTitle: String? = null
    private var listFragment: MimiFragmentBase? = null
    private var detailFragment: MimiFragmentBase? = null
    private var boardsFragment: MimiFragmentBase? = null
    private var openPage = Pages.NONE
    private var addContentFab: FloatingActionButton? = null
    private var panelLayout: SlidingPaneLayout? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sliding_panel)
        supportFragmentManager.addOnBackStackChangedListener {
            val activeListFragment = supportFragmentManager
                    .findFragmentById(R.id.postitem_list) as? MimiFragmentBase
            if (activeListFragment != null) {
                listFragment = activeListFragment
                if (activeListFragment is BoardItemListFragment) {
                    boardsFragment = activeListFragment
                }
                setFabVisibility(activeListFragment.showFab())
                if (panelLayout?.isOpen == true) {
                    activeListFragment.initMenu()
                }
            }
            refreshBackNavigationState()
        }
        var sliderFadeColor = if (MimiUtil.getInstance().theme == MimiUtil.THEME_LIGHT) {
            ResourcesCompat.getColor(resources, R.color.background_light, theme)
        } else {
            ResourcesCompat.getColor(resources, R.color.background_dark, theme)
        }
        val coverFadeColor = sliderFadeColor
        sliderFadeColor = Color.argb(0xAA, Color.red(sliderFadeColor), Color.green(sliderFadeColor), Color.blue(sliderFadeColor))
        panelLayout = findViewById<View>(R.id.panel_layout) as SlidingPaneLayout
        panelLayout?.sliderFadeColor = sliderFadeColor
        panelLayout?.coveredFadeColor = coverFadeColor
        panelLayout?.setPanelSlideListener(object : PanelSlideListener {
            override fun onPanelSlide(panel: View, slideOffset: Float) {}
            override fun onPanelOpened(panel: View) {
                if (listFragment != null) {
                    listFragment?.initMenu()
                }
                refreshBackNavigationState()
            }

            override fun onPanelClosed(panel: View) {
                if (detailFragment != null) {
                    detailFragment?.initMenu()
                }
                refreshBackNavigationState()
            }
        })
        panelLayout?.openPane()
        addContentFab = findViewById<View>(R.id.fab_add_content) as FloatingActionButton
        addContentFab?.setOnClickListener {
            val fragment = if (panelLayout?.isOpen == true) {
                listFragment
            } else {
                detailFragment
            }
            if (fragment is ContentInterface) {
                (fragment as ContentInterface).addContent()
            }
        }

        val toolbar: Toolbar? = findViewById<View>(R.id.mimi_toolbar) as Toolbar
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(this)
            this.toolbar = toolbar
        }
        extractExtras(intent.extras)
        initDrawers(R.id.nav_drawer, R.id.nav_drawer_container, true)
        createDrawers(R.id.nav_drawer)
        initFragments()
        if (openPage == Pages.BOOKMARKS) {
            openHistoryPage(true)
        }
    }

    private fun initFragments() {
        val ft = supportFragmentManager.beginTransaction()
        when (listType) {
            BOARD_LIST_ID -> {
                val fragment = BoardItemListFragment()
                val extras = Bundle()
                extras.putBoolean(Extras.EXTRAS_OPTIONS_MENU_ENABLED, false)
                fragment.arguments = extras
                ft.add(R.id.postitem_list, fragment, TAG_BOARD_LIST)
                ft.commit()
                fragment.setActivateOnItemClick(true)
                detailFragment = fragment
                listFragment = fragment
                boardsFragment = fragment
            }
            BOOKMARKS_ID -> {
                val fragment = HistoryFragment()
                val args = Bundle()
                args.putInt(Extras.EXTRAS_HISTORY_QUERY_TYPE, HistoryTableConnection.BOOKMARKS)
                args.putInt(Extras.EXTRAS_VIEWING_HISTORY, VIEWING_BOOKMARKS)
                args.putBoolean(Extras.EXTRAS_OPTIONS_MENU_ENABLED, false)
                fragment.arguments = args
                ft.add(R.id.postitem_list, fragment, TAG_BOARD_LIST)
                ft.commit()
                detailFragment = fragment
                listFragment = fragment
            }
            HISTORY_ID -> {
                val fragment = HistoryFragment()
                val args = Bundle()
                args.putInt(Extras.EXTRAS_HISTORY_QUERY_TYPE, HistoryTableConnection.HISTORY)
                args.putInt(Extras.EXTRAS_VIEWING_HISTORY, VIEWING_HISTORY)
                args.putBoolean(Extras.EXTRAS_OPTIONS_MENU_ENABLED, false)
                fragment.arguments = args
                ft.add(R.id.postitem_list, fragment, TAG_BOARD_LIST)
                ft.commit()
                detailFragment = fragment
                listFragment = fragment
            }
        }
    }

    private fun extractExtras(extras: Bundle?) {
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
    }

    private fun setFabVisibility(visible: Boolean) {
        if (addContentFab?.isShown == true && !visible) {
            addContentFab?.hide()
        } else if (addContentFab?.isShown == false && visible) {
            addContentFab?.show()
        }
    }

    override fun onPause() {
        SimpleChromeCustomTabs.getInstance().disconnectFrom(this)
        super.onPause()
    }

    override fun onResume() {
        SimpleChromeCustomTabs.getInstance().connectTo(this)
        super.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        if (panelLayout?.isOpen == true) {
            listFragment?.onCreateOptionsMenu(menu, inflater)
        } else {
            detailFragment?.onCreateOptionsMenu(menu, inflater)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            toggleNavDrawer()
            return true
        }
        return if (panelLayout?.isOpen == true) {
            listFragment?.onOptionsItemSelected(item) == true
        } else {
            detailFragment?.onOptionsItemSelected(item) == true
        }
    }

    override val pageName: String
        get() = "sliding_drawer_activity"

    override fun onClick(v: View) {
        if (listType == BOARD_LIST_ID) {
            toggleNavDrawer()
        } else {
            finish()
        }
    }

    override fun onBoardItemClick(board: ChanBoard, saveBackStack: Boolean) {
        val arguments = Bundle()
        arguments.putString(Extras.EXTRAS_BOARD_NAME, board.name)
        arguments.putString(Extras.EXTRAS_BOARD_TITLE, board.title)
        arguments.putBoolean(Extras.EXTRAS_OPTIONS_MENU_ENABLED, false)
        val fragment = PostItemsListFragment()
        fragment.arguments = arguments
        val fm = supportFragmentManager
        val ft = fm.beginTransaction()
        val catalogItemsFragment = fm.findFragmentByTag(TAG_POST_LIST)
        if (catalogItemsFragment != null) {
            ft.remove(catalogItemsFragment)
        }
        ft.replace(R.id.postitem_list, fragment, TAG_POST_LIST)
        if (saveBackStack) {
            ft.addToBackStack(null)
        }
        ft.commit()
        listFragment = fragment
        setFabVisibility(fragment.showFab())
    }

    override fun onGalleryMenuItemClick(boardPath: String, threadId: Long) {
        start(this, GalleryActivity2.GALLERY_TYPE_GRID, 0, boardPath, threadId, LongArray(0))
    }

    override fun setExpandedToolbar(expanded: Boolean, animate: Boolean) {
        // no op
    }

    override fun onPostItemClick(v: View?, posts: List<ChanPost>, position: Int, boardTitle: String, boardName: String, threadId: Long) {
        openThread(posts, position, boardName, boardTitle, threadId)
    }

    private fun openThread(posts: List<ChanPost>, position: Int, boardName: String, boardTitle: String, threadId: Long) {
        this.boardName = boardName
        this.boardTitle = boardTitle
        val threadDetailFragment = if (posts.size > position) {
            ThreadDetailFragment.newInstance(posts[position].no, boardName, boardTitle, posts[position], true, false)
        } else {
            ThreadDetailFragment.newInstance(threadId, boardName, boardTitle, null, true, false)
        }
        val fm = supportFragmentManager
        val ft = fm.beginTransaction()
        ft.replace(R.id.postitem_detail, threadDetailFragment, TAG_THREAD_DETAIL)
        ft.commit()
        panelLayout?.closePane()
        detailFragment = threadDetailFragment
    }

    override fun hasCustomBackNavigation(): Boolean {
        return super.hasCustomBackNavigation() || panelLayout?.isOpen == false
    }

    override fun handleCustomBackNavigation() {
        if (isNavDrawerOpen()) {
            super.handleCustomBackNavigation()
        } else if (panelLayout?.isOpen == false) {
            panelLayout?.openPane()
        }
    }

    override fun onHistoryItemClicked(boardName: String, threadId: Long, boardTitle: String, position: Int, watched: Boolean) {
        this.boardName = boardName
        this.boardTitle = boardTitle
        val fm = supportFragmentManager
        val ft = fm.beginTransaction()
        val threadDetailFragment = ThreadDetailFragment.newInstance(threadId, boardName, boardTitle, null, false, false)
        ft.replace(R.id.postitem_detail, threadDetailFragment, TAG_THREAD_DETAIL)
        ft.commit()
        if (panelLayout?.isOpen == true) {
            panelLayout?.closePane()
        }

        detailFragment = threadDetailFragment
    }

    override fun openHistoryPage(watched: Boolean) {
        val fm = supportFragmentManager
        val ft = fm.beginTransaction()
        val saveBackStack = listFragment === boardsFragment
        val fragment = HistoryFragment.newInstance(watched)
        ft.replace(R.id.postitem_list, fragment, TAG_POST_LIST)
        if (saveBackStack) {
            ft.addToBackStack(null)
        }
        ft.commit()
        listFragment = fragment
        panelLayout?.openPane()
    }

    override fun onReplyClicked(boardName: String, threadId: Long, id: Long, replies: List<String>) {
        if (detailFragment is ReplyClickListener) {
            val frag = detailFragment as ReplyClickListener
            frag.onReplyClicked(boardName, threadId, id, replies)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val page = intent.getStringExtra(Extras.OPEN_PAGE)
        if (!TextUtils.isEmpty(page)) {
            try {
                val pageEnum = Pages.valueOf(page!!)
                val watched: Boolean
                watched = pageEnum == Pages.BOOKMARKS
                openHistoryPage(watched)
            } catch (e: Exception) {
            }
        }
    }

    companion object {
        const val BOARD_LIST_ID = 0
        const val BOOKMARKS_ID = 1
        const val HISTORY_ID = 2
        private const val TAG_BOARD_LIST = "board_list_fragment"
        private const val TAG_POST_LIST = "post_list_fragment"
        private const val TAG_THREAD_DETAIL = "thread_detail_fragment"
    }
}
