package com.emogoth.android.phone.mimi.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.contains
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.app.MimiApplication
import com.emogoth.android.phone.mimi.databinding.ActivityGallery2Binding
import com.emogoth.android.phone.mimi.fourchan.FourChanConnector
import com.emogoth.android.phone.mimi.service.DownloadService
import com.emogoth.android.phone.mimi.util.*
import com.emogoth.android.phone.mimi.util.GalleryScrollReceiver.Companion.SCROLL_ID_FLAG
import com.emogoth.android.phone.mimi.view.gallery.GalleryGrid
import com.emogoth.android.phone.mimi.view.gallery.GalleryPager
import com.emogoth.android.phone.mimi.viewmodel.GalleryItem
import com.emogoth.android.phone.mimi.viewmodel.GalleryViewModel
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import com.mimireader.chanlib.ChanConnector
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class GalleryActivity2 : AppCompatActivity() {
    private lateinit var binding: ActivityGallery2Binding
    private val gallery_root get() = binding.galleryRoot
    private val appBarLayout get() = binding.appBarLayout
    private val toolbar get() = binding.toolbar

    companion object {
        private val LOG_TAG = GalleryActivity2::class.java.simpleName
        const val GALLERY_TYPE_GRID = 0
        const val GALLERY_TYPE_PAGER = 1
        const val GALLERY_TYPE_BOTH = 2

        const val REQUEST_CODE_DIR_CHOOSER = 43
        const val REQUEST_CODE_BATCH_DOWNLOAD = 42

        const val EXTRA_SELECTED_ITEMS = "selected_items_list"
        const val EXTRA_FROM_REPLY = "from_reply"

        @JvmStatic
        fun start(context: Context, galleryType: Int, postId: Long, boardPath: String, threadId: Long, postIds: LongArray = LongArray(0)) {
            val args = Bundle()

            args.putInt(Extras.EXTRAS_GALLERY_TYPE, galleryType)
            args.putString(Extras.EXTRAS_BOARD_NAME, boardPath)
            args.putLong(Extras.EXTRAS_THREAD_ID, threadId)
            args.putLong(Extras.EXTRAS_POST_ID, postId)

            if (postIds.isNotEmpty()) {
                args.putLongArray(Extras.EXTRAS_POST_LIST, postIds)
                args.putBoolean(EXTRA_FROM_REPLY, true)
            }

            val intent = Intent(context, GalleryActivity2::class.java)
            intent.putExtras(args)

            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)

            context.startActivity(intent)
        }
    }

    private var threadDisposable: Disposable? = null
    private var menu: Menu? = null
    private var actionMode: ActionMode? = null

    private var galleryPager: GalleryPager? = null
    private var galleryGrid: GalleryGrid? = null

    private var viewModel: GalleryViewModel = GalleryViewModel.empty()
//    private val selectedItems: ArrayList<Long> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme.applyStyle(MimiUtil.getFontStyle(this), true)

        binding = ActivityGallery2Binding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(toolbar)

        toolbar.setNavigationIcon(R.drawable.ic_nav_arrow_back)
        toolbar.setNavigationOnClickListener { finish() }

        val chanConnector = FourChanConnector.Builder()
                .setCacheDirectory(MimiUtil.getInstance().cacheDir)
                .setEndpoint(FourChanConnector.getDefaultEndpoint())
                .setPostEndpoint(FourChanConnector.getDefaultPostEndpoint())
                .setClient(HttpClientFactory.getInstance().client)
                .build<ChanConnector>()

        val prefs = PreferenceManager.getDefaultSharedPreferences(MimiApplication.instance)
        val audioLock = prefs.getBoolean(getString(R.string.webm_audio_lock_pref), false)

        viewModel = GalleryViewModel.get(this, chanConnector.imageBaseUrl, audioLock)
        viewModel.initState(savedInstanceState ?: intent.extras ?: Bundle())

        if (viewModel.threadId == -1L) {
            Log.v(LOG_TAG, "Invalid thread parameters: board: ${viewModel.boardName}, thread: ${viewModel.threadId}")
            Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show()
            finish()
        }

