package com.emogoth.android.phone.mimi.view.gallery

import android.content.Context
import android.util.AttributeSet
import android.util.LongSparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.util.contains
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.app.MimiApplication
import com.emogoth.android.phone.mimi.util.GlideApp
import com.emogoth.android.phone.mimi.util.MimiPrefs
import com.emogoth.android.phone.mimi.util.MimiUtil
import com.emogoth.android.phone.mimi.util.Utils
import com.emogoth.android.phone.mimi.viewmodel.GalleryItem
import java.util.*
import kotlin.collections.ArrayList

class GalleryGrid @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val MODE_OPEN = 0
        const val MODE_SELECT = 1
    }

    private val grid: RecyclerView by lazy { RecyclerView(context) }
    private var adapter: GalleryGridItemAdapter? = null
    var itemSelected: ((GalleryItem, Boolean) -> (Unit))? = null
    var multipleItemsSelected: ((List<GalleryItem>) -> (Unit))? = null
    var itemClicked: ((View, GalleryItem) -> (Unit))? = null

//    @State
    var mode = MODE_OPEN
        set(value) {
            field = value

            adapter?.mode = value
            adapter?.notifyDataSetChanged()
        }

//    @State
    var position: Int = 0
        get() {
            val lm: GridLayoutManager = grid.layoutManager as GridLayoutManager
            return lm.findFirstCompletelyVisibleItemPosition()
        }
        set(value) {
            field = value
            grid.post { grid.layoutManager?.scrollToPosition(value) }
        }

    var items: List<GalleryItem> = Collections.emptyList()
    set(value) {
        field = value
        adapter = GalleryGridItemAdapter(value)
        adapter?.selectedChange = { item, selected ->
            itemSelected?.invoke(item, selected)
        }
        adapter?.itemClicked = { view: View, item: GalleryItem ->
            itemClicked?.invoke(view, item)
        }
        grid.adapter = adapter
    }

    init {
        id = R.id.gallery2_grid

        val landscape = resources.getBoolean(R.bool.is_landscape)
        val rows = if (landscape) 3 else 2
        grid.layoutManager = GridLayoutManager(context, rows)
        addView(grid)
    }

    fun selectItems(all: Boolean) {
        adapter?.selectItems(all)
        multipleItemsSelected?.invoke(getSelectedItems())
    }

    fun invertSelection() {
        adapter?.invertSelection()
        multipleItemsSelected?.invoke(getSelectedItems())
    }

    fun getSelectedItems(): List<GalleryItem> {
        return adapter?.getSelectedItems() ?: Collections.emptyList()
    }
}

class GalleryGridItemAdapter(val items: List<GalleryItem>) : RecyclerView.Adapter<GalleryGridItemViewHolder>() {

    private val preloadEnabled: Boolean = MimiPrefs.preloadEnabled(MimiApplication.instance)
    var mode = GalleryGrid.MODE_OPEN
    private val selectedItemMap = LongSparseArray<Boolean>()
    var selectedChange: ((GalleryItem, Boolean) -> (Unit))? = null
    var itemClicked: ((View, GalleryItem) -> (Unit))? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryGridItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.gallery_grid_item, parent, false)

        return GalleryGridItemViewHolder(view, preloadEnabled)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun selectItems(all: Boolean) {
        for (item in items) {
            selectedItemMap.put(item.id, all)
        }

        notifyDataSetChanged()
    }

    fun invertSelection() {
        for (item in items) {
            if (selectedItemMap.contains(item.id)) {
                val selected = !selectedItemMap[item.id]
                selectedItemMap.put(item.id, selected)
            } else {
                selectedItemMap.put(item.id, true)
            }
        }

        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<GalleryItem> {
        val list = ArrayList<GalleryItem>(items.size)
        for (item in items) {
            if (selectedItemMap.contains(item.id) && selectedItemMap[item.id] == true) {
                list.add(item)
            }
        }

        return list
    }

    override fun onBindViewHolder(holder: GalleryGridItemViewHolder, position: Int) {
        val item = items[position]
        val selected = selectedItemMap[item.id] ?: false
        holder.bind(items[position], mode, selected)
        holder.selectionChange = {
            selectedItemMap.put(item.id, it)
            selectedChange?.invoke(item, it)
            mode = GalleryGrid.MODE_SELECT
        }

        holder.itemClicked = {
            itemClicked?.invoke(holder.itemView, item)
        }
    }

    fun setItems(items: List<GalleryItem>) {
        if (this.items is ArrayList) {
            this.items.clear()
            this.items.addAll(items)
            notifyDataSetChanged()
        }

        throw IllegalStateException("Items cannot be modified")
    }

}

class GalleryGridItemViewHolder(private val root: View, private val preloadEnabled: Boolean) : RecyclerView.ViewHolder(root) {
    private val thumbView: AppCompatImageView = this.root.findViewById(R.id.gallery_thumbnail)
    private val selectedView: View = this.root.findViewById(R.id.selected)
    private val fileSizeText: TextView = this.root.findViewById(R.id.file_size)
    private val fileExtText: TextView = this.root.findViewById(R.id.file_ext)
    private val number: TextView = this.root.findViewById(R.id.item_number)
    var selected: Boolean = false
        set(value) {
            field = value
            selectedView.visibility = if (value) View.VISIBLE else View.GONE
        }

    var mode = GalleryGrid.MODE_OPEN

    var selectionChange: ((Boolean) -> (Unit))? = null
    var itemClicked: ((GalleryItem) -> (Unit))? = null

    fun bind(item: GalleryItem, mode: Int, itemSelected: Boolean) {
        val useThumbnail = Utils.isVideoExtension(item.ext) || item.size >= 400000 || !preloadEnabled
        val url = if (useThumbnail) item.thumbnailUrl else item.downloadUrl
        GlideApp.with(root)
                .load(url)
                .optionalCenterCrop()
                .into(thumbView)
                .clearOnDetach()

        this.selected = itemSelected

        fileSizeText.text = MimiUtil.humanReadableByteCount(item.size.toLong(), true)
        fileExtText.text = item.ext.substring(1).uppercase(Locale.getDefault())

        number.text = (layoutPosition + 1).toString()

        root.setOnClickListener {
            if (mode == GalleryGrid.MODE_SELECT) {
                this.selected = !this.selected
                selectionChange?.invoke(this.selected)
            } else {
                itemClicked?.invoke(item)
            }
        }

        root.setOnLongClickListener {
            this.selected = !this.selected
            selectionChange?.invoke(this.selected)
            return@setOnLongClickListener true
        }
    }
}
