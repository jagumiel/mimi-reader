package com.emogoth.android.phone.mimi.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.MimiActivity
import com.emogoth.android.phone.mimi.activity.StartupActivity
import com.emogoth.android.phone.mimi.db.BoardTableConnection.fetchBoard
import com.emogoth.android.phone.mimi.db.DatabaseUtils.applySchedulers
import com.emogoth.android.phone.mimi.db.DatabaseUtils.applySingleSchedulers
import com.emogoth.android.phone.mimi.db.HistoryTableConnection.fetchHistory
import com.emogoth.android.phone.mimi.db.HistoryTableConnection.fetchPost
import com.emogoth.android.phone.mimi.db.HistoryTableConnection.observeHistory
import com.emogoth.android.phone.mimi.db.models.History
import com.emogoth.android.phone.mimi.fourchan.FourChanCommentParser
import com.emogoth.android.phone.mimi.interfaces.HistoryClickedListener
import com.emogoth.android.phone.mimi.interfaces.HomeButtonListener
import com.emogoth.android.phone.mimi.interfaces.TabEventListener
import com.emogoth.android.phone.mimi.prefs.SettingsActivity
import com.bumptech.glide.Glide
import com.emogoth.android.phone.mimi.util.LayoutType
import com.emogoth.android.phone.mimi.util.MimiPrefs.Companion.navDrawerBookmarkCount
import com.emogoth.android.phone.mimi.util.MimiUtil
import com.emogoth.android.phone.mimi.util.MediaThumbnail
import com.emogoth.android.phone.mimi.util.RxUtil
import com.emogoth.android.phone.mimi.view.DrawerViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mimireader.chanlib.models.ChanBoard
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class NavDrawerFragment : Fragment() {
    //    private var sharedPreferences: SharedPreferences? = null
    private val loginRow: TextView? = null
    private var themeId = 0
    private var themeColorId = 0
    private var notificationContainer: FrameLayout? = null
    private var noBookmarksContainer: View? = null
    private var bookmarksItemContainer: ViewGroup? = null
    private var currentBookmarks: List<History>? = null
    private var fontSizeId = 0
    private var layoutType: String? = null
    private var boardInfoSubscription: Disposable? = null
    private var bookmarkSubscription: Disposable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        retainInstance = false
        if (activity == null) {
            return null
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        themeId = sharedPreferences.getString(getString(R.string.theme_pref), "0")?.toInt() ?: 0
        themeColorId = sharedPreferences.getString(getString(R.string.theme_color_pref), "0")?.toInt()
                ?: 0
        fontSizeId = sharedPreferences.getString(getString(R.string.font_style_pref), "0")?.toInt()
                ?: 0
        layoutType = sharedPreferences.getString(getString(R.string.start_activity_pref), StartupActivity.getDefaultStartupActivity())
        return inflater.inflate(R.layout.fragment_nav_drawer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val settingsRow = view.findViewById<View>(R.id.settings_row)
        settingsRow.setOnClickListener { v: View? ->
            startActivityForResult(Intent(activity, SettingsActivity::class.java), MimiActivity.SETTINGS_ID)
            toggleDrawer()
        }
        notificationContainer = view.findViewById(R.id.notification_container)
        totalUnread(object : UnreadCountUpdate {
            override fun OnUnreadCountUpdate(count: Int) {
                if (activity != null) {
                    val act = activity as MimiActivity
                    val nc = notificationContainer
                    nc?.addView(MimiUtil.getInstance().createActionBarNotification(act.layoutInflater, nc, count))
                }
            }
        })
        val homeRow = view.findViewById<View>(R.id.home_row)
        homeRow.setOnClickListener { v: View? ->
            if (activity is HomeButtonListener) {
                (activity as HomeButtonListener).onHomeButtonClicked()
            }

            toggleDrawer()
        }
        val closeTabsContainer = view.findViewById<View>(R.id.close_tabs_container)
        if (MimiUtil.getLayoutType(activity) != LayoutType.TABBED) {
            closeTabsContainer.visibility = View.GONE
        } else {
            val closeTabsRow = view.findViewById<View>(R.id.close_tabs_row)
            closeTabsRow.setOnClickListener { v: View? -> closeAllTabs() }
        }
        val bookmarksRow = view.findViewById<View>(R.id.bookmarks_row)
        bookmarksRow.setOnClickListener { v: View? ->
            toggleDrawer()
            if (activity is HistoryClickedListener) {
                val listener = activity as HistoryClickedListener
                listener.openHistoryPage(true)
            }
        }
        bookmarksItemContainer = view.findViewById(R.id.bookmark_items)
        noBookmarksContainer = view.findViewById(R.id.no_bookmarks)
        val historyRow = view.findViewById<View>(R.id.history_row)
        historyRow.setOnClickListener { v: View? ->
            toggleDrawer()
            if (activity is HistoryClickedListener) {
                val listener = activity as HistoryClickedListener
                listener.openHistoryPage(false)
            }
        }

    }

    private fun toggleDrawer() {
        if (activity is MimiActivity) {
            (activity as MimiActivity).toggleNavDrawer()
        }
    }

    private fun closeAllTabs() {
        if (activity == null) {
            return
        }

        val inflater = LayoutInflater.from(activity)
        val dialogBuilder = MaterialAlertDialogBuilder(activity as Context)
        val dialogView = inflater.inflate(R.layout.dialog_close_tabs_prompt, null, false)
        val dontShow = dialogView.findViewById<View>(R.id.dialog_dont_show) as CheckBox
        val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val shouldShowDialog = pref.getBoolean(getString(R.string.close_all_tabs_prompt_pref), true)
        if (shouldShowDialog) {
            dialogBuilder.setTitle(R.string.close_all_tabs)
                    .setView(dialogView)
                    .setPositiveButton(R.string.yes) { dialog, which ->
                        pref.edit().putBoolean(getString(R.string.close_all_tabs_prompt_pref), !dontShow.isChecked).apply()
                        if (activity is TabEventListener) {
                            val act = activity as TabEventListener
                            act.onTabClosed(-1, "", "", true)
                        }

                        toggleDrawer()
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
        } else if (activity is TabEventListener) {
            val act = activity as TabEventListener
            act.onTabClosed(-1, "", "", true)

            toggleDrawer()
        }
    }

    private fun populateBookmarks() {
        if (activity == null || !isAdded) {
            return
        }
        val bookmarkCount = navDrawerBookmarkCount(activity as Context)
        RxUtil.safeUnsubscribe(bookmarkSubscription)
        bookmarkSubscription = observeHistory(true, bookmarkCount)
                .compose(applySchedulers())
                .subscribe({ bookmarks: List<History> ->
                    if (activity == null || !isAdded) {
                        return@subscribe
                    }
                    currentBookmarks = bookmarks
                    bookmarksItemContainer?.removeAllViews()
                    bookmarksItemContainer?.addView(noBookmarksContainer)
                    if (bookmarks.isNotEmpty()) {
                        notificationContainer?.visibility = View.VISIBLE
                        noBookmarksContainer?.visibility = View.GONE
                        for (i in bookmarks.indices) {
                            val bookmark = bookmarks[i]
                            bookmark.orderId = i
                            val row = (activity as FragmentActivity).layoutInflater.inflate(R.layout.bookmark_row_item, bookmarksItemContainer, false)
                            row.setOnClickListener { v: View? ->
                                sendBookmarkClickedEvent(bookmark)
                                toggleDrawer()
                            }
                            bookmarksItemContainer?.addView(createBookmarkRow(bookmark, row))
                        }
                    } else {
                        notificationContainer?.visibility = View.INVISIBLE
                        noBookmarksContainer?.visibility = View.VISIBLE
                    }

                    var unread = 0
                    for (history in bookmarks) {
                        unread += history.unreadCount
                    }

                    val act = activity as MimiActivity
                    val nc = notificationContainer
                    nc?.addView(MimiUtil.getInstance().createActionBarNotification(act.layoutInflater, nc, unread))


                }) { throwable: Throwable? -> Log.e(LOG_TAG, "Error loading bookmarks", throwable) }
    }

    private fun sendBookmarkClickedEvent(bookmark: History) {
        RxUtil.safeUnsubscribe(boardInfoSubscription)
        boardInfoSubscription = fetchBoard(bookmark.boardName)
                .compose(applySingleSchedulers())
                .subscribe { chanBoard: ChanBoard ->
                    if (!TextUtils.isEmpty(chanBoard.name)) {
                        if (activity is HistoryClickedListener) {
                            val act = activity as HistoryClickedListener
                            act.onHistoryItemClicked(chanBoard.name, bookmark.threadId, chanBoard.title, bookmark.orderId, bookmark.watched)
                        }
                    } else if (activity != null) {
                        Toast.makeText(activity, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                    }
                }
    }

    private fun createBookmarkRow(bookmark: History, row: View): View {
        val time = DateUtils.getRelativeTimeSpanString(
                bookmark.lastAccess,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE)
        val viewHolder = DrawerViewHolder(row)
        val parserBuilder = FourChanCommentParser.Builder()
        parserBuilder.setContext(activity)
                .setComment(bookmark.text)
                .setBoardName(bookmark.boardName)
                .setThreadId(bookmark.threadId)
                .setQuoteColor(MimiUtil.getInstance().quoteColor)
                .setReplyColor(MimiUtil.getInstance().replyColor)
                .setHighlightColor(MimiUtil.getInstance().highlightColor)
                .setLinkColor(MimiUtil.getInstance().linkColor)
        viewHolder.text.text = parserBuilder.build().parse()
        if (bookmark.watched) {
            if (bookmark.unreadCount > 0) {
                viewHolder.unreadcount.text = bookmark.unreadCount.toString()
                viewHolder.unreadcount.visibility = View.VISIBLE
            } else {
                viewHolder.unreadcount.visibility = View.GONE
            }
        } else {
            viewHolder.unreadcount.visibility = View.GONE
        }
        viewHolder.image.visibility = View.INVISIBLE
        if (!TextUtils.isEmpty(bookmark.tim)) {
            val thumbBaseUrl = activity?.getString(R.string.thumb_link)
            val thumbPath = activity?.getString(R.string.thumb_path, bookmark.boardName, bookmark.tim)
            val url = MimiUtil.https() + thumbBaseUrl + thumbPath
            MediaThumbnail.load(viewHolder.image, if (bookmark.boardName == "f") "" else url,
                    if (bookmark.boardName == "f") ".swf" else "")
            viewHolder.image.visibility = View.VISIBLE
        } else {
            viewHolder.image.visibility = View.INVISIBLE
            Glide.with(viewHolder.image).clear(viewHolder.image)
        }
        viewHolder.boardName.text = "/" + bookmark.boardName + "/"
        viewHolder.threadId.text = bookmark.threadId.toString()
        viewHolder.lastviewed.text = time
        return row
    }

    override fun onPause() {
        super.onPause()
        RxUtil.safeUnsubscribe(bookmarkSubscription)
        RxUtil.safeUnsubscribe(boardInfoSubscription)
    }

    override fun onResume() {
        super.onResume()

        populateBookmarks()

        if (activity == null) {
            return
        }
        val act = activity as Activity

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val themePref = sharedPreferences.getString(getString(R.string.theme_pref), "0")?.toInt()
                ?: 0
        val themeColorPref = sharedPreferences.getString(getString(R.string.theme_color_pref), "0")?.toInt()
                ?: 0
        if (themeId != themePref || themeColorPref != themeColorId) {
            themeId = themePref
            themeColorId = themeColorPref
            MimiUtil.getInstance().setCurrentTheme(themePref, themeColorPref)
            val intent = Intent(activity, act.javaClass)
            act.finish()
            startActivity(intent)

        }
        val fontSizePref = sharedPreferences.getString(getString(R.string.font_style_pref), "0")?.toInt()
                ?: 0
        if (fontSizeId != fontSizePref) {
            val intent = Intent(act, act.javaClass)
            act.finish()
            startActivity(intent)
        }
        val layoutTypePref = sharedPreferences.getString(getString(R.string.start_activity_pref), StartupActivity.getDefaultStartupActivity())
        if (!TextUtils.equals(layoutType, layoutTypePref)) {
            val intent = Intent(act, StartupActivity::class.java)
            act.finish()
            startActivity(intent)
        }
        if (loginRow != null && MimiUtil.getInstance().isLoggedIn) {
            loginRow.setText(R.string.chanpass_logout)
        }
    }

    private fun postUnread(boardName: String, threadId: Long, callback: UnreadCountUpdate) {
        fetchPost(boardName, threadId)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : SingleObserver<History> {
                    override fun onSubscribe(d: Disposable) {
                        // no op
                    }

                    override fun onSuccess(history: History) {
                        callback.OnUnreadCountUpdate(history.unreadCount)
                    }

                    override fun onError(e: Throwable) {
                        Log.e(LOG_TAG, "Error setting unread count badge", e)
                    }
                })
    }

    private fun totalUnread(callback: UnreadCountUpdate) {
        fetchHistory(true)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(object : SingleObserver<List<History>> {
                    override fun onSubscribe(d: Disposable) {
                        // no op
                    }

                    override fun onSuccess(histories: List<History>) {
                        var unread = 0
                        for (history in histories) {
                            unread += history.unreadCount
                        }
                        callback.OnUnreadCountUpdate(unread)
                    }

                    override fun onError(e: Throwable) {
                        Log.e(LOG_TAG, "Error setting unread count badge", e)
                    }
                })
    }

    override fun onDestroy() {
        super.onDestroy()
        RxUtil.safeUnsubscribe(bookmarkSubscription)
        RxUtil.safeUnsubscribe(boardInfoSubscription)
    }

    private interface UnreadCountUpdate {
        fun OnUnreadCountUpdate(count: Int)
    }

    companion object {
        private val LOG_TAG = NavDrawerFragment::class.java.simpleName
    }
}
