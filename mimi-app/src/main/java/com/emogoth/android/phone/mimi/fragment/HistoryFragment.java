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

package com.emogoth.android.phone.mimi.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.activity.MimiActivity;
import com.emogoth.android.phone.mimi.activity.PostItemDetailActivity;
import com.emogoth.android.phone.mimi.activity.PostItemListActivity;
import com.emogoth.android.phone.mimi.adapter.HistoryAdapter;
import com.emogoth.android.phone.mimi.db.DatabaseUtils;
import com.emogoth.android.phone.mimi.db.HistoryTableConnection;
import com.emogoth.android.phone.mimi.db.models.History;
import com.emogoth.android.phone.mimi.fourchan.FourChanCommentParser;
import com.emogoth.android.phone.mimi.interfaces.IToolbarContainer;
import com.emogoth.android.phone.mimi.model.ThreadInfo;
import com.emogoth.android.phone.mimi.util.Extras;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import com.emogoth.android.phone.mimi.util.RxUtil;
import com.emogoth.android.phone.mimi.widget.MimiRecyclerView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;

public class HistoryFragment extends MimiFragmentBase {
    private static final String LOG_TAG = HistoryFragment.class.getSimpleName();

    private MimiRecyclerView historyRecyclerView;
    private HistoryAdapter historyAdapter;
    private ViewGroup emptyListMessageContainer;
    private TextView emptyListMessage;
    private TextView historyRetryButton;
    private ListView historylist;
    private TextView closeButton;
    private TextView clearAllButton;
    private LinearLayout clearAllContainer;
    private TextView emptylisttext;
    private FrameLayout emptylist;
    private int historyQuery = 0;
    private List<History> postList;
    private int viewingHistory = 0;

    private boolean isBookmarks = false;

