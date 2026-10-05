package com.emogoth.android.phone.mimi.fragment

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.emogoth.android.phone.mimi.BuildConfig
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.MimiActivity
import com.emogoth.android.phone.mimi.adapter.BoardListAdapter
import com.emogoth.android.phone.mimi.adapter.BoardListAdapter.OnBoardClickListener
import com.emogoth.android.phone.mimi.app.MimiApplication
import com.emogoth.android.phone.mimi.db.ArchivedPostTableConnection
import com.emogoth.android.phone.mimi.db.BoardTableConnection.convertBoardDbModelsToChanBoards
import com.emogoth.android.phone.mimi.db.BoardTableConnection.fetchBoard
import com.emogoth.android.phone.mimi.db.BoardTableConnection.fetchBoards
import com.emogoth.android.phone.mimi.db.BoardTableConnection.incrementAccessCount
import com.emogoth.android.phone.mimi.db.BoardTableConnection.observeBoards
import com.emogoth.android.phone.mimi.db.BoardTableConnection.saveBoards
import com.emogoth.android.phone.mimi.db.BoardTableConnection.setBoardVisibility
import com.emogoth.android.phone.mimi.db.DatabaseUtils.applySchedulers
import com.emogoth.android.phone.mimi.db.DatabaseUtils.applySingleSchedulers
import com.emogoth.android.phone.mimi.db.MimiDatabase.Companion.getInstance
import com.emogoth.android.phone.mimi.db.PostTableConnection
import com.emogoth.android.phone.mimi.db.models.Board
import com.emogoth.android.phone.mimi.fourchan.FourChanConnector
import com.emogoth.android.phone.mimi.interfaces.BoardItemClickListener
import com.emogoth.android.phone.mimi.interfaces.ContentInterface
import com.emogoth.android.phone.mimi.interfaces.IToolbarContainer
import com.emogoth.android.phone.mimi.interfaces.TabInterface
import com.emogoth.android.phone.mimi.util.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.mimireader.chanlib.ChanConnector
import com.mimireader.chanlib.models.ChanBoard
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.*

/**
 * A list fragment representing a list of PostItems. This fragment
 * also supports tablet devices by allowing list items to be given an
 * 'activated' state upon selection. This helps indicate which item is
 * currently being viewed in a [ThreadPagerFragment].
 *
 *
 * Activities containing this fragment MUST implement the [com.emogoth.android.phone.mimi.interfaces.BoardItemClickListener]
 * interface.
 */
