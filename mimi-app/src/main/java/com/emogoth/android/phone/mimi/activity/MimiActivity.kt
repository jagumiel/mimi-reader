package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import com.emogoth.android.phone.mimi.BuildConfig
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.GalleryActivity2.Companion.start
import com.emogoth.android.phone.mimi.db.HistoryTableConnection.observeHistory
import com.emogoth.android.phone.mimi.fragment.NavDrawerFragment
import com.emogoth.android.phone.mimi.interfaces.*
import com.emogoth.android.phone.mimi.util.*
import com.mimireader.chanlib.models.ChanPost
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.prefs.PreferenceChangeEvent
import java.util.prefs.PreferenceChangeListener

abstract class MimiActivity : AppCompatActivity(), PreferenceChangeListener,
        ThumbnailClickListener, PostItemClickListener, ReplyClickListener, HistoryClickedListener, ActionMode.Callback, HomeButtonListener {
//    private val refreshScheduler = RefreshScheduler.getInstance()
    private var navDrawerView: View? = null
    var drawerLayout: DrawerLayout? = null
        private set
    var drawerToggle: ActionBarDrawerToggle? = null
        private set
    private var showBadge = true
    var resultFragment: Fragment? = null
        private set
    private var bookmarksOrHistory = 0
    var toolbar: Toolbar? = null
        set(toolbar) {
            field = toolbar
            if (field != null) {
                field?.logo = null
                setSupportActionBar(field)
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
            }
        }
    private var fetchPostSubscription: Disposable? = null
    private var savedRequestCode = 0
    private var savedResultCode = 0
    private var savedIntentData: Intent? = null

    @DrawableRes
    protected var navDrawable: Int = R.drawable.ic_nav_menu

    private var historyObserver: Disposable? = null

    private val customBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            handleCustomBackNavigation()
            refreshBackNavigationState()
        }
    }