    private Disposable fetchHistorySubscription;
    private Toolbar toolbar;
    private ItemTouchHelper recyclerViewTouchHelper;
    private ItemTouchHelper.Callback touchHelperCallback;
    private RecyclerView.LayoutManager layoutManager;
    private Snackbar loadErrorSnackbar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(false);
    }

    public static HistoryFragment newInstance(boolean watched) {
        final Bundle args = new Bundle();

        if (watched) {
            args.putInt(Extras.EXTRAS_HISTORY_QUERY_TYPE, HistoryTableConnection.BOOKMARKS);
            args.putInt(Extras.EXTRAS_VIEWING_HISTORY, MimiActivity.VIEWING_BOOKMARKS);
        } else {
            args.putInt(Extras.EXTRAS_HISTORY_QUERY_TYPE, HistoryTableConnection.HISTORY);
            args.putInt(Extras.EXTRAS_VIEWING_HISTORY, MimiActivity.VIEWING_HISTORY);
        }

        HistoryFragment fragment = new HistoryFragment();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.fragment_history_list, container, false);

        if (getUserVisibleHint()) {
            initMenu();
        }

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Bundle args = getArguments();
        if (args != null) {
            if (args.containsKey(Extras.EXTRAS_VIEWING_HISTORY)) {
                viewingHistory = args.getInt(Extras.EXTRAS_VIEWING_HISTORY);
            }
            if (args.containsKey(Extras.EXTRAS_HISTORY_QUERY_TYPE)) {
                historyQuery = args.getInt(Extras.EXTRAS_HISTORY_QUERY_TYPE);
            }

            if (viewingHistory == MimiActivity.VIEWING_BOOKMARKS) {
                isBookmarks = true;
            }
        }

        toolbar = ((MimiActivity) getActivity()).getToolbar();

        if (getActivity() instanceof MimiActivity) {
            final MimiActivity activity = (MimiActivity) getActivity();
            activity.getSupportActionBar().setTitle(getTitle());
            activity.getSupportActionBar().setSubtitle(getSubtitle());
        }

        clearAllContainer = view.findViewById(R.id.clear_all_container);

        historyRecyclerView = view.findViewById(R.id.history_list);
        historyRecyclerView.setItemAnimator(new DefaultItemAnimator());
        layoutManager = new LinearLayoutManager(getActivity());
        historyAdapter = new HistoryAdapter();
        historyAdapter.setHasStableIds(true);

        touchHelperCallback = createTouchHelperCallback();
        recyclerViewTouchHelper = new ItemTouchHelper(createTouchHelperCallback());
        recyclerViewTouchHelper.attachToRecyclerView(historyRecyclerView);
        historyAdapter.setItemClickListener(new HistoryAdapter.HistoryItemClickListener() {
            @Override
            public void onItemLongClick(View v, int position) {
                if (historyAdapter != null) {
                    if (historyAdapter.isEditMode()) {
                        historyAdapter.setEditMode(false);
                        clearAllContainer.setVisibility(View.GONE);
                    } else {
                        historyAdapter.setEditMode(true);
                        clearAllContainer.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onItemClick(View v, int position) {
                if (!historyAdapter.isEditMode()) {
                    openThreadInActivity(position);
                }
            }

            @Override
            public void onItemStartDrag(HistoryAdapter.ViewHolder holder) {
                recyclerViewTouchHelper.startDrag(holder);
            }
        });
        historyRecyclerView.setLayoutManager(layoutManager);
        historyRecyclerView.setAdapter(historyAdapter);

        emptyListMessageContainer = view.findViewById(R.id.empty_list);
        emptyListMessage = view.findViewById(R.id.empty_list_text);
        historyRetryButton = view.findViewById(R.id.history_retry_button);
        historyRetryButton.setOnClickListener(v -> loadHistory(isBookmarks));

        clearAllButton = view.findViewById(R.id.clear_all_button);
        clearAllButton.setOnClickListener(v -> {
            if (historyAdapter != null) {
                historyAdapter.setEditMode(false);
                historyAdapter.clearHistory();

                historyRecyclerView.setVisibility(View.GONE);
                emptyListMessageContainer.setVisibility(View.VISIBLE);
                clearAllContainer.setVisibility(View.GONE);
            }
        });

        closeButton = view.findViewById(R.id.remove_history_button);
        closeButton.setOnClickListener(v -> {

            if (historyAdapter != null) {
                historyAdapter.setEditMode(false);
            }

            if (clearAllContainer != null) {
                clearAllContainer.setVisibility(View.GONE);
            }
        });

        final int loaderId = MimiUtil.getInstance().getLoaderId(((Object) this).getClass());
        Log.i(LOG_TAG, "Loading history using loader id=" + loaderId);

        if (historyQuery == HistoryTableConnection.BOOKMARKS) {
            emptyListMessage.setText(R.string.no_bookmarks_message);
        } else {
            emptyListMessage.setText(R.string.no_history_message);
        }

        loadHistory(isBookmarks);
    }

    private ItemTouchHelper.Callback createTouchHelperCallback() {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {
            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder from, RecyclerView.ViewHolder to) {
                HistoryAdapter adapter;
                if (recyclerView.getAdapter() instanceof HistoryAdapter) {
                    adapter = (HistoryAdapter) recyclerView.getAdapter();
                    adapter.swapItems(from.getAdapterPosition(), to.getAdapterPosition());
                    return true;
                }

                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int i) {

            }
        };
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.history_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.refresh_menu) {
            loadHistory(isBookmarks);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void initMenu() {
        super.initMenu();

        MimiActivity activity = null;
        if (getActivity() instanceof MimiActivity) {
            activity = (MimiActivity) getActivity();
            toolbar = activity.getToolbar();
        }

        if (toolbar != null) {

            Spinner toolbarSpinner = toolbar.findViewById(R.id.board_spinner);
            if (toolbarSpinner != null) {
                toolbarSpinner.setVisibility(View.GONE);
            }

            activity.getSupportActionBar().setTitle(getTitle());
            activity.getSupportActionBar().setSubtitle(null);
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser) {
            if (getActivity() != null) {
                toolbar = ((MimiActivity) getActivity()).getToolbar();
            }

            loadHistory(isBookmarks);

            if (getActivity() instanceof IToolbarContainer) {
                IToolbarContainer activity = (IToolbarContainer) getActivity();
                activity.setExpandedToolbar(true, true);
            }
        } else {
            RxUtil.safeUnsubscribe(fetchHistorySubscription);
            if (historyAdapter != null && historyAdapter.isEditMode()) {
                historyAdapter.setEditMode(false);
                clearAllContainer.setVisibility(View.GONE);
            }
        }

    }

    private void loadHistory(boolean watched) {
        RxUtil.safeUnsubscribe(fetchHistorySubscription);
        fetchHistorySubscription = HistoryTableConnection.observeHistory(watched)
                .flatMap((Function<List<History>, Flowable<List<History>>>) histories -> {
                    FourChanCommentParser.Builder builder = new FourChanCommentParser.Builder();
                    builder.setContext(getActivity())
                            .setQuoteColor(MimiUtil.getInstance().getQuoteColor())
                            .setReplyColor(MimiUtil.getInstance().getReplyColor())
                            .setHighlightColor(MimiUtil.getInstance().getHighlightColor())
                            .setLinkColor(MimiUtil.getInstance().getLinkColor());

                    for (History history : histories) {
                        history.setComment(builder.setComment(history.getText()).build().parse());
                    }

                    return Flowable.just(histories);
                })
                .compose(DatabaseUtils.applySchedulers())
                .subscribe(historyList -> {
                    dismissLoadError();
                    if (historyList != null && getActivity() != null) {
                        postList = historyList;
                        if (historyList.size() > 0) {
                            if (historyAdapter != null) {
                                historyAdapter.setHistory(historyList);
                            }

                            historyRecyclerView.setVisibility(View.VISIBLE);
                            emptyListMessageContainer.setVisibility(View.GONE);
                        } else {
                            historyRecyclerView.setVisibility(View.GONE);
                            emptyListMessageContainer.setVisibility(View.VISIBLE);
                        }
                    } else {
                        Log.i(LOG_TAG, "Cursor is null!");
                    }
                }, this::showLoadError);
    }

    void showLoadError(Throwable error) {
        Log.e(LOG_TAG, "Error loading history tab", error);
        if (getView() == null) {
            return;
        }

        if (historyAdapter != null && historyAdapter.getItemCount() > 0) {
            loadErrorSnackbar = Snackbar.make(
                    getView(),
                    R.string.error_loading_tab,
                    Snackbar.LENGTH_INDEFINITE
            ).setAction(R.string.refresh, view -> loadHistory(isBookmarks));
            loadErrorSnackbar.show();
            return;
        }

        historyRecyclerView.setVisibility(View.GONE);
        emptyListMessage.setText(R.string.error_loading_tab);
        historyRetryButton.setVisibility(View.VISIBLE);
        emptyListMessageContainer.setVisibility(View.VISIBLE);
    }

    private void dismissLoadError() {
        if (loadErrorSnackbar != null) {
            loadErrorSnackbar.dismiss();
            loadErrorSnackbar = null;
        }
        if (historyRetryButton != null) {
            historyRetryButton.setVisibility(View.GONE);
        }
        if (emptyListMessage != null) {
            emptyListMessage.setText(
                    historyQuery == HistoryTableConnection.BOOKMARKS
                            ? R.string.no_bookmarks_message
                            : R.string.no_history_message
            );
        }
    }

    @Override
    public void onDestroyView() {
        RxUtil.safeUnsubscribe(fetchHistorySubscription);
        dismissLoadError();
        historyRetryButton = null;
        super.onDestroyView();
    }

    private void openThreadInActivity(final int position) {

        if (postList == null || position >= postList.size()) {
            return;
        }

        if (getActivity() instanceof PostItemListActivity) {
            final Bundle args = new Bundle();
            final Class clazz;
            if (historyQuery == HistoryTableConnection.BOOKMARKS) {
                args.putBoolean(Extras.EXTRAS_USE_BOOKMARKS, true);
                args.putInt(Extras.EXTRAS_VIEWING_HISTORY, MimiActivity.VIEWING_BOOKMARKS);
            }

            if (historyQuery == HistoryTableConnection.HISTORY) {
                args.putBoolean(Extras.EXTRAS_USE_HISTORY, true);
                args.putInt(Extras.EXTRAS_VIEWING_HISTORY, MimiActivity.VIEWING_HISTORY);
            }

            args.putString(Extras.EXTRAS_BOARD_NAME, postList.get(position).getBoardName());
            args.putLong(Extras.EXTRAS_THREAD_ID, postList.get(position).getThreadId());
            args.putInt(Extras.EXTRAS_POSITION, position);
            args.putIntArray(Extras.EXTRAS_UNREAD_COUNT, historyAdapter.getUnreadCountList());

            final ArrayList<ThreadInfo> threadList = new ArrayList<ThreadInfo>(postList.size());

            for (final History post : postList) {
                final ThreadInfo threadInfo = new ThreadInfo(post.getThreadId(), post.getBoardName(), "", post.getWatched());
                threadList.add(threadInfo);
            }

            args.putParcelableArrayList(Extras.EXTRAS_THREAD_LIST, threadList);

            if (getResources().getBoolean(R.bool.two_pane)) {
                clazz = PostItemListActivity.class;
            } else {
                clazz = PostItemDetailActivity.class;
            }

            final Intent intent = new Intent(getActivity(), clazz);
            intent.putExtras(args);
            startActivity(intent);
        } else if (getActivity() instanceof MimiActivity) {
            MimiActivity activity = (MimiActivity) getActivity();
            History history = postList.get(position);
            activity.onPostItemClick(null, Collections.emptyList(), position, "", history.getBoardName(), history.getThreadId());
        }

//        if(viewingHistory > 0 && getActivity() != null) {
//            getActivity().finish();
//        }

    }

    @Override
    public void onResume() {
        super.onResume();

        if (historyAdapter != null) {
            historyAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean showFab() {
        return false;
    }

    @Override
    public String getTitle() {
        if (viewingHistory == MimiActivity.VIEWING_HISTORY) {
            return getString(R.string.history_title);
        } else if (viewingHistory == MimiActivity.VIEWING_BOOKMARKS) {
            return getString(R.string.bookmarks);
        }

        return null;
    }

    @Override
    public String getSubtitle() {
        return null;
    }

    @Override
    public String getPageName() {
        if (viewingHistory == MimiActivity.VIEWING_HISTORY) {
            return "history_tab";
        } else if (viewingHistory == MimiActivity.VIEWING_BOOKMARKS) {
            return "bookmarks_tab";
        }

        return "history_tab";
    }
}