class BoardItemListFragment
/**
 * Mandatory empty constructor for the fragment manager to instantiate the
 * fragment (e.g. upon screen orientation changes).
 */
    : MimiFragmentBase(), OnBoardClickListener, TabInterface, ContentInterface {
    /**
     * The fragment's current callback object, which is notified of list item
     * clicks.
     */
    private var mCallbacks: BoardItemClickListener? = null
    private var activateOnItemClick = false

    /**
     * The current activated item position. Only used on tablets.
     */
    private val mActivatedPosition = ListView.INVALID_POSITION
    private var boardsList: RecyclerView? = null
    private var boardListAdapter: BoardListAdapter? = null
    private var rootView: View? = null
    private var boardOrderContainer: ViewGroup? = null
    private var showContentButton: TextView? = null
    private var boardOrderText: TextView? = null
    private var orderTypeList: ViewGroup? = null
    private var orderByFavorites: TextView? = null
    private var orderByName: TextView? = null
    private var orderByTitle: TextView? = null
    private var orderByAccess: TextView? = null
    private var orderByLast: TextView? = null
    private var orderByPost: TextView? = null
    private var orderbyCustom: TextView? = null
    private var filterByAll: TextView? = null
    private var filterBySfw: TextView? = null
    private var filterByNsfw: TextView? = null
    private var boardOrderBackground: ViewGroup? = null
    private var toolbarSpinner: Spinner? = null
    private var errorView: View? = null
    private var errorSwitcher: BetterViewAnimator? = null
    private var revealListAnimation: Animation? = null
    private var showBoardOrderBackground: Animation? = null
    private var hideListAnimation: Animation? = null
    private var hideBoardOrderBackground: Animation? = null
    private var orderByNames: Array<String> = arrayOf("")
    private var filterNames: Array<String> = arrayOf("")
    private var allBoards: List<ChanBoard> = emptyList()
    private var boardOrderListVisible = false
    private var editMode = false
    private var toolbar: Toolbar? = null
    private var chanConnector: ChanConnector? = null

    private var itemTouchHelper: ItemTouchHelper? = null
    private var actionModeCallback: ActionMode.Callback? = null
    private var boardInfoSubscription: Disposable? = null
    private var fetchBoardsSubscription: Disposable? = null
    private var watchDatabaseSubscription: Disposable? = null
    private var boardFetchDisposable: Disposable? = null
    private var initDatabaseDisposable: Disposable? = null
    private var manageBoardsMenuItem: MenuItem? = null
    private var cachedContentSnackbar: Snackbar? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val userAgent = PreferenceManager.getDefaultSharedPreferences(activity).getString(getString(R.string.user_agent_pref), null)
        if (userAgent != null) {
            RequestQueueUtil.getInstance().userAgent = userAgent
        } else {
            try {
                val webView = WebView(activity as Context)
                val webViewAgent = webView.settings.userAgentString
                PreferenceManager.getDefaultSharedPreferences(activity).edit().putString(getString(R.string.user_agent_pref), webViewAgent).apply()
                webView.destroy()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Caught exception", e)
                if (PreferenceManager.getDefaultSharedPreferences(activity).getString(getString(R.string.user_agent_pref), null) == null) {
                    val defaultAgent = System.getProperty("http.agent")
                    PreferenceManager.getDefaultSharedPreferences(activity).edit().putString(getString(R.string.user_agent_pref), defaultAgent).apply()
                }
            }
        }
        if (activity is MimiActivity) {
            toolbar = (activity as MimiActivity).toolbar
        }
        rootView = inflater.inflate(R.layout.fragment_boards_list, container, false)
        errorSwitcher = rootView?.findViewById(R.id.error_switcher)
        boardListAdapter = BoardListAdapter(activity as Context, ArrayList())
        val layoutManager = LinearLayoutManager(activity)
        boardsList = rootView?.findViewById(R.id.boards_list)
        boardsList?.addItemDecoration(DividerItemDecoration(boardsList?.context, RecyclerView.VERTICAL))
        boardsList?.layoutManager = layoutManager
        boardsList?.adapter = boardListAdapter
        boardListAdapter?.setDragListener(object : BoardListAdapter.OnStartDragListener {
            override fun onStartDrag(viewHolder: RecyclerView.ViewHolder?) {
                if (viewHolder != null) {
                    itemTouchHelper?.startDrag(viewHolder)
                }
            }
        })
        boardListAdapter?.itemLongClickListener = AdapterView.OnItemLongClickListener { parent, view, position, id ->
            if (activity != null) {
                activity?.startActionMode(actionMode)
            }
            true
        }

        return rootView
    }

    private val actionMode: ActionMode.Callback
        get() {
            editMode = true
            boardListAdapter?.editMode(true)
            if (actionModeCallback == null) {
                val callback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                        if (activity !is MimiActivity) {
                            return false
                        }
                        val act = activity as MimiActivity
                        val inflater = mode.menuInflater
                        inflater.inflate(R.menu.edit_boards, menu)
                        if (act.drawerLayout != null) {
                            act.drawerLayout?.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                        }
                        toolbar?.visibility = View.GONE
                        boardOrderContainer?.visibility = View.GONE
                        boardsList?.isClickable = false
                        boardListAdapter?.boardClickListener = null
                        boardListAdapter?.boards = ArrayList(allBoards)
                        mode.setTitle(R.string.manage_boards)

                        return act.onCreateActionMode(mode, menu)
                    }

                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                        return false
                    }

                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                        if (item.itemId == R.id.add_board) {
                            showAddBoardDialog()
                        }
                        return true
                    }

                    override fun onDestroyActionMode(mode: ActionMode) {
                        if (activity == null || activity !is MimiActivity) {
                            return
                        }
                        val act = activity as MimiActivity
                        editMode = false
                        act.drawerLayout?.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                        toolbar?.visibility = View.VISIBLE
                        boardListAdapter?.editMode(false)
                        allBoards = ArrayList(boardListAdapter?.boards ?: emptyList())
                        displayBoards()
                        boardsList?.isClickable = true
                        boardListAdapter?.boardClickListener = this@BoardItemListFragment
                        boardOrderContainer?.visibility = View.VISIBLE
                        val order = MimiUtil.getBoardOrder()

                        if (boardOrderText != null) {
                            val bo = boardOrderText as TextView
                            bo.text = sortSummary(order)
                        }

                        act.onDestroyActionMode(mode)
                    }
                }
                actionModeCallback = callback
                return callback
            } else {
                return actionModeCallback as ActionMode.Callback
            }
        }

    private fun showAddBoardDialog() {
        if (activity == null) {
            return
        }
        val alertBuilder = MaterialAlertDialogBuilder(activity as Context)
        val input = EditText(activity)
        input.setHint(R.string.board_name_input_hint)
        input.setSingleLine()
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        alertBuilder.setView(input)
        alertBuilder.setPositiveButton(R.string.add) { _: DialogInterface?, _: Int -> addBoard(input.text.toString()) }
        alertBuilder.setTitle(R.string.add_board)
        val d = alertBuilder.create()
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show()
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addBoard(input.text.toString())
                d.dismiss()
            }
            true
        }
    }

    private fun addBoard(rawBoardName: String) {
        if (TextUtils.isEmpty(rawBoardName)) {
            return
        }
        if (errorSwitcher != null) {
            val switcher = errorSwitcher as BetterViewAnimator
            switcher.displayedChildId = boardsList?.id ?: 0
        }

        RxUtil.safeUnsubscribe(boardInfoSubscription)
        val boardName = rawBoardName.replace("/".toRegex(), "").lowercase(Locale.ROOT).trim { it <= ' ' }
        boardInfoSubscription = fetchBoard(boardName)
                .flatMap { chanBoard: ChanBoard -> setBoardVisibility(chanBoard, true) }
                .flatMap { _ ->
                    val orderId = MimiUtil.getBoardOrder()
                    fetchBoards(orderId)
                }
                .flatMap { boards: List<Board> -> Single.just(convertBoardDbModelsToChanBoards(boards)) }
                .onErrorReturn { Collections.emptyList() }
                .compose(applySingleSchedulers())
                .subscribe { boards: List<ChanBoard> ->
                    if (boards.isNotEmpty()) {
                        updateBoardsAdapter(boards)
                        if (manageBoardsMenuItem != null) {
                            manageBoardsMenuItem?.isEnabled = true
                        }
                    } else {
                        showError()
                    }
                }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (activity == null) {
            return
        }
        setupHeader(view)
        if (userVisibleHint) {
            initMenu()
        }
        setupTouchListeners()
        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val lastVersion = preferences.getInt(getString(R.string.last_version_code_pref), 0)
        if (BuildConfig.VERSION_CODE > lastVersion) {
            showChangeLog()

            // TODO: Remove the removeAllThreads() calls
            Single.defer {
                val timer = System.currentTimeMillis()
                Log.d(LOG_TAG, "Starting clearing cache...")
                MimiUtil.deleteRecursive(activity?.cacheDir, true)
                Log.d(LOG_TAG, "Cache cleared in " + (System.currentTimeMillis() - timer) + " ms")
                Single.just(true)
            }
                    .flatMap { PostTableConnection.removeAllThreads() }
                    .flatMap { ArchivedPostTableConnection.removeAllThreads() }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .onErrorReturn { throwable: Throwable ->
                        Log.e(LOG_TAG, "Cache could not be cleared on app upgrade", throwable)
                        false
                    }
                    .subscribe()
            preferences.edit().putInt(getString(R.string.last_version_code_pref), BuildConfig.VERSION_CODE).apply()
        }
        chanConnector = FourChanConnector.Builder()
                .setCacheDirectory(MimiUtil.getInstance().cacheDir)
                .setEndpoint(FourChanConnector.getDefaultEndpoint())
                .setClient(HttpClientFactory.getInstance().client)
                .setPostEndpoint(FourChanConnector.getDefaultPostEndpoint())
                .build<ChanConnector>()
        boardListAdapter?.boardClickListener = this
        initDatabase()
        getInstance()?.boards()?.getVisibleBoards()?.subscribe(object : Subscriber<List<Board>> {
            override fun onSubscribe(s: Subscription) {}
            override fun onNext(boards: List<Board>) {
                for ((_, _, name) in boards) {
                    Log.d(LOG_TAG, "Board: $name")
                }
            }

            override fun onError(t: Throwable) {
                Log.e(LOG_TAG, "Error fetching boards using Room", t)
            }

            override fun onComplete() {
                Log.d(LOG_TAG, "Finished fetching boards")
            }
        })
    }

    private fun initDatabase() {
        if (chanConnector == null) {
            return
        }

        val connector = chanConnector as ChanConnector
        RxUtil.safeUnsubscribe(initDatabaseDisposable)
        initDatabaseDisposable = fetchBoards(MimiUtil.getBoardOrder())
                .map { b: List<Board> -> convertBoardDbModelsToChanBoards(b) }
                .flatMap {
                    if (it.isEmpty()) {
                        Log.d(LOG_TAG, "Fetching all boards for debug version")
                        connector.fetchBoards()
                                .observeOn(Schedulers.io())
                                .doOnSuccess(saveBoards())
                                .toFlowable()
                                .flatMapIterable { list -> list }
                                .doOnNext { chanBoard: ChanBoard -> Log.d(LOG_TAG, "Setting visibility for " + chanBoard.title) }
                                .flatMap { chanBoard: ChanBoard -> setBoardVisibility(chanBoard, if (BuildConfig.SHOW_ALL_BOARDS) true else isDefaultBoard(chanBoard.name)).toFlowable() }
                                .toList()
                    } else {
                        Single.just(it)
                    }
                }
                .compose(applySingleSchedulers())
                .subscribe({ chanBoards: List<ChanBoard> ->
                    Log.d(LOG_TAG, "Fetching boards was a success; starting database watch: boards=" + chanBoards.size)
                    watchDatabase()
                    if (chanBoards.isNotEmpty()) {
                        loadBoards()
                    } else {
                        showError()
                    }
                }) { throwable: Throwable? ->
                    Log.e(LOG_TAG, "Error while initializing database with boards", throwable)
                    watchDatabase()
                    showError()
                }
    }

    private fun isDefaultBoard(boardName: String): Boolean {
        val boards = MimiApplication.instance.resources.getStringArray(R.array.boards)
        for (board in boards) {
            if (board == boardName) {
                return true
            }
        }

        return false
    }

    private fun loadBoards() {
        if (chanConnector == null) {
            return
        }

        val connector = chanConnector as ChanConnector

        RxUtil.safeUnsubscribe(boardFetchDisposable)
        boardFetchDisposable = connector.fetchBoards()
                .observeOn(Schedulers.io())
                .doOnSuccess(saveBoards())
                .compose(applySingleSchedulers())
                .subscribe({
                    dismissCachedContentWarning()
                }, { throwable ->
                    Log.e(LOG_TAG, "Error while fetching list of boards from the network", throwable)
                    if (allBoards.isNotEmpty()) {
                        showCachedContent(throwable)
                    } else {
                        showError(throwable)
                    }
                })
    }

    private fun watchDatabase() {
        errorSwitcher?.displayedChildId = boardsList?.id ?: 0
        RxUtil.safeUnsubscribe(watchDatabaseSubscription)
        watchDatabaseSubscription = observeBoards(MimiUtil.getBoardOrder())
                .first(emptyList())
                .compose(applySingleSchedulers())
                .subscribe { chanBoards: List<ChanBoard> ->
                    if (chanBoards.isEmpty() || TextUtils.isEmpty(chanBoards[0].title)) {
                        return@subscribe
                    }
                    if (manageBoardsMenuItem != null) {
                        manageBoardsMenuItem?.isEnabled = true
                    }
                    if (boardsList != null) {
                        boardOrderContainer?.visibility = View.VISIBLE
                        updateBoardsAdapter(chanBoards)
                        errorSwitcher?.displayedChildId = boardsList?.id ?: 0
                    }
                }
    }

    private fun showError(error: Throwable? = null) {
        dismissCachedContentWarning()
        if (manageBoardsMenuItem != null) {
            manageBoardsMenuItem?.isEnabled = false
        }
        val messageResource = error?.let(NetworkErrorMessage::resourceFor) ?: R.string.error_loading_boards
        if (errorView != null) {
            errorView?.findViewById<TextView>(R.id.error_message)?.setText(messageResource)
            errorSwitcher?.displayedChildId = errorView?.id ?: 0
            return
        }
        val errorStub = rootView?.findViewById<ViewStub>(R.id.error_container)
        errorStub?.setOnInflateListener { _: ViewStub?, view: View ->
            view.findViewById<TextView>(R.id.error_message).setText(messageResource)
            view.findViewById<View>(R.id.retry_button).setOnClickListener { loadBoards() }
            errorSwitcher?.displayedChildId = view.id
            errorView = view
        }
        errorStub?.inflate()
    }

    private fun showCachedContent(error: Throwable) {
        errorSwitcher?.displayedChildId = boardsList?.id ?: 0
        dismissCachedContentWarning()
        cachedContentSnackbar = rootView?.let {
            Snackbar.make(it, R.string.cached_content_warning, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.retry) { loadBoards() }
                    .also(Snackbar::show)
        }
        Log.w(LOG_TAG, "Showing cached boards after a recoverable refresh error", error)
    }

    private fun dismissCachedContentWarning() {
        cachedContentSnackbar?.dismiss()
        cachedContentSnackbar = null
    }

    private fun setupTouchListeners() {
        val simpleItemTouchCallback: ItemTouchHelper.SimpleCallback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            var mCurrentTarget: RecyclerView.ViewHolder? = null
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                if (boardListAdapter?.IsEditMode() == true) {
                    mCurrentTarget = target
                    boardListAdapter?.onItemMove(viewHolder.absoluteAdapterPosition, target.absoluteAdapterPosition)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                boardListAdapter?.onDismiss(viewHolder.absoluteAdapterPosition)
            }

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                return if (boardListAdapter?.IsEditMode() == true) super.getSwipeDirs(recyclerView, viewHolder) else 0
            }

            override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                return if (boardListAdapter?.IsEditMode() == true) super.getDragDirs(recyclerView, viewHolder) else 0
            }
        }
        itemTouchHelper = ItemTouchHelper(simpleItemTouchCallback)
        itemTouchHelper?.attachToRecyclerView(boardsList)
    }

    override fun onBoardClick(board: ChanBoard) {
        incrementAccessCount(board.name)
                .compose(applySingleSchedulers())
                .subscribe()
        mCallbacks?.onBoardItemClick(board, true)
    }

    override fun initMenu() {
        super.initMenu()
        if (toolbar != null) {
            setupToolBar()
            if (activity != null) {
                activity?.invalidateOptionsMenu()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        RxUtil.safeUnsubscribe(initDatabaseDisposable)
        RxUtil.safeUnsubscribe(fetchBoardsSubscription)
        RxUtil.safeUnsubscribe(watchDatabaseSubscription)
        RxUtil.safeUnsubscribe(boardInfoSubscription)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        // Activities containing this fragment must implement its callbacks.
        check(context is BoardItemClickListener) { "Activity must implement fragment's callbacks." }
        mCallbacks = context
    }

    private fun showManageBoardsTutorial() {
        if (activity == null) {
            return
        }

        val inflater = LayoutInflater.from(activity)
        val dialogBuilder = MaterialAlertDialogBuilder(activity as Context)
        val dialogView = inflater.inflate(R.layout.dialog_manage_boards_tutorial, null, false)
        val dontShow = dialogView.findViewById<CheckBox>(R.id.manage_boards_dont_show)
        dialogBuilder.setTitle(R.string.manage_boards)
                .setView(dialogView)
                .setPositiveButton(R.string.ok) { dialog: DialogInterface?, which: Int ->
                    if (activity != null) {
                        val pref = PreferenceManager.getDefaultSharedPreferences(activity)
                        pref.edit().putBoolean(getString(R.string.show_manage_boards_tutorial), !dontShow.isChecked).apply()
                    }
                }
                .show()
    }

    private fun showChangeLog() {
        if (isAdded) {
            LicensesFragment.displayLicensesFragment(activity?.supportFragmentManager, R.raw.changelog, "ChangeLog")
        }
    }

    private fun setupHeader(rootView: View) {
        showContentButton = rootView.findViewById(R.id.board_header_show_content)
        boardOrderText = rootView.findViewById(R.id.board_order_subtitle)
        orderTypeList = rootView.findViewById(R.id.board_order_content)
        orderByFavorites = rootView.findViewById(R.id.board_order_type_favorite)
        orderByName = rootView.findViewById(R.id.board_order_type_name)
        orderByTitle = rootView.findViewById(R.id.board_order_type_title)
        orderByAccess = rootView.findViewById(R.id.board_order_type_access_count)
        orderByLast = rootView.findViewById(R.id.board_order_type_last_access)
        orderByPost = rootView.findViewById(R.id.board_order_type_post_count)
        orderbyCustom = rootView.findViewById(R.id.board_order_type_custom)
        filterByAll = rootView.findViewById(R.id.board_filter_all)
        filterBySfw = rootView.findViewById(R.id.board_filter_sfw)
        filterByNsfw = rootView.findViewById(R.id.board_filter_nsfw)
        val orderBackground = rootView.findViewById<ViewGroup>(R.id.board_order_background)
        if (orderBackground != null) {
            orderBackground.setOnClickListener { v: View? -> hideList(NO_ORDER_SELECTED) }
            boardOrderBackground = orderBackground
        }
        orderByNames = resources.getStringArray(R.array.orderbyName)
        filterNames = resources.getStringArray(R.array.boardFilterName)
        val boardOrder = MimiUtil.getBoardOrder()
        showBoardOrderBackground = AlphaAnimation(0f, 1f)
        showBoardOrderBackground?.duration = 400
        revealListAnimation = AnimationUtils.loadAnimation(activity, R.anim.board_order_slide_down)
        hideListAnimation = AnimationUtils.loadAnimation(activity, R.anim.board_order_slide_up)
        hideBoardOrderBackground = AlphaAnimation(1f, 0f)
        hideBoardOrderBackground?.duration = 400
        revealListAnimation?.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                orderTypeList?.setVisibility(View.VISIBLE)
            }

            override fun onAnimationEnd(animation: Animation) {}
            override fun onAnimationRepeat(animation: Animation) {}
        })
        showBoardOrderBackground?.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                boardOrderBackground?.setVisibility(View.VISIBLE)
            }

            override fun onAnimationEnd(animation: Animation) {}
            override fun onAnimationRepeat(animation: Animation) {}
        })
        hideListAnimation?.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                orderTypeList?.setVisibility(View.GONE)
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        hideBoardOrderBackground?.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                boardOrderBackground?.setVisibility(View.GONE)
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        boardOrderContainer = rootView.findViewById(R.id.board_order_container)
        boardOrderText?.text = sortSummary(boardOrder)
        orderByFavorites?.setOnClickListener { hideList(6) }
        orderByName?.setOnClickListener { hideList(2) }
        orderByTitle?.setOnClickListener { hideList(1) }
        orderByAccess?.setOnClickListener { hideList(3) }
        orderByLast?.setOnClickListener { hideList(5) }
        orderByPost?.setOnClickListener { hideList(4) }
        orderbyCustom?.setOnClickListener { hideList(7) }
        filterByAll?.setOnClickListener { selectFilter(BoardFilter.ALL) }
        filterBySfw?.setOnClickListener { selectFilter(BoardFilter.SFW_ONLY) }
        filterByNsfw?.setOnClickListener { selectFilter(BoardFilter.NSFW_ONLY) }
        boardOrderContainer?.setOnClickListener {
            if (orderTypeList?.visibility == View.VISIBLE) {
                hideList(NO_ORDER_SELECTED)
            } else {
                showList()
            }
        }
    }

    private fun showList() {
        boardOrderListVisible = true
        orderTypeList?.startAnimation(revealListAnimation)
        boardOrderBackground?.startAnimation(showBoardOrderBackground)
        showContentButton?.setText(R.string.ic_content_shown)
    }

    private fun hideList(index: Int) {
        boardOrderListVisible = false
        if (index >= 0) {
            orderList(index)
            boardOrderText?.text = sortSummary(index)
        }
        orderTypeList?.startAnimation(hideListAnimation)
        boardOrderBackground?.startAnimation(hideBoardOrderBackground)
        showContentButton?.setText(R.string.ic_content_hidden)
    }

    private fun orderList(orderType: Int) {
        if (activity != null) {
            MimiUtil.setBoardOrder(activity, orderType)
            errorSwitcher?.displayedChildId = boardsList?.id ?: 0
            RxUtil.safeUnsubscribe(fetchBoardsSubscription)
            fetchBoardsSubscription = fetchBoards(orderType)
                    .flatMap { boards: List<Board> -> Single.just<List<ChanBoard>>(convertBoardDbModelsToChanBoards(boards)) }
                    .compose(applySingleSchedulers())
                    .subscribe { orderedBoards: List<ChanBoard> ->
                        updateBoardsAdapter(orderedBoards)
                    }
        }
    }

    private fun updateBoardsAdapter(updatedBoards: List<ChanBoard>) {
        allBoards = ArrayList(updatedBoards)
        displayBoards()
        if (manageBoardsMenuItem != null) {
            manageBoardsMenuItem?.isEnabled = true
        }
    }

    private fun displayBoards() {
        val displayedBoards = if (editMode) allBoards else BoardFilter.apply(allBoards, MimiUtil.getBoardFilter())
        if (boardListAdapter != null) {
            boardListAdapter?.boards = ArrayList(displayedBoards)
        } else if (activity is MimiActivity) {
            val act = activity as MimiActivity
            boardListAdapter = BoardListAdapter(act, ArrayList(displayedBoards))
        }
    }

    private fun selectFilter(filter: Int) {
        val context = activity ?: return
        MimiUtil.setBoardFilter(context, filter)
        displayBoards()
        boardOrderText?.text = sortSummary(MimiUtil.getBoardOrder())
        hideList(NO_ORDER_SELECTED)
    }

    private fun sortSummary(order: Int): String {
        val orderName = orderByNames.getOrElse(order) { orderByNames[0] }
        val filter = BoardFilter.normalize(MimiUtil.getBoardFilter())
        val filterName = filterNames.getOrElse(filter) { filterNames[BoardFilter.ALL] }
        return getString(R.string.board_sort_summary, orderName, filterName)
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser) {
            if (activity is IToolbarContainer) {
                val toolbarContainer = activity as IToolbarContainer?
                toolbarContainer?.setExpandedToolbar(true, true)
            }
        }
    }

    override fun onDestroyView() {
        dismissCachedContentWarning()
        super.onDestroyView()
    }

    override fun onBackPressed(): Boolean {
        if (boardOrderListVisible) {
            hideList(NO_ORDER_SELECTED)
            return true
        }
        return super.onBackPressed()
    }

    private fun setupToolBar() {
        if (activity is MimiActivity) {
            val activity = activity as MimiActivity?
            activity?.supportActionBar?.setTitle(R.string.app_name)
            activity?.supportActionBar?.subtitle = null
        }
        toolbarSpinner = toolbar?.findViewById(R.id.board_spinner)
        if (toolbarSpinner != null) {
            toolbarSpinner?.visibility = View.GONE
        }
    }

    override fun getMenuRes(): Int {
        return R.menu.board_list
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(menuRes, menu)
        manageBoardsMenuItem = menu.findItem(R.id.manage_boards_menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.manage_boards_menu && activity != null) {
            activity?.startActionMode(actionMode)
        }
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition)
        }
    }

    /**
     * Turns on activate-on-click mode. When this mode is on, list items will be
     * given the 'activated' state when touched.
     */
    fun setActivateOnItemClick(activateOnItemClick: Boolean) {
        // When setting CHOICE_MODE_SINGLE, ListView will automatically
        // give items the 'activated' state when touched.
        this.activateOnItemClick = activateOnItemClick
    }

    override fun showFab(): Boolean {
        return true
    }

    override fun getTitle(): String {
        return ""
    }

    override fun getSubtitle(): String {
        return ""
    }

    override fun getPageName(): String {
        return "board_list"
    }

    override fun getTabId(): Int {
        return TAB_ID
    }

    override fun addContent() {
        showAddBoardDialog()
    }

    companion object {
        /**
         * The serialization (saved instance state) Bundle key representing the
         * activated item position. Only used on tablets.
         */
        private const val STATE_ACTIVATED_POSITION = "activated_position"
        private val LOG_TAG = BoardItemListFragment::class.java.simpleName
        private const val NO_ORDER_SELECTED = -1
        const val TAB_ID = 100
    }
}