//    var onBookmarkClicked: ((boardName: String, threadId: Long, boardTitle: String, orderId: Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            val currentTheme = packageManager.getActivityInfo(componentName, 0).theme
            if (currentTheme != R.style.Theme_Mimi_Gallery) {
                setTheme(MimiUtil.getInstance().themeResourceId)
            }
            theme.applyStyle(MimiUtil.getFontStyle(this), true)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            setTheme(MimiUtil.getInstance().themeResourceId)
        }
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, customBackCallback)
        val extras = intent.extras
        if (extras != null) {
            if (extras.containsKey(Extras.EXTRAS_SHOW_ACTIONBAR_BADGE)) {
                showBadge = extras.getBoolean(Extras.EXTRAS_SHOW_ACTIONBAR_BADGE, true)
            }
            if (extras.containsKey(Extras.EXTRAS_VIEWING_HISTORY)) {
                bookmarksOrHistory = extras.getInt(Extras.EXTRAS_VIEWING_HISTORY)
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (drawerToggle != null) {
            // Sync the toggle state after onRestoreInstanceState has occurred.
            drawerToggle?.syncState()
        }
    }

    override fun onPause() {
        super.onPause()
        RxUtil.safeUnsubscribe(historyObserver)
    }

    override fun onResume() {
        super.onResume()
        try {
            updateBadge()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Caught exception", e)
        }
    }

    protected fun drawerItemSelected(item: MenuItem?) {
        drawerToggle?.onOptionsItemSelected(item)
    }

    fun toggleNavDrawer() {
        if (navDrawerView == null) {
            return
        }

        val view = navDrawerView as View
        if (drawerLayout?.isDrawerOpen(view) == true) {
            drawerLayout?.closeDrawer(view)
        } else {
            drawerLayout?.openDrawer(view)
        }
    }

    fun isNavDrawerOpen(): Boolean {
        val drawer = navDrawerView ?: return false
        return drawerLayout?.isDrawerOpen(drawer) == true
    }

    protected open fun hasCustomBackNavigation(): Boolean = isNavDrawerOpen()

    protected open fun handleCustomBackNavigation() {
        if (isNavDrawerOpen()) {
            toggleNavDrawer()
        }
    }

    fun refreshBackNavigationState() {
        customBackCallback.isEnabled = hasCustomBackNavigation()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (drawerLayout != null && drawerToggle != null) {
            drawerLayout?.removeDrawerListener(drawerToggle!!)
        }
    }

    protected fun initDrawers(@IdRes navRes: Int, @IdRes drawerLayoutRes: Int, drawerIndicator: Boolean) {
        navDrawerView = findViewById(navRes)
        drawerLayout = findViewById<View>(drawerLayoutRes) as DrawerLayout
        val dt = object : ActionBarDrawerToggle(this, drawerLayout, R.string.app_name, R.string.app_name) {
            override fun onDrawerOpened(drawerView: View) {
                invalidateOptionsMenu()
                syncState()
                refreshBackNavigationState()
            }

            override fun onDrawerClosed(drawerView: View) {
                invalidateOptionsMenu()
                syncState()
                refreshBackNavigationState()
            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                super.onDrawerSlide(drawerView, slideOffset)
                customBackCallback.isEnabled = slideOffset > 0f || hasCustomBackNavigation()
            }
        }

        dt.isDrawerIndicatorEnabled = false
        if (drawerIndicator) {
            navDrawable = R.drawable.ic_nav_menu
        } else {
            navDrawable = R.drawable.ic_nav_arrow_back
        }
        drawerLayout?.addDrawerListener(dt)

        drawerToggle = dt
        refreshBackNavigationState()
    }

    fun updateBadge() {
        historyObserver = observeHistory(true)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    var unread = 0
                    for (history in it) {
                        unread += history.unreadCount
                    }
                    setNavigationIconWithBadge(navDrawable, unread)
                }
    }

    protected fun setNavigationIconWithBadge(drawableRes: Int, count: Int) {
        Log.d(LOG_TAG, "Setting nav icon: count=$count")
        val layers: Array<Drawable?>
        val layerDrawable: LayerDrawable?
        if (drawableRes > 0) {
            if (count > 0) {
                layers = arrayOfNulls(2)
                layers[0] = VectorDrawableCompat.create(resources, drawableRes, theme)
                layers[1] = ResourcesCompat.getDrawable(resources, R.drawable.notification_unread, theme)
            } else {
                layers = arrayOfNulls(1)
                layers[0] = VectorDrawableCompat.create(resources, drawableRes, theme)
            }
            layerDrawable = LayerDrawable(layers)
        } else {
            val navDrawable = toolbar?.navigationIcon
            if (navDrawable is LayerDrawable) {
                val icon = navDrawable.getDrawable(0)
                if (count > 0) {
                    layers = arrayOfNulls(2)
                    layers[0] = icon
                    layers[1] = ResourcesCompat.getDrawable(resources, R.drawable.notification_unread, theme)
                } else {
                    layers = arrayOfNulls(1)
                    layers[0] = icon
                }
                layerDrawable = LayerDrawable(layers)
            } else {
                layerDrawable = null
            }
        }
        if (layerDrawable != null) {
            drawerToggle?.setHomeAsUpIndicator(layerDrawable)
        }
    }

    protected fun createDrawers(@IdRes navRes: Int) {
        val ft = supportFragmentManager.beginTransaction()
        val navDrawerFragment = NavDrawerFragment()
        val args = Bundle()
        args.putInt(Extras.EXTRAS_VIEWING_HISTORY, bookmarksOrHistory)
        ft.add(navRes, navDrawerFragment, TAG_NAV_DRAWER)
        ft.commit()
    }

    override fun preferenceChange(pce: PreferenceChangeEvent) {
        Log.i(LOG_TAG, "Preference Changed: name=" + pce.key + ", value=" + pce.newValue)
    }

    fun showBadge(): Boolean {
        return showBadge
    }

    fun setResultFragment(resultFragment: Fragment, dispatchResults: Boolean) {
        this.resultFragment = resultFragment
        if (dispatchResults && savedIntentData != null) {
            resultFragment.onActivityResult(savedRequestCode, savedResultCode, savedIntentData)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SETTINGS_ID) {
            if (resultCode == RESTART_ACTIVITY_RESULT) {
                startActivity(Intent(this, StartupActivity::class.java))
                finish()
            }
        } else {
            savedRequestCode = requestCode
            savedResultCode = resultCode
            savedIntentData = data
        }
    }

    override fun onThumbnailClick(posts: List<ChanPost>, threadId: Long, position: Int, boardName: String) {
        try {
            val id: Long
            id = if (position < 0 || posts.size <= position) {
                val e = Exception("Could not locate post in post list: position=" + position + ", list size=" + posts.size)
                Log.e(LOG_TAG, "Caught exception", e)
                Log.e(LOG_TAG, "Error opening gallery into a post", e)
                threadId
            } else {
                posts[position].no
            }
//            ThreadRegistry.getInstance().setPosts(threadId, posts)
            start(this, GalleryActivity2.GALLERY_TYPE_PAGER, id, boardName, threadId, LongArray(0))
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Caught exception", e)
            Log.e(LOG_TAG, "Could not open thumbnail: posts.size()=" + posts.size + ", position=" + position)
            Toast.makeText(this, R.string.error_opening_gallery, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPostItemClick(v: View?, posts: List<ChanPost>, position: Int, boardTitle: String, boardName: String, threadId: Long) {
        // no op
    }

    override fun onReplyClicked(boardName: String, threadId: Long, id: Long, replies: List<String>) {
        // no op
    }

    override fun onHistoryItemClicked(boardName: String, threadId: Long, boardTitle: String, position: Int, watched: Boolean) {
        // no op
    }

    override fun openHistoryPage(watched: Boolean) {
        // no op
    }

    // ActionMode.Callback
    override fun onActionItemClicked(p0: ActionMode?, p1: MenuItem?): Boolean {
        // no op
        return false
    }

    override fun onCreateActionMode(p0: ActionMode?, p1: Menu?): Boolean {
        // no op
        return false
    }

    override fun onPrepareActionMode(p0: ActionMode?, p1: Menu?): Boolean {
        // no op
        return false
    }

    override fun onDestroyActionMode(p0: ActionMode?) {
        // no op
    }

    override fun onHomeButtonClicked() {
        // no op
    }

    protected abstract val pageName: String?

    companion object {
        const val LOG_TAG = "MimiActivity"
        private const val LOG_DEBUG = false
        private const val TAG_NAV_DRAWER = "nav_drawer"
        const val VIEWING_NONE = 0
        const val VIEWING_BOOKMARKS = 1
        const val VIEWING_HISTORY = 2
        const val SETTINGS_ID = 10
        const val RESTART_ACTIVITY_RESULT = 2
    }
}
