package com.emogoth.android.phone.mimi.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.TextView
import androidx.core.view.MotionEventCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.adapter.BoardListAdapter.BoardViewHolder
import com.emogoth.android.phone.mimi.db.BoardTableConnection.setBoardFavorite
import com.emogoth.android.phone.mimi.db.BoardTableConnection.setBoardVisibility
import com.emogoth.android.phone.mimi.db.BoardTableConnection.updateBoardOrder
import com.emogoth.android.phone.mimi.db.DatabaseUtils.applySchedulers
import com.emogoth.android.phone.mimi.db.DatabaseUtils.applySingleSchedulers
import com.emogoth.android.phone.mimi.interfaces.MoveAndDismissable
import com.emogoth.android.phone.mimi.util.BoardCatalog
import com.emogoth.android.phone.mimi.util.MimiUtil
import com.emogoth.android.phone.mimi.util.RxUtil
import com.mimireader.chanlib.models.ChanBoard
import io.reactivex.disposables.Disposable
import java.util.*
import java.util.concurrent.TimeUnit

class BoardListAdapter(context: Context, boards: MutableList<ChanBoard>) : RecyclerView.Adapter<BoardViewHolder>(), MoveAndDismissable {
    private var editMode = false
    private var context: Context? = null
    private var footer: View? = null
    private var dragListener: OnStartDragListener? = null
    private var updateBoardsSubscription: Disposable? = null
    private var favoriteSubscription: Disposable? = null
    private var removeBoardSubscription: Disposable? = null
    private fun init(context: Context, boards: MutableList<ChanBoard>) {
        checkNotNull(boards) { "boards object is null" }
        this.context = context
        this.boards = boards
    }

