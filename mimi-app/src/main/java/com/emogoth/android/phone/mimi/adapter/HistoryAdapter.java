/*
 * Copyright (c) 2016. Eli Connelly
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.emogoth.android.phone.mimi.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.view.MotionEventCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.autorefresh.RefreshScheduler2;
import com.emogoth.android.phone.mimi.db.DatabaseUtils;
import com.emogoth.android.phone.mimi.db.HistoryTableConnection;
import com.emogoth.android.phone.mimi.db.models.History;
import com.bumptech.glide.Glide;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import com.emogoth.android.phone.mimi.util.MediaThumbnail;
import com.emogoth.android.phone.mimi.util.RxUtil;
;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.disposables.Disposable;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private static final String LOG_TAG = History.class.getSimpleName();

    private int[] unreadCountList;
    private boolean isEditMode = false;

    private LinkedList<CharSequence> timeList;
    private Disposable updateHistoryOrderSubscription;
    private Disposable fetchPostSubscription;
    private Disposable removeHistorySubscription;

    private final List<History> historyList = new ArrayList<>();

    private HistoryItemClickListener clickListener;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.history_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, final int pos) {

        final int position = viewHolder.getAdapterPosition();
        final History historyItem = historyList.get(position);
        Context context = viewHolder.text.getContext();
        viewHolder.text.setText(historyItem.getComment());

        if (clickListener != null) {
            viewHolder.root.setOnLongClickListener(view -> {
                clickListener.onItemLongClick(view, position);
                return true;
            });

            viewHolder.root.setOnClickListener(view -> clickListener.onItemClick(view, position));
        }

        if (historyItem.getWatched()) {
            final int count = historyItem.getUnreadCount();
            unreadCountList[position] = count;
            if (count > 0) {
                viewHolder.unreadcount.setText(String.valueOf(count));
                viewHolder.unreadcount.setVisibility(View.VISIBLE);
            } else {
                viewHolder.unreadcount.setVisibility(View.GONE);
            }
        } else {
            viewHolder.unreadcount.setVisibility(View.GONE);
        }

        viewHolder.image.setVisibility(View.INVISIBLE);
        if (!TextUtils.isEmpty(historyItem.getTim())) {
            final String url = MimiUtil.https() + context.getString(R.string.thumb_link) + context.getString(R.string.thumb_path, historyItem.getBoardName(), historyItem.getTim());

            viewHolder.image.setVisibility(View.VISIBLE);

            MediaThumbnail.load(viewHolder.image,
                    "f".equals(historyItem.getBoardName()) ? "" : url,
                    "f".equals(historyItem.getBoardName()) ? ".swf" : "");
        } else {
            viewHolder.image.setVisibility(View.INVISIBLE);
            Glide.with(context).clear(viewHolder.image);
        }

        viewHolder.threadinfo.setText("/" + historyItem.getBoardName() + "/" + historyItem.getThreadId());
        viewHolder.opname.setText(historyItem.getUserName());
        viewHolder.lastviewed.setText(timeList.get(position));

        if (isEditMode) {
            viewHolder.deletehistory.setVisibility(View.VISIBLE);
        } else {
            viewHolder.deletehistory.setVisibility(View.GONE);
        }

        viewHolder.deletehistory.setOnClickListener(v -> {

            RxUtil.safeUnsubscribe(removeHistorySubscription);
            removeHistorySubscription = HistoryTableConnection.removeHistory(historyItem.getBoardName(), historyItem.getThreadId())
                    .compose(DatabaseUtils.applySingleSchedulers())
                    .subscribe(success -> {
                        int currentPosition = viewHolder.getAdapterPosition();
                        if (success && currentPosition >= 0 && currentPosition < historyList.size()) {
                            Log.d(LOG_TAG, "Removed history: " + "board name=" + historyItem.getBoardName() + ", thread id=" + historyItem.getThreadId());

                            RefreshScheduler2.removeThread(historyItem.getBoardName(), historyItem.getThreadId());
//                            RefreshScheduler.getInstance().removeThread(historyItem.getBoardName(), historyItem.getThreadId());
//                            ThreadRegistry.getInstance().remove(historyItem.threadId);
                            historyList.remove(currentPosition);
                            timeList.remove(currentPosition);
                            if (historyList.size() == 0) {
                                isEditMode = false;
                            }
                            startUpdateTimer(historyList);

                            notifyItemRemoved(currentPosition);
                        } else {
                            String errorMsg = "Error removing history: " + "board name=" + historyItem.getBoardName() + ", thread id=" + historyItem.getThreadId() + " ,history list size=" + historyList.size() + ", history item position=" + currentPosition;
                            Exception e = new IllegalStateException(errorMsg);
                            Log.e(LOG_TAG, "Something went wrong while removing history", e);
                        }
                    }, throwable -> Log.e(LOG_TAG, "Something went wrong while removing history", throwable));
        });

        viewHolder.dragHandle.setOnTouchListener((view, motionEvent) -> {
            if (clickListener != null) {
                if (MotionEventCompat.getActionMasked(motionEvent) ==
                        MotionEvent.ACTION_DOWN) {
                    clickListener.onItemStartDrag(viewHolder);
                }
            }

            return false;
        });
    }

    private void init() {
        unreadCountList = new int[historyList.size()];
        for (int i = 0; i < historyList.size(); i++) {
            History history = historyList.get(i);
            unreadCountList[i] = history.getUnreadCount();
        }

        buildTimeMap();
    }

    private void buildTimeMap() {
        this.timeList = new LinkedList<>();
        for (int i = 0; i < historyList.size(); i++) {
            timeList.add(DateUtils.getRelativeTimeSpanString(
                    historyList.get(i).getLastAccess(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
            ));
        }
    }

    public void setHistory(final List<History> list) {
        historyList.clear();
        historyList.addAll(list);

        init();

        notifyDataSetChanged();
    }

    public void setEditMode(final boolean enabled) {
        isEditMode = enabled;
        notifyDataSetChanged();
    }

    public boolean isEditMode() {
        return isEditMode;
    }

    public int[] getUnreadCountList() {
        return unreadCountList;
    }

    public void swapItems(int from, int to) {
        if (from < to) {
            for (int i = from; i < to; i++) {
                Collections.swap(historyList, i, i + 1);
            }
        } else {
            for (int i = from; i > to; i--) {
                Collections.swap(historyList, i, i - 1);
            }
        }
        notifyItemMoved(from, to);
        startUpdateTimer(historyList);
    }

    public History getItem(int pos) {
        return historyList.get(pos);
    }

    public void clearHistory() {
        if (historyList.size() > 0) {

            RxUtil.safeUnsubscribe(fetchPostSubscription);
            fetchPostSubscription = HistoryTableConnection.fetchPost(historyList.get(0).getBoardName(), historyList.get(0).getThreadId())
                    .compose(DatabaseUtils.<History>applySingleSchedulers())
                    .subscribe(history -> {
                        if (history.getThreadId() == -1) {
                            return;
                        }

                        final boolean watched = history.getWatched();
                        for (int position = 0; position < historyList.size(); position++) {
                            final History historyItem = historyList.get(position);

//                            RefreshScheduler.getInstance().removeThread(historyItem.getBoardName(), historyItem.getThreadId());

                            historyList.remove(position);
                            timeList.remove(position);

                        }

//                        ThreadRegistry.getInstance().clear();
                        MimiUtil.removeHistory(watched).subscribe();

                        isEditMode = false;
                        notifyDataSetChanged();
                    });
        }
    }

    private void startUpdateTimer(List<History> histories) {
        RxUtil.safeUnsubscribe(updateHistoryOrderSubscription);
        updateHistoryOrderSubscription = HistoryTableConnection.updateHistoryOrder(histories)
                .compose(DatabaseUtils.applySingleSchedulers())
                .delay(500, TimeUnit.MILLISECONDS)
                .subscribe();
    }

    @Override
    public long getItemId(int position) {
        return historyList.get(position).getThreadId();
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    public void setItemClickListener(HistoryItemClickListener listener) {
        clickListener = listener;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public final View root;
        public final ImageView image;
        public final TextView threadinfo;
        public final TextView opname;
        public final TextView lastviewed;
        public final TextView text;
        public final TextView unreadcount;
        public final ImageView deletehistory;
        public final View dragHandle;

        public ViewHolder(View root) {
            super(root);

            this.root = root;

            image = root.findViewById(R.id.image);
            threadinfo = root.findViewById(R.id.thread_info);
            opname = root.findViewById(R.id.op_name);
            lastviewed = root.findViewById(R.id.last_viewed);
            text = root.findViewById(R.id.text);
            unreadcount = root.findViewById(R.id.unread_count);
            deletehistory = root.findViewById(R.id.delete_history);
            dragHandle = root.findViewById(R.id.drag_handle);
        }
    }

    public interface HistoryItemClickListener {
        void onItemLongClick(View v, int position);

        void onItemClick(View v, int position);

        void onItemStartDrag(ViewHolder holder);
    }
}
