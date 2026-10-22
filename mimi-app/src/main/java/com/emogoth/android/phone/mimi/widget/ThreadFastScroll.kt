package com.emogoth.android.phone.mimi.widget

import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.emogoth.android.phone.mimi.R
import me.zhanghai.android.fastscroll.FastScroller
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.PopupStyles
import me.zhanghai.android.fastscroll.Predicate

/** One controller per view; observes only its attached adapter and never assumes equal row heights. */
class ThreadFastScroll private constructor(private val view: RecyclerView) : FastScroller.ViewHelper {
    companion object {
        @JvmStatic fun attach(view: RecyclerView): ThreadFastScroll =
            (view.getTag(R.id.thread_fast_scroll) as? ThreadFastScroll) ?: ThreadFastScroll(view).also {
                view.setTag(R.id.thread_fast_scroll, it)
            }
    }

    lateinit var popup: TextView
        private set
    private val bounds = Rect()
    private var cancelTouch: (() -> Unit)? = null
    private var observedAdapter: RecyclerView.Adapter<*>? = null
    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() { cancelTouch?.invoke() }
        override fun onItemRangeChanged(start: Int, count: Int) = onChanged()
        override fun onItemRangeInserted(start: Int, count: Int) = onChanged()
        override fun onItemRangeRemoved(start: Int, count: Int) = onChanged()
        override fun onItemRangeMoved(from: Int, to: Int, count: Int) = onChanged()
    }

    private fun observeCurrentAdapter() {
        if (observedAdapter === view.adapter) return
        if (observedAdapter != null) cancelTouch?.invoke()
        observedAdapter?.unregisterAdapterDataObserver(observer)
        observedAdapter = view.adapter
        observedAdapter?.registerAdapterDataObserver(observer)
    }
    private val manager get() = (view.layoutManager as? LinearLayoutManager)
        ?.takeIf { it.orientation == RecyclerView.VERTICAL && !it.reverseLayout }
    private val count get() = view.adapter?.itemCount ?: 0
    private val range get() = ThreadScrollPosition.range(count, view.height,
        manager != null && (view.canScrollVertically(-1) || view.canScrollVertically(1)))

    init {
        FastScrollerBuilder(view).setViewHelper(this).setPopupStyle {
            PopupStyles.DEFAULT.accept(it)
            it.setBackgroundResource(R.drawable.fastscroll_popup)
            popup = it
        }.build()
        if (view.isAttachedToWindow) observeCurrentAdapter()
    }

    override fun addOnPreDrawListener(onPreDraw: Runnable) {
        view.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                // Cancel before FastScroller notices a zero range: its disabled-state path
                // ignores touch events, including CANCEL, and could otherwise retain a drag.
                observeCurrentAdapter()
                onPreDraw.run()
            }
        })
    }

    override fun addOnScrollChangedListener(onScrollChanged: Runnable) {
        view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                observeCurrentAdapter()
                onScrollChanged.run()
            }
        })
    }

    override fun addOnTouchEventListener(onTouchEvent: Predicate<MotionEvent>) {
        fun dispatch(event: MotionEvent): Boolean {
            // Reserve only the scrollbar edge, not content drags, from SwipeRefreshLayout.
            if (event.actionMasked == MotionEvent.ACTION_DOWN && range > 0) {
                val hitWidth = 48 * view.resources.displayMetrics.density
                val edge = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL)
                    event.x <= view.paddingLeft + hitWidth
                else event.x >= view.width - view.paddingRight - hitWidth
                if (edge) view.parent?.requestDisallowInterceptTouchEvent(true)
            }
            val handled = onTouchEvent.test(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            return handled
        }
        view.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent) = dispatch(event)
            override fun onTouchEvent(rv: RecyclerView, event: MotionEvent) { dispatch(event) }
        })
        cancelTouch = {
            val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            try { dispatch(cancel) } finally { cancel.recycle() }
            popup.animate().cancel()
            popup.alpha = 0f
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { observeCurrentAdapter(); view.invalidate() }
            override fun onViewDetachedFromWindow(v: View) {
                cancelTouch?.invoke()
                observedAdapter?.unregisterAdapterDataObserver(observer)
                observedAdapter = null
            }
        })
    }

    override fun getScrollRange(): Int = view.height + range

    override fun getScrollOffset(): Int {
        val layout = manager ?: return 0
        val range = range
        if (range == 0) return 0
        if (!view.canScrollVertically(1)) return range
        val position = layout.findFirstVisibleItemPosition()
        val child = layout.findViewByPosition(position) ?: return 0
        view.getDecoratedBoundsWithMargins(child, bounds)
        val fraction = (view.paddingTop - bounds.top).toDouble() / bounds.height().coerceAtLeast(1)
        return ThreadScrollPosition.offset(position, fraction, range, count)
    }

    override fun scrollTo(offset: Int) {
        val layout = manager ?: return
        if (count == 0 || range == 0) return
        view.stopScroll()
        layout.scrollToPositionWithOffset(ThreadScrollPosition.position(offset, range, count), 0)
    }

    override fun getPopupText(): CharSequence? {
        val position = if (range > 0 && !view.canScrollVertically(1)) count - 1
            else manager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
        return if (position == RecyclerView.NO_POSITION) null else ThreadScrollPosition.label(position, count)
    }
}