//        this.selectedItems.addAll(Collections.list())

        val vm = viewModel
        val obs = if (vm.postIds.isNotEmpty()) vm.getGalleryItemsFromList(vm.postIds) else vm.fetchThread()

        threadDisposable = obs
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { galleryItems ->
                    val position = if (vm.position >= 0) vm.position else MimiUtil.findGalleryItemPositionById(vm.postId, galleryItems)
                    when (vm.galleryType) {
                        GALLERY_TYPE_PAGER -> showPager(galleryItems, position, savedInstanceState != null, vm.fullScreen)
                        GALLERY_TYPE_GRID -> showGrid(galleryItems, position)
                        GALLERY_TYPE_BOTH -> {
                            showGrid(galleryItems, position)
                            showPager(galleryItems, position, savedInstanceState != null, vm.fullScreen)
                        }
                    }
                }
    }

    private fun showPager(galleryItems: List<GalleryItem>, position: Int, savedState: Boolean, fullScreen: Boolean = false) {
        val gp = (if (galleryPager == null) {
            val g = GalleryPager(this)
            val params = CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.MATCH_PARENT, CoordinatorLayout.LayoutParams.MATCH_PARENT)
            params.anchorGravity = Gravity.BOTTOM
            params.anchorId = appBarLayout.id
            params.behavior = AppBarLayout.ScrollingViewBehavior()
            g.layoutParams = params
            lifecycle.addObserver(g)
            g
        } else {
            galleryPager
        }) ?: return

        gp.setViewModel(viewModel)
        gp.setItems(galleryItems)