    var itemLongClickListener: AdapterView.OnItemLongClickListener? = null
    var boardClickListener: OnBoardClickListener? = null
    var boards: MutableList<ChanBoard> = ArrayList()
        set(value) {
            if (field.size > 0) {
                val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun getOldListSize(): Int {
                        return boards.size
                    }

                    override fun getNewListSize(): Int {
                        return value.size
                    }

                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                        return boards[oldItemPosition] == value[newItemPosition]
                    }

                    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                        return boards[oldItemPosition].compareContents(value[newItemPosition])
                    }
                }, true)
                field = value
                result.dispatchUpdatesTo(this)
            } else {
                field.clear()
                field.addAll(value)
                notifyDataSetChanged()
            }
        }

    fun removeBoard(item: Int) {
        val boardName = boards[item].name
        boards.removeAt(item)
        notifyDataSetChanged()
        RxUtil.safeUnsubscribe(removeBoardSubscription)
        val board = ChanBoard()
        board.name = boardName
        removeBoardSubscription = setBoardVisibility(board, false)
                .compose(applySingleSchedulers())
                .subscribe()
    }

    fun setFooter(footer: View?) {
        this.footer = footer
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoardViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.board_list_item, parent, false)
        return BoardViewHolder(v)
    }

    override fun onBindViewHolder(holder: BoardViewHolder, position: Int) {
        val board = boards[position]
        if (holder.boardTitle != null) {
            holder.boardTitle.text = board.title
        }
        if (holder.boardName != null) {
            holder.boardName.text = "/${board.name}/"
        }
        val currentContext = context
        if (holder.boardMetadata != null && currentContext != null) {
            holder.boardMetadata.text = currentContext.getString(
                    R.string.board_metadata,
                    currentContext.getString(BoardCatalog.categoryLabel(BoardCatalog.categoryFor(board.name))),
                    currentContext.getString(if (board.wsBoard == 1) R.string.board_sfw else R.string.board_nsfw))
        }
        if (holder.dragHandle != null) {
            holder.dragHandle.setOnTouchListener(View.OnTouchListener { v: View?, event: MotionEvent? ->
                if (dragListener != null && event?.action == MotionEvent.ACTION_DOWN) {
                    dragListener?.onStartDrag(holder)
                } else {
                    v?.performClick()
                }

                false
            })
            if (editMode) {
                holder.dragHandle.visibility = View.VISIBLE
            } else {
                holder.dragHandle.visibility = View.GONE
            }
        }
        if (holder.favorite != null) {
            if (editMode) {
                holder.favorite.visibility = View.VISIBLE
            } else {
                holder.favorite.visibility = View.GONE
            }
            if (board.isFavorite) {
                holder.favorite.setText(R.string.ic_favorite_set)
            } else {
                holder.favorite.setText(R.string.ic_favorite_unset)
            }
            holder.favorite.setOnClickListener(View.OnClickListener { v: View ->
                val checkBox = v as TextView
                val isFavorite = checkBox.text != context?.getString(R.string.ic_favorite_set)
                RxUtil.safeUnsubscribe(favoriteSubscription)
                favoriteSubscription = setBoardFavorite(board.name, isFavorite)
                        .onErrorReturn { throwable: Throwable? ->
                            Log.e(LOG_TAG, "Error setting board favorite: board=" + board.name + ", favorite=" + isFavorite, throwable)
                            false
                        }
                        .compose(applySingleSchedulers())
                        .subscribe { success: Boolean ->
                            if (success) {
                                board.isFavorite = isFavorite
                            } else {
                                if (isFavorite) {
                                    checkBox.setText(R.string.ic_favorite_unset)
                                } else {
                                    checkBox.setText(R.string.ic_favorite_set)
                                }
                            }
                        }
                if (isFavorite) {
                    checkBox.setText(R.string.ic_favorite_set)
                } else {
                    checkBox.setText(R.string.ic_favorite_unset)
                }
            })
        }
        if (boardClickListener != null && holder.root != null) {
            if (editMode) {
                holder.root.setOnClickListener(null)
            } else {
                holder.root.setOnClickListener(View.OnClickListener { v: View? ->
                    if (!editMode) {
                        boardClickListener?.onBoardClick(board)
                    }
                })
            }
        }
        if (itemLongClickListener != null && holder.root != null) {
            if (editMode) {
                holder.root.setOnLongClickListener(null)
            } else {
                holder.root.setOnLongClickListener(View.OnLongClickListener { v: View? ->
                    if (!editMode) {
                        itemLongClickListener?.onItemLongClick(null, holder.root, holder.adapterPosition, 0)
                    }
                    true
                })
            }
        }
    }

    override fun getItemId(position: Int): Long {
        if (position < boards.size) {
            val board = boards[position]
            val s = board.name + board.title
            return s.hashCode().toLong()
        }
        return -1
    }

    override fun getItemCount(): Int {
        return boards.size + if (footer != null) 1 else 0
    }

    override fun onItemMove(from: Int, to: Int) {
        val item = boards.set(from, boards[to])
        boards[to] = item

        RxUtil.safeUnsubscribe(updateBoardsSubscription)
        updateBoardsSubscription = updateBoardOrder(boards)
                .delay(500, TimeUnit.MILLISECONDS)
                .compose(applySingleSchedulers())
                .onErrorReturn {
                    Log.e(LOG_TAG, "Error ordering boards", it)
                    false
                }
                .subscribe { success: Boolean ->
                    if (success && MimiUtil.getBoardOrder() != 7) {
                        MimiUtil.setBoardOrder(context, 7)
                    }
                }

        notifyItemMoved(from, to)
    }

    override fun onDismiss(position: Int) {
        removeBoard(position)
    }

    fun editMode(enabled: Boolean) {
        editMode = enabled

        notifyDataSetChanged()
    }

    fun IsEditMode(): Boolean {
        return editMode
    }

    fun setDragListener(listener: OnStartDragListener?) {
        dragListener = listener
    }

    class BoardViewHolder(root: View) : RecyclerView.ViewHolder(root) {
        val root: View?
        val boardName: TextView?
        val boardMetadata: TextView?
        val boardTitle: TextView?
        val dragHandle: View?
        val favorite: TextView?

        init {
            this.root = root
            boardName = root.findViewById(R.id.board_name)
            boardMetadata = root.findViewById(R.id.board_metadata)
            boardTitle = root.findViewById(R.id.board_title)
            dragHandle = root.findViewById(R.id.drag_handle)
            favorite = root.findViewById(R.id.favorite)
        }
    }

    interface OnStartDragListener {
        /**
         * Called when a view is requesting a start of a drag.
         *
         * @param viewHolder The holder of the view to drag.
         */
        fun onStartDrag(viewHolder: RecyclerView.ViewHolder?)
    }

    interface OnBoardClickListener {
        fun onBoardClick(board: ChanBoard)
    }

    companion object {
        val LOG_TAG = BoardListAdapter::class.java.simpleName
        const val LOG_DEBUG = true
    }

    init {
        init(context, boards)
    }
}