//        val pos: Int = if (adjustedPosition) gp.getAdjustedPagerPosition(position) else position
        if (savedState) gp.position = position else gp.setInitialPosition(position)
        gp.fullScreenListener {
            if (!it) {
                toolbar.visibility = View.VISIBLE
            }
        }

        gp.gridButtonListener = {
            showGrid(galleryItems, gp.position)
        }

        val fromReply = intent.extras?.getBoolean(EXTRA_FROM_REPLY, false) ?: false
        if (MimiPrefs.scrollThreadWithGallery(this) && !fromReply) {
            gp.pageChangeCallback = { pos ->
                if (pos >= 0) {
                    val intent = Intent()
                    intent.setAction(GalleryScrollReceiver.createIntentFilter(viewModel.boardName, viewModel.threadId))
                    intent.putExtra(SCROLL_ID_FLAG, galleryItems[pos].id)

                    sendBroadcast(intent)
//                    BusProvider.getInstance().post(GalleryPagerScrolledEvent(galleryItems[pos].id))
                }
            }
        }

        switchMenu(GALLERY_TYPE_PAGER, menu)
        galleryPager = gp

        if (fullScreen) {
            toolbar.visibility = View.GONE
            gp.fullScreen(true)
        }

        if (gallery_root.contains(gp)) {
            gallery_root.removeView(gp)
        }
        gallery_root.addView(galleryPager)

        viewModel.galleryType = if (galleryGrid != null) {
            GALLERY_TYPE_BOTH
        } else {
            GALLERY_TYPE_PAGER
        }
    }

    private fun showGrid(galleryItems: List<GalleryItem>, position: Int) {
        val gg = (if (galleryGrid == null) {
            val g = GalleryGrid(this)
            val params = CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.MATCH_PARENT, CoordinatorLayout.LayoutParams.MATCH_PARENT)
            params.anchorGravity = Gravity.BOTTOM
            params.anchorId = appBarLayout.id
            params.behavior = AppBarLayout.ScrollingViewBehavior()
            g.layoutParams = params
            g
        } else {
            galleryGrid
        }) ?: return
        val gp = galleryPager

        toolbar.visibility = View.VISIBLE

        if (gp != null && gallery_root.contains(gp)) {
            gallery_root.removeView(gp)
            lifecycle.removeObserver(gp)
            gp.release()
            galleryPager = null
        }

        if (!galleryItems.isEmpty()) {
            gg.items = galleryItems
        }

        if (position >= 0) {
            gg.position = position
        }

        gg.itemSelected = { item: GalleryItem, selected: Boolean ->
            if (gg.mode == GalleryGrid.MODE_OPEN) toolbar.startActionMode(initActionMode())
            val pos = viewModel.selectedItems.indexOf(item)
            if (selected) {
                if (pos < 0) {
                    viewModel.selectedItems.add(item)
                }
            } else {
                if (pos >= 0) {
                    viewModel.selectedItems.remove(item)
                }
            }
        }

        gg.multipleItemsSelected = {
            viewModel.selectedItems.clear()
            if (!it.isEmpty()) {
                viewModel.selectedItems.addAll(it)
            }
        }

        gg.itemClicked = { _: View, item: GalleryItem ->
            val items = gg.items
            val pos = items.indexOf(item)
            showPager(items, pos, false)
        }

        switchMenu(GALLERY_TYPE_GRID, menu)

        if (!gallery_root.contains(gg)) {
            galleryGrid = gg
            gallery_root.addView(galleryGrid)
        }

        viewModel.galleryType = GALLERY_TYPE_GRID
    }

    private fun initActionMode(): ActionMode.Callback {
        return object : ActionMode.Callback {
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                when (item?.itemId) {
                    R.id.select_all -> {
                        galleryGrid?.selectItems(true)
                    }
                    R.id.select_none -> {
                        galleryGrid?.selectItems(false)
                    }
                    R.id.invert -> {
                        galleryGrid?.invertSelection()
                    }
                    R.id.download -> {
                        chooseSaveLocation(REQUEST_CODE_BATCH_DOWNLOAD)
                    }
                }
                return true
            }

            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                galleryGrid?.mode = GalleryGrid.MODE_SELECT
                mode?.menuInflater?.inflate(R.menu.batch_download, menu)
                actionMode = mode
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                galleryGrid?.selectItems(false)
                galleryGrid?.mode = GalleryGrid.MODE_OPEN
                actionMode = null
            }
        }
    }

    private fun visibleGalleryViews(): Int {
        var hasPager = false
        if (galleryPager != null) {
            val gp: GalleryPager = galleryPager as GalleryPager
            if (gallery_root.contains(gp)) {
                hasPager = true
            }
        }

        var hasGrid = false
        if (galleryGrid != null) {
            val gg: GalleryGrid = galleryGrid as GalleryGrid
            if (gallery_root.contains(gg)) {
                hasGrid = true
            }
        }

        return if (hasPager && hasGrid) {
            GALLERY_TYPE_BOTH
        } else if (hasPager) {
            GALLERY_TYPE_PAGER
        } else if (hasGrid) {
            GALLERY_TYPE_GRID
        } else {
            GALLERY_TYPE_PAGER
        }
    }

    private fun selectedItemPositions(): ArrayList<Int> {
        val hasSelectedItems = !(galleryGrid?.getSelectedItems()?.isEmpty() ?: true)
        if (hasSelectedItems) {
            val selectedList: ArrayList<Int> = ArrayList()
            val items = galleryGrid?.getSelectedItems() ?: Collections.emptyList()

            for (i in 0 until items.size) {
                val pos = galleryGrid?.items?.indexOf(items[i]) ?: -1
                if (pos >= 0) {
                    selectedList.add(pos)
                }
            }

            return selectedList
        }

        return ArrayList()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val type = visibleGalleryViews()
        val position = when (type) {
            GALLERY_TYPE_PAGER -> galleryPager?.position ?: 0
            GALLERY_TYPE_GRID -> galleryGrid?.position ?: 0
            else -> galleryPager?.position ?: 0
        }
        viewModel.galleryType = type
        viewModel.position = position
        viewModel.fullScreen = (type == GALLERY_TYPE_PAGER || type == GALLERY_TYPE_BOTH) && toolbar.visibility != View.VISIBLE
        viewModel.selectedItems = ArrayList(galleryGrid?.getSelectedItems()
                ?: Collections.emptyList())

        viewModel.saveState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        this.menu = menu
        this.menu?.clear()

        switchMenu(viewModel.galleryType, menu)
        return true
    }

    private fun switchMenu(type: Int, menu: Menu?) {
        val menuRes = when (type) {
            GALLERY_TYPE_PAGER -> R.menu.image_menu
            GALLERY_TYPE_GRID -> R.menu.gallery_grid
            else -> R.menu.image_menu
        }

        if (type == GALLERY_TYPE_GRID) toolbar.subtitle = null
        menu?.clear()
        try {
            menuInflater.inflate(menuRes, menu)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Error! ${e.localizedMessage}", e)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.save_menu -> {
                saveFile(MimiUtil.getSaveDir())
            }
            R.id.save_folder_menu -> {
                chooseSaveLocation(REQUEST_CODE_DIR_CHOOSER)
            }
            R.id.share_menu -> {
                galleryPager?.shareImage()
            }
            R.id.fullscreen_menu -> {
                toolbar.visibility = View.GONE
                galleryPager?.fullScreen(enabled = true)
            }
            R.id.download_images -> {
                toolbar.visibility = View.VISIBLE
                toolbar.startActionMode(initActionMode())
            }
        }
        return true
    }

    private fun saveFile(dir: DocumentFile?) {
        if (dir == null || !dir.canWrite()) {
            chooseSaveLocation(IOUtils.REQUEST_CODE_DIR_CHOOSER_PERSISTENT)
            return
        }
        saveDocumentToDisk(dir)
    }

    private fun saveDocumentToDisk(dir: DocumentFile) {
        val id = galleryPager?.postId ?: -1L
        if (id < 0) {
            if (galleryPager == null) {
                Log.e(LOG_TAG, "Could not get post ID from null gallery pager", Exception("Invalid Post ID"))
            } else {
                Log.e(LOG_TAG, "Invalid post ID returned from pager", Exception("Invalid Post ID"))
            }

            return
        }
        val items = viewModel.getGalleryItems()
        val pos = MimiUtil.findGalleryItemPositionById(id, items)
        if (pos < 0) {
            Log.e(LOG_TAG, "Could not find post $id in list of gallery items", Exception("Invalid Post Position"))
            return
        }
        if (!items.isEmpty() && items.size > pos) {
            val item = items[pos]
            if (item.id > 0) {
                val useOriginalFilename = MimiPrefs.userOriginalFilename(this)
                val saveFilename = if (useOriginalFilename) item.originalFileName else item.remoteFileName
                val absolutePos = galleryPager?.position ?: -1
                if (absolutePos < 0) {
                    if (galleryPager == null) {
                        Log.e(LOG_TAG, "Could not get pager position from null gallery pager", Exception("Invalid Pager Position"))
                    } else {
                        Log.e(LOG_TAG, "Invalid position returned from pager", Exception("Invalid Pager Position"))
                    }

                    return
                }
                val savePath = galleryPager?.getLocalPathForPosition(absolutePos)
                if (savePath != null) {
                    IOUtils.safeSaveFile(this, dir, savePath, saveFilename, true)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val textInfo = StringBuilder()
        if (resultCode == RESULT_OK && (requestCode == REQUEST_CODE_DIR_CHOOSER || requestCode == IOUtils.REQUEST_CODE_DIR_CHOOSER_PERSISTENT || requestCode == REQUEST_CODE_BATCH_DOWNLOAD)) {
            val uriTree = data?.data
            if (uriTree == null) {
                Snackbar.make(gallery_root, R.string.error_occurred, Snackbar.LENGTH_LONG).show()
                Log.e(LOG_TAG, "Directory picker returned no URI")
                return
            }

            textInfo.append(uriTree).append("\n")
            textInfo.append("=====================\n")

            if (requestCode == IOUtils.REQUEST_CODE_DIR_CHOOSER_PERSISTENT
                    || requestCode == REQUEST_CODE_BATCH_DOWNLOAD) {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    try {
                        contentResolver.takePersistableUriPermission(uriTree, takeFlags)
                        if (requestCode == IOUtils.REQUEST_CODE_DIR_CHOOSER_PERSISTENT) {
                            MimiUtil.setSaveDir(this, uriTree.toString())
                        }
                    } catch (e: SecurityException) {
                        Log.e(LOG_TAG, "Could not persist access to selected save directory", e)
                    }
            }

            if (requestCode == REQUEST_CODE_DIR_CHOOSER || requestCode == IOUtils.REQUEST_CODE_DIR_CHOOSER_PERSISTENT) {
                val documentFile = DocumentFile.fromTreeUri(this, uriTree)
                if (documentFile != null && documentFile.canWrite()) {
                    updateViewModelSaveLocation()

                    if (viewModel.saveLocation != "" && viewModel.postId >= 0) {
                        viewModel.fetchThread()
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(object : SingleObserver<List<GalleryItem>> {
                                    override fun onSuccess(items: List<GalleryItem>) {

                                        val pos = MimiUtil.findGalleryItemPositionById(viewModel.postId, items)
                                        if (pos >= 0) {
                                            val useOriginalFilename = MimiPrefs.userOriginalFilename(this@GalleryActivity2)
                                            val saveFilename = if (useOriginalFilename) items[pos].originalFileName else items[pos].remoteFileName
                                            IOUtils.safeSaveFile(this@GalleryActivity2, documentFile, File(viewModel.saveLocation), saveFilename, true)
                                        }
                                    }

                                    override fun onSubscribe(d: Disposable) {
                                        // no op
                                    }

                                    override fun onError(e: Throwable) {
                                        Log.e(LOG_TAG, "Error saving gallery item", e)
                                    }
                                })
                    }

                } // else error
            } else if (requestCode == REQUEST_CODE_BATCH_DOWNLOAD) {
//                val list = Collections.emptyList<Int>()
                val list = viewModel.selectedItemIds
                if (!list.isEmpty()) {
                    startBatchDownload(uriTree, list)
                } else {
                    Snackbar.make(gallery_root, R.string.batch_dowmload_no_items_selected, Snackbar.LENGTH_SHORT).show()
                    Log.d(LOG_TAG, "No selected items found for batch download")
                }
            }
        }
    }

    private fun chooseSaveLocation(requestCode: Int) {
        openLocationDialog(requestCode)
    }

    private fun openLocationDialog(requestCode: Int) {
        viewModel.keepFiles = true
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (requestCode == IOUtils.REQUEST_CODE_DIR_CHOOSER_PERSISTENT
                || requestCode == REQUEST_CODE_BATCH_DOWNLOAD) {
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        updateViewModelSaveLocation()
        startActivityForResult(intent, requestCode)
    }

    private fun updateViewModelSaveLocation() {
        if ((viewModel.galleryType == GALLERY_TYPE_PAGER || viewModel.galleryType == GALLERY_TYPE_BOTH)
                && galleryPager != null) {
            val pager = galleryPager
            val id = pager?.postId ?: -1
            if (id >= 0) {
                viewModel.postId = id
                viewModel.saveLocation = pager?.getLocalPathForPosition(pager.position)?.absolutePath
                        ?: ""
            }
        }
    }

    private fun startBatchDownload(path: Uri, selectedPosts: LongArray) {
        if (selectedPosts.isNotEmpty()) {
            val downloadIntent = Intent(this@GalleryActivity2, DownloadService::class.java)
            val extras = Bundle()

            extras.putString(DownloadService.COMMAND_SAVE, path.toString())
            extras.putString(Extras.EXTRAS_BOARD_NAME, viewModel.boardName)
            extras.putLongArray(Extras.EXTRAS_POST_LIST, selectedPosts)
            extras.putLong(Extras.EXTRAS_THREAD_ID, viewModel.threadId)
            extras.putInt(DownloadService.DOWNLOAD_TYPE_KEY, DownloadService.DOWNLOAD_BATCH)

            downloadIntent.putExtras(extras)
            startService(downloadIntent)

            galleryGrid?.selectItems(false)
            actionMode?.finish()

        } else if (galleryGrid != null) {
            val gg: GalleryGrid = galleryGrid as GalleryGrid
            Snackbar.make(gg, R.string.no_images_selected, Snackbar.LENGTH_SHORT).show()
        }


    }

//    override fun onPause() {
//        super.onPause()
//        try {
//            RefreshScheduler.getInstance().unregister(this)
//        } catch (e: Exception) {
//            Log.e(LOG_TAG, "Caught exception", e)
//        }
//
//    }

//    override fun onResume() {
//        super.onResume()
//
//        RefreshScheduler.getInstance().register(this)
//    }

    override fun onStop() {
        super.onStop()
        threadDisposable?.dispose()
    }

    override fun onDestroy() {
        super.onDestroy()
        galleryPager?.release()
    }

    override fun onBackPressed() {
        if (galleryGrid == null || galleryPager == null) {
            super.onBackPressed()
            return
        }

        val gg: GalleryGrid = galleryGrid as GalleryGrid
        val gp: GalleryPager = galleryPager as GalleryPager

        if (gg.visibility == View.VISIBLE && gp.visibility == View.VISIBLE) {
            showGrid(Collections.emptyList(), gp.position)
            return
        }

        super.onBackPressed()
    }
}
