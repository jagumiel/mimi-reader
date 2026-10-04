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

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.AlphaAnimation;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.emogoth.android.phone.mimi.BuildConfig;
import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.activity.MimiActivity;
import com.emogoth.android.phone.mimi.activity.TabsActivity;
import com.emogoth.android.phone.mimi.activity.WebActivity;
import com.emogoth.android.phone.mimi.adapter.ThreadListAdapter;
import com.emogoth.android.phone.mimi.autorefresh.RefreshScheduler2;
import com.emogoth.android.phone.mimi.db.BoardTableConnection;
import com.emogoth.android.phone.mimi.db.DatabaseUtils;
import com.emogoth.android.phone.mimi.db.HistoryTableConnection;
import com.emogoth.android.phone.mimi.dialog.RepliesDialog;
import com.emogoth.android.phone.mimi.exceptions.ChanPostException;
import com.emogoth.android.phone.mimi.interfaces.ContentInterface;
import com.emogoth.android.phone.mimi.interfaces.GoToPostListener;
import com.emogoth.android.phone.mimi.interfaces.IToolbarContainer;
import com.emogoth.android.phone.mimi.interfaces.ReplyClickListener;
import com.emogoth.android.phone.mimi.interfaces.ReplyMenuClickListener;
import com.emogoth.android.phone.mimi.interfaces.TabEventListener;
import com.emogoth.android.phone.mimi.interfaces.TabInterface;
import com.emogoth.android.phone.mimi.interfaces.ThumbnailClickListener;
import com.emogoth.android.phone.mimi.model.OutsideLink;
import com.emogoth.android.phone.mimi.util.Extras;
import com.emogoth.android.phone.mimi.util.GalleryScrollReceiver;
import com.emogoth.android.phone.mimi.util.LayoutType;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import com.emogoth.android.phone.mimi.util.RxUtil;
import com.emogoth.android.phone.mimi.viewmodel.ThreadViewModel;
import com.emogoth.android.phone.mimi.widget.MimiRecyclerView;
import com.google.android.material.snackbar.Snackbar;
;
import com.mimireader.chanlib.models.ArchivedChanThread;
import com.mimireader.chanlib.models.ChanPost;
import com.mimireader.chanlib.models.ChanThread;
import com.mimireader.chanlib.models.ErrorChanThread;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import kotlin.Unit;
import retrofit2.HttpException;


public class ThreadDetailFragment extends MimiFragmentBase implements
        TabInterface,
        ContentInterface,
        ReplyClickListener,
        GoToPostListener {
    private static final String LOG_TAG = ThreadDetailFragment.class.getSimpleName();
    private static final boolean LOG_DEBUG = true;
    private static final String REPLY_FRAGMENT_TAG = "reply_fragment";
    private static final int LOADER_ID = 2;

    public static final int REPLIES_DIALOG_CODE = 100;

    private String threadReplyFragmentTag;

    private MimiRecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private ThreadListAdapter threadListAdapter;
    private String boardName;
    private String boardTitle;
    private long threadId;
    private ChanThread currentThread = ChanThread.empty();
    private View loadingLayout;

    private int[] listViewItemHeight;

    private ViewGroup messageContainer;

    private TextView messageText;

    private String replyComment;
    private PostFragment postFragment;
    private int unreadCount = 0;
    private int loaderId = LOADER_ID;
    private TextView closeMessageButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private boolean createNewPostFragment = false;
    //    private boolean isWatched;
    private boolean rememberThreadScrollPosition;
    private int postFromExtras = 0;
    private MenuItem bookmarkMenuItem;
    private Toolbar toolbar;

    private final Handler lastReadHandler = new Handler();
    private LastReadRunnable lastReadRunnable;

    private Snackbar postingSnackbar;
    private boolean stickyAutoRefresh;
    //    private ChanConnector chanConnector;
    private RecyclerView.OnScrollListener recyclerViewScrollListener;
    private ViewStub findViewStub;
    private View findView;

    private Disposable threadSubscription;
    private Disposable boardInfoSubscription;
    private Disposable threadWatcherSubscription;

    private ThreadViewModel viewModel;

    private Disposable fetchPostSubscription;
    private Disposable historyRemovedSubscription;
    private Disposable putHistorySubscription;
    private Disposable fetchHistorySubscription;
    private Disposable fetchThreadSubscription;

    private Handler textFindHandler;
    private Runnable updateAdapterRunnable;

    private Bundle postState;
    private GalleryScrollReceiver scrollReceiver;

    public static ThreadDetailFragment newInstance(long threadId, String boardName, String boardTitle, ChanPost firstPost, boolean stickyAutoRefresh, boolean hasOptionsMenu) {
        final Bundle args = new Bundle();
        args.putLong(Extras.EXTRAS_THREAD_ID, threadId);
        args.putString(Extras.EXTRAS_BOARD_NAME, boardName);
        args.putString(Extras.EXTRAS_BOARD_TITLE, boardTitle);
        args.putBoolean(Extras.EXTRAS_STICKY_AUTO_REFRESH, stickyAutoRefresh);
        args.putBoolean(Extras.EXTRAS_OPTIONS_MENU_ENABLED, hasOptionsMenu);

        if (firstPost != null) {
            args.putParcelable(Extras.EXTRAS_THREAD_FIRST_POST, firstPost);
        }

        ThreadDetailFragment fragment = new ThreadDetailFragment();
        fragment.setArguments(args);

        return fragment;
    }

    public static ThreadDetailFragment newInstance(long threadId, String boardName, String boardTitle, ChanPost firstPost, boolean stickyAutoRefresh) {
        return newInstance(threadId, boardName, boardTitle, firstPost, stickyAutoRefresh, true);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (LOG_DEBUG) {
            Log.d(LOG_TAG, "Entered onCreate()");
        }

        if (savedInstanceState == null) {
            extractExtras(getArguments());
        } else {
            if (savedInstanceState.containsKey(Extras.EXTRAS_THREAD_ID)
                    && getArguments() != null
                    && getArguments().containsKey(Extras.EXTRAS_THREAD_ID)
                    && savedInstanceState.getLong(Extras.EXTRAS_THREAD_ID) == getArguments().getLong(Extras.EXTRAS_THREAD_ID)) {
                extractExtras(savedInstanceState);
            } else {
                extractExtras(getArguments());
            }

        }

//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
//        prefs.edit().remove(RefreshJobService.NOTIFICATIONS_KEY_THREAD_SIZE + "." + threadId).apply();

        lastReadRunnable = new LastReadRunnable(threadId, boardName);
        threadReplyFragmentTag = REPLY_FRAGMENT_TAG + "_" + threadId;

        ViewModelProvider.Factory factory = new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) new ThreadViewModel(boardName, threadId);
            }
        };

        viewModel = new ViewModelProvider(this, factory)
                .get(ThreadViewModel.class);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        if (LOG_DEBUG) {
            Log.d(LOG_TAG, "Entered onCreateView()");
        }

        final View view = inflater.inflate(R.layout.fragment_thread_detail, container, false);

        loadingLayout = view.findViewById(R.id.loading_layout);
        recyclerView = view.findViewById(R.id.thread_list);
        messageContainer = view.findViewById(R.id.message_container);
        messageText = view.findViewById(R.id.message);
        closeMessageButton = view.findViewById(R.id.close_message_button);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        rememberThreadScrollPosition = MimiUtil.rememberThreadScrollPosition(getActivity());
        findViewStub = view.findViewById(R.id.find_bar_stub);

        final MimiActivity activity = (MimiActivity) getActivity();
        toolbar = activity.getToolbar();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (LOG_DEBUG) {
            Log.d(LOG_TAG, "Entered onViewCreated() for thread " + threadId);
        }

        layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), LinearLayoutManager.VERTICAL));

        threadListAdapter = new ThreadListAdapter(currentThread, getChildFragmentManager());

        setupRepliesDialog();
        if (getActivity() instanceof ThumbnailClickListener) {
            threadListAdapter.setOnThumbnailClickListener((ThumbnailClickListener) getActivity());
        }

        recyclerView.setAdapter(threadListAdapter);

        TypedValue typedValue = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.actionBarSize, typedValue, true);
        int pixels = getResources().getDimensionPixelSize(typedValue.resourceId);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            swipeRefreshLayout.setRefreshing(true);
            refresh(false);
        });

        swipeRefreshLayout.setProgressViewOffset(false, pixels, pixels + 100);

        if (getUserVisibleHint() && MimiUtil.getLayoutType(getActivity()) != LayoutType.SLIDING_PANEL) {
            initMenu();
        }

        if (!TextUtils.isEmpty(boardName)) {
            initPosts();
        } else {
            Log.w(LOG_TAG, "No board name for thread " + threadId);
        }
    }

    private void initPosts() {
        if (getActivity() == null || !isAdded()) {
            return;
        }

//        RxUtil.safeUnsubscribe(threadSubscription);
        threadWatcherSubscription = viewModel.watchThread()
                .compose(DatabaseUtils.applySchedulers())
                .doOnNext(chanThread -> {
                    if (chanThread.getPosts().size() == 0) {
                        return;
                    }

                    if (threadId == chanThread.getThreadId()
                            && (chanThread.getPosts().size() > currentThread.getPosts().size() || !chanThread.getClass().equals(currentThread.getClass()))) {
                        final int pos;
                        if (!viewModel.getFirstFetchComplete()) {
                            pos = viewModel.lastReadPos();
//                            RefreshScheduler.getInstance().addThread(boardName, threadId, viewModel.bookmarked());
                        } else {
                            pos = 0;
                        }

                        viewModel.setFirstFetchComplete(true);

                        if (threadListAdapter != null) {
                            threadListAdapter.setUserPosts(viewModel.getUserPosts());
                        }

                        currentThread = chanThread;
                        showThread(chanThread, pos > 0 && rememberThreadScrollPosition);

                    } else {
                        Log.w(LOG_TAG, "Database updated but the thread has no posts");
                    }
                })
                .onErrorResumeNext(throwable -> {
                    Log.e(LOG_TAG, "Error occurred while watching thread", throwable);
                    return Flowable.just(new ErrorChanThread(new ChanThread(boardName, threadId, Collections.emptyList()), throwable));
                })
                .subscribe();

//        RxUtil.safeUnsubscribe(fetchThreadSubscription);
        fetchThreadSubscription = viewModel.fetchThread(false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSuccess(t -> {
                    if (t instanceof ErrorChanThread) {
                        Log.e(LOG_TAG, "Error while fetching thread", ((ErrorChanThread) t).getError());
                        onErrorResponse(((ErrorChanThread) t).getError());
                    } else if (t instanceof ArchivedChanThread) {
                        if (getActivity() != null && isAdded()) {
                            final String archiveMessage = getString(R.string.archive_message, ((ArchivedChanThread) t).getName());
                            onErrorResponse(new Exception(archiveMessage));
                        }
                    } else {
                        Log.e(LOG_TAG, "Returned with " + t.getPosts().size() + " posts in thread /" + t.getBoardName() + "/" + t.getThreadId());
                    }
                })
                .doOnError(this::onErrorResponse)
                .subscribe();
    }

    @Override
    public void initMenu() {
        super.initMenu();

        if (toolbar != null) {
            toolbar.getMenu().clear();
            if (viewModel.bookmarked() && bookmarkMenuItem != null) {
                bookmarkMenuItem.setIcon(R.drawable.ic_bookmark);
            }

            if (TextUtils.isEmpty(boardTitle)) {
                RxUtil.safeUnsubscribe(boardInfoSubscription);
                boardInfoSubscription = BoardTableConnection.fetchBoard(boardName)
                        .compose(DatabaseUtils.applySingleSchedulers())
                        .subscribe(chanBoard -> {
                            if (!TextUtils.isEmpty(chanBoard.getName())) {
                                boardTitle = chanBoard.getTitle();

                                if (getActivity() instanceof MimiActivity) {
                                    MimiActivity activity = (MimiActivity) getActivity();
                                    activity.getSupportActionBar().setTitle(boardTitle);
                                    activity.getSupportActionBar().setSubtitle(String.valueOf(threadId));
                                }
                            }

                        });
            } else {
                if (getActivity() instanceof MimiActivity) {
                    MimiActivity activity = (MimiActivity) getActivity();
                    activity.getSupportActionBar().setTitle(boardTitle);
                    activity.getSupportActionBar().setSubtitle(String.valueOf(threadId));
                }
            }

            final View spinner = toolbar.findViewById(R.id.board_spinner);
            if (spinner != null) {
                spinner.setVisibility(View.GONE);
            }
        }

        if (getActivity() != null) {
            getActivity().invalidateOptionsMenu();
        }

    }

    @Override
    public int getMenuRes() {
        if (MimiUtil.getLayoutType(getActivity()) == LayoutType.TABBED) {
            return R.menu.detail_tab;
        } else {
            return R.menu.detail;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        int menuRes = getMenuRes();
        inflater.inflate(menuRes, menu);

        final MenuItem bookmarkItem = menu.findItem(R.id.bookmark_menu);
        if (bookmarkItem != null) {
            if (viewModel.bookmarked()) {
                bookmarkItem.setIcon(R.drawable.ic_bookmark);
            }
            bookmarkMenuItem = bookmarkItem;
        }

        final MenuItem closeTabItem = menu.findItem(R.id.close_tab);
        if (!(getActivity() instanceof TabsActivity) && closeTabItem != null) {
            closeTabItem.setVisible(false);
        }

        final MenuItem closeOtherTabsItem = menu.findItem(R.id.close_other_tabs);
        if (!(getActivity() instanceof TabsActivity) && closeOtherTabsItem != null) {
            closeOtherTabsItem.setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NotNull MenuItem item) {
        if (getActivity() != null) {

            int itemId = item.getItemId();
            if (itemId == R.id.refresh_menu) {
                refresh(true);
                return true;
            } else if (itemId == R.id.bookmark_menu) {
                toggleWatch();
                return true;
            } else if (itemId == R.id.share_menu) {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT,
                        MimiUtil.https() + getResources().getString(R.string.board_link) + getResources().getString(R.string.raw_thread_path, getBoardName(), getThreadId()));
                sendIntent.setType("text/plain");
                startActivity(sendIntent);
                return true;
            } else if (itemId == R.id.find_menu) {
                showFindView();
                return true;
            } else if (itemId == R.id.mark_as_read_menu) {
                if (currentThread == null || currentThread.getPosts() == null) {
                    return true;
                }

                lastReadRunnable.setPosition(currentThread.getPosts().size());
                lastReadRunnable.run();
                return true;
            } else if (itemId == R.id.close_tab) {
                if (getActivity() instanceof TabEventListener) {
                    ((TabEventListener) getActivity()).onTabClosed(threadId, boardName, boardTitle, false);
                }
                return true;
            } else if (itemId == R.id.close_other_tabs) {
                if (getActivity() instanceof TabEventListener) {
                    ((TabEventListener) getActivity()).onTabClosed(threadId, boardName, boardTitle, true);
                }
                return true;
            } else if (itemId == R.id.quick_top) {
                if (recyclerView != null) {
                    recyclerView.scrollToPosition(0);
                }
                return true;
            } else if (itemId == R.id.quick_bottom) {
                if (recyclerView != null && currentThread != null && currentThread.getPosts() != null) {
                    recyclerView.scrollToPosition(currentThread.getPosts().size() - 1);
                }
                return true;
            }

            return false;

        }

        return false;
    }

    private void showFindView() {
        if (textFindHandler == null) {
            textFindHandler = new Handler();
        }
        if (findView != null) {
            findView.setVisibility(View.VISIBLE);
            AppCompatEditText input = findView.findViewById(R.id.find_text);
            if (input != null) {
                input.requestFocus();
                MimiUtil.showKeyboard();
            }
            return;
        }

        findViewStub.setLayoutResource(R.layout.find_bar_view);
        findViewStub.setOnInflateListener((stub, inflated) -> {
            findView = inflated;

            final AppCompatTextView foundText = inflated.findViewById(R.id.number_found);
            final AppCompatEditText input = inflated.findViewById(R.id.find_text);
            input.requestFocus();
            MimiUtil.showKeyboard();
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    findNext();
                    return true;
                }
                return false;
            });
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(final CharSequence s, int start, int before, int count) {
                    if (threadListAdapter != null) {
                        textFindHandler.removeCallbacks(updateAdapterRunnable);

                        if (!TextUtils.isEmpty(s)) {
                            textFindHandler.postDelayed(updateAdapterRunnable, 500);
                            updateAdapterRunnable = () -> {
                                if (threadListAdapter != null) {
                                    threadListAdapter.getFilter().filter(s);
                                    int count1 = threadListAdapter.getFilterCount();
                                    foundText.setText(inflated.getResources().getString(R.string.found_number, count1));
                                }
                            };
                        }
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });

            View nextButton = inflated.findViewById(R.id.find_next);
            nextButton.setOnClickListener(v -> findNext());

            View prevButton = inflated.findViewById(R.id.find_prev);
            prevButton.setOnClickListener(v -> findPrevious());

            View closeButton = inflated.findViewById(R.id.close_find_bar);
            closeButton.setOnClickListener(v -> {
                if (threadListAdapter != null) {
                    threadListAdapter.clearFilter();
                }

                if (input != null) {
                    input.setText("");
                    MimiUtil.hideKeyboard(input);
                }

                if (inflated != null) {
                    inflated.setVisibility(View.GONE);
                }
            });

            threadListAdapter.setOnFilterUpdateCallback((filteredString, count) -> foundText.setText(inflated.getResources().getString(R.string.found_number, count)));
        });
        findViewStub.inflate();
    }

    private void findNext() {
        int pos = threadListAdapter.getNextFoundStringPosition();
        if (pos >= 0) {
            layoutManager.scrollToPositionWithOffset(pos, 20);
        }

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Position = " + pos);
        }
    }

    private void findPrevious() {
        int pos = threadListAdapter.getPrevFoundStringPosition();
        if (pos >= 0) {
            layoutManager.scrollToPositionWithOffset(pos, 20);
        }

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Position = " + pos);
        }
    }

    private void createRecyclerViewScrollListeners() {
        if (recyclerViewScrollListener == null) {
            recyclerViewScrollListener = new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    final int lastPos = viewModel.lastReadPos();
                    final int currentPos = layoutManager.findFirstVisibleItemPosition() - 1;
                    if (currentPos > lastPos) {
                        lastReadHandler.removeCallbacks(lastReadRunnable);
                        lastReadRunnable
                                .setSize(currentThread.getPosts().size())
                                .setWatched(viewModel.bookmarked())
                                .setPosition(currentPos);

                        lastReadHandler.postDelayed(lastReadRunnable, 500);
                    }
                }
            };
        }

        recyclerView.removeOnScrollListener(recyclerViewScrollListener);
        recyclerView.addOnScrollListener(recyclerViewScrollListener);
    }

    private void extractExtras(final Bundle bundle) {
        if (bundle != null) {
            if (bundle.containsKey(Extras.EXTRAS_THREAD_ID)) {
                threadId = bundle.getLong(Extras.EXTRAS_THREAD_ID);
            }
            if (bundle.containsKey(Extras.EXTRAS_BOARD_NAME)) {
                boardName = bundle.getString(Extras.EXTRAS_BOARD_NAME);
            }
            if (bundle.containsKey(Extras.EXTRAS_BOARD_TITLE)) {
                boardTitle = bundle.getString(Extras.EXTRAS_BOARD_TITLE);
            }
            if (bundle.containsKey(Extras.EXTRAS_UNREAD_COUNT)) {
                unreadCount = bundle.getInt(Extras.EXTRAS_UNREAD_COUNT);
                Log.i(LOG_TAG, "Unread count=" + unreadCount);
            }

            if (bundle.containsKey(Extras.LOADER_ID)) {
                loaderId = bundle.getInt(Extras.LOADER_ID);
            }

            if (bundle.containsKey(Extras.EXTRAS_THREAD_FIRST_POST)) {
                final ChanPost post = bundle.getParcelable(Extras.EXTRAS_THREAD_FIRST_POST);

                List<ChanPost> posts = new ArrayList<>(1);
                posts.add(post);

                currentThread = new ChanThread();
                currentThread.setThreadId(threadId);
                currentThread.setBoardName(boardName);
                currentThread.setPosts(posts);

                postFromExtras++;
            }

            if (bundle.containsKey(Extras.EXTRAS_STICKY_AUTO_REFRESH)) {
                stickyAutoRefresh = bundle.getBoolean(Extras.EXTRAS_STICKY_AUTO_REFRESH);
            } else {
                stickyAutoRefresh = false;
            }

            if (bundle.containsKey(Extras.EXTRAS_POST_STATE)) {
                postState = bundle.getBundle(Extras.EXTRAS_POST_STATE);
            }
        }

    }

    private void toggleWatch() {
        final boolean bookmarked = viewModel.bookmarked();
        if (currentThread != null) {
            final ChanPost post;
            if (viewModel.bookmarked()) {

                if (bookmarkMenuItem != null) {
                    bookmarkMenuItem.setIcon(R.drawable.ic_bookmark_outline);
                }

            } else if (bookmarkMenuItem != null) {
                bookmarkMenuItem.setIcon(R.drawable.ic_bookmark);
            }

            if (currentThread.getPosts().size() > 0) {
                post = currentThread.getPosts().get(0);
                RxUtil.safeUnsubscribe(putHistorySubscription);
                putHistorySubscription = viewModel.setBookmarked(!bookmarked)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(success -> {
                            if (success && currentThread.getPosts().size() > 0) {
                                post.setWatched(bookmarked);
                            }
                        });
            }
        }
    }

    private void refresh(final boolean showLoading) {
        Log.d(LOG_TAG, "Refreshing thread " + threadId);
        if (getActivity() == null) {
            return;
        }

        if (showLoading) {
            showLoadingLayout();
        }

        RxUtil.safeUnsubscribe(threadSubscription);
        threadSubscription = refreshObservable()
                .subscribe(chanThread -> {
                    if (chanThread.getPosts().size() > 0 && showLoading) {
                        showContent();
                    } else if (showLoading) {
                        onErrorResponse(new Exception("Empty response from server"));
                    }

                    Log.d(LOG_TAG, "Refreshed thread: /" + boardName + "/" + threadId);
                }, this::onErrorResponse);

        swipeRefreshLayout.setRefreshing(false);
    }

    private Single<ChanThread> refreshObservable() {
        return viewModel.fetchThread(true)
                .doOnSuccess(chanThread -> {
                    if (chanThread instanceof ErrorChanThread) {
                        onErrorResponse(((ErrorChanThread) chanThread).getError());
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private void showPostFragment() {

        if (getActivity() == null || !isAdded()) {
            return;
        }

        try {
            final FragmentManager fm = getChildFragmentManager();

            recyclerView.smoothScrollBy(0, 0);
            final int threadSize;
            if (currentThread != null && currentThread.getPosts() != null) {
                threadSize = currentThread.getPosts().size();
            } else {
                threadSize = 0;
            }

            final Bundle args;
            if (postState == null || createNewPostFragment) {
                createNewPostFragment = false;

                args = new Bundle();
                args.putLong(Extras.EXTRAS_THREAD_ID, threadId);
                args.putString(Extras.EXTRAS_BOARD_NAME, boardName);
                args.putString(Extras.EXTRAS_BOARD_TITLE, boardTitle);
                args.putInt(Extras.EXTRAS_THREAD_SIZE, threadSize);

            } else {
                args = postState;
            }

            if (!TextUtils.isEmpty(replyComment)) {
                args.putString(Extras.EXTRAS_POST_REPLY, replyComment);
                replyComment = null;
            }

            postFragment = new PostFragment();
            postFragment.setPostListener(createPostCompleteListener());
            postFragment.setArguments(args);

            postFragment.show(fm, threadReplyFragmentTag);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error showing post form", e);
        }
    }

    private PostFragment.PostListener createPostCompleteListener() {
        return new PostFragment.PostListener() {
            @Override
            public void onStartPost() {
                RxUtil.safeUnsubscribe(fetchHistorySubscription);
                fetchHistorySubscription = HistoryTableConnection.fetchPost(boardName, threadId)
                        .compose(DatabaseUtils.applySingleSchedulers())
                        .subscribe(history -> {
                            if (history.getThreadId() == -1) {
                                return;
                            }

                            final boolean watched = history.getWatched();
                            if (!watched) {
                                toggleWatch();
                            }
                        });

                showPostProgress();
                onBackPressed();
            }

            @Override
            public void onSuccess(final String postId) {
                try {
                    if (threadWatcherSubscription == null || threadWatcherSubscription.isDisposed()) {
                        return;
                    }

                    Log.i(LOG_TAG, "Post ID: " + postId);

                    if (getActivity() == null) {
                        return;
                    }

                    showPostStatus("Success!", null);
                    refresh(false);

                    final FragmentTransaction ft = getChildFragmentManager().beginTransaction();
                    ft.remove(postFragment);
                    ft.commit();

                    createNewPostFragment = true;
                    postFragment = null;
                    replyComment = null;
                    postState = null;
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error creating post", e);
                }
            }

            @Override
            public void onError(Throwable error) {
                if (getActivity() != null) {
                    error.printStackTrace();
                    final String html;
                    if (error instanceof ChanPostException) {
                        html = ((ChanPostException) error).getHtml();
                    } else {
                        html = null;
                    }
                    showPostStatus(error.getLocalizedMessage(), html);
                }
            }

            @Override
            public void onDismiss() {
                if (postFragment != null) {
                    postState = postFragment.saveState();
                }
            }

            @Override
            public void onCanceled() {
                if (getActivity() != null && postFragment != null) {
                    final FragmentTransaction ft = getChildFragmentManager().beginTransaction();
                    postFragment.dismiss();
                    ft.remove(postFragment);
                    ft.commit();

                    createNewPostFragment = true;
                    postFragment = null;
                    replyComment = null;
                    postState = null;
                }
            }
        };
    }

    private void showPostProgress() {
        if (getActivity() != null) {
            final View v = getActivity().findViewById(android.R.id.content);
            if (postingSnackbar != null && postingSnackbar.isShown()) {
                postingSnackbar.dismiss();
            }
            postingSnackbar = Snackbar.make(v, R.string.sending, Snackbar.LENGTH_INDEFINITE);
            postingSnackbar.show();
        }
    }

    private void showPostStatus(final String status, final String html) {
        if (getActivity() != null) {
            final View v = getActivity().findViewById(android.R.id.content);
            if (postingSnackbar != null && postingSnackbar.isShown()) {
                postingSnackbar.dismiss();
            }
            Snackbar snackbar = Snackbar.make(v, status, Snackbar.LENGTH_LONG);
            if (!TextUtils.isEmpty(html)) {
                snackbar.setAction(R.string.view,
                        view -> WebActivity
                                .start(getActivity(), html))
                        .setActionTextColor(ResourcesCompat.getColor(getResources(), R.color.md_green_400, getActivity().getTheme()));
            }

            snackbar.show();
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        outState.putLong(Extras.EXTRAS_THREAD_ID, threadId);
        outState.putString(Extras.EXTRAS_BOARD_NAME, boardName);
        outState.putString(Extras.EXTRAS_BOARD_TITLE, boardTitle);
        outState.putInt(Extras.LOADER_ID, loaderId);
        outState.putBoolean(Extras.EXTRAS_STICKY_AUTO_REFRESH, stickyAutoRefresh);

        if (postState != null) {
            outState.putBundle(Extras.EXTRAS_POST_STATE, postState);
        }

        super.onSaveInstanceState(outState);
    }

    private void showLoadingLayout() {
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE);
        }

        if (loadingLayout != null) {
            loadingLayout.setVisibility(View.VISIBLE);
        }

        if (messageContainer != null) {
            messageContainer.setVisibility(View.GONE);
        }
    }

    private void showContent() {
        if (loadingLayout != null) {
            loadingLayout.setVisibility(View.GONE);
        }

        if (recyclerView != null) {
            recyclerView.setVisibility(View.VISIBLE);
        }

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setEnabled(true);
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void setupRepliesDialog() {
        threadListAdapter.setOnReplyMenuClickListener(new ReplyMenuClickListener() {
            @Override
            public void onReply(View view, long threadId) {
                replyComment = ">>" + threadId + "\n";
                showPostFragment();
            }

            @Override
            public void onQuote(View view, ChanPost post) {

                if (post.getCom() != null) {
                    String quote = post.getComment().toString();

                    if (!TextUtils.isEmpty(quote)) {
                        quote = ">" + quote;
                        quote = quote.replace("\n", "\n>");
                    }
                    replyComment = ">>" + post.getNo() + "\n" + quote + "\n";
                }
                showPostFragment();
            }

            @Override
            public void onQuoteSelection(View view, ChanPost post, int start, int end) {
                if (post.getCom() != null) {
                    String quote = post.getComment().toString();
                    if (!TextUtils.isEmpty(quote) && start < quote.length() && end <= quote.length()) {
                        quote = ">" + quote.substring(start, end);
                        quote = quote.replace("\n", "\n>");
                    }
                    replyComment = ">>" + post.getNo() + "\n" + quote + "\n";
                }

                showPostFragment();
            }
        });
    }

    private void showThread(final ChanThread thread, final boolean scrollToPosition) {
        if (thread == null || thread.getPosts() == null || !isAdded()) {
            if (isAdded()) {
                Log.d(LOG_TAG, "Thread object does not have posts; not showing thread");
            } else {
                Log.d(LOG_TAG, "Thread fragment has not been added yet; not showing thread");
            }
            return;
        }

        Log.d(LOG_TAG, "Showing thread...");

        listViewItemHeight = new int[thread.getPosts().size()];
        Arrays.fill(listViewItemHeight, -1);

        if (bookmarkMenuItem != null) {
            if (viewModel.bookmarked()) {
                bookmarkMenuItem.setIcon(R.drawable.ic_bookmark);
            } else {
                bookmarkMenuItem.setIcon(R.drawable.ic_bookmark_outline);
            }
        }

        if (threadListAdapter != null) {
            if (recyclerView.isComputingLayout()) {
                Log.d(LOG_TAG, "RecyclerView is computing layout");
            }

            threadListAdapter.setThread(thread);
        }

        if (scrollToPosition) {
            recyclerView.post(() -> scrollListToPosition(recyclerView, viewModel.lastReadPos()));
        }

        if (postFromExtras > 1) {
            Log.d(LOG_TAG, "Showing footer refresh icon");
            postFromExtras = 0;
        }
        showContent();

        final FragmentManager fm = getChildFragmentManager();
        final PostFragment fragment = (PostFragment) fm.findFragmentByTag(threadReplyFragmentTag);

        if (fragment != null && fragment.getUserVisibleHint()) {
            postFragment = fragment;
            postFragment.setPostListener(createPostCompleteListener());
        }
    }

    private void onErrorResponse(final Throwable error) {
        Log.e(LOG_TAG, "Error: " + error.getLocalizedMessage(), error);

        showContent();

        if (error instanceof HttpException && ((HttpException) error).code() == 404 && messageText != null) {
            messageText.setText(R.string.error_404);
        } else if (messageText != null) {
            messageText.setText(error.getLocalizedMessage());
        } else if (getActivity() != null) {
            Toast.makeText(getActivity(), R.string.unknown_error, Toast.LENGTH_SHORT).show();
//            return;
        }

        if (currentThread == null) {
            if (closeMessageButton != null) {
                closeMessageButton.setVisibility(View.INVISIBLE);
            }
        } else {
            if (closeMessageButton != null) {
                closeMessageButton.setVisibility(View.VISIBLE);
                closeMessageButton.setOnClickListener(v -> {
                    if (messageContainer != null) {
                        final AlphaAnimation animation = new AlphaAnimation(1.0F, 0.0F);
                        animation.setDuration(100);
                        messageContainer.setAnimation(animation);
                        messageContainer.setVisibility(View.GONE);
                    }
                });
            }
        }

        if (messageContainer != null) {
            messageContainer.setVisibility(View.VISIBLE);
        }

        if (viewModel.bookmarked()) {
            RxUtil.safeUnsubscribe(historyRemovedSubscription);
            historyRemovedSubscription = HistoryTableConnection.setHistoryRemovedStatus(boardName, threadId, true)
                    .compose(DatabaseUtils.applySingleSchedulers())
                    .subscribe();
        }

        Log.d(LOG_TAG, "Exception while accessing network", error);
    }

    private ChanPost getFirstPost(final ChanThread thread) {
        if (thread != null && thread.getPosts().size() > 0) {
            return thread.getPosts().get(0);
        }

        return new ChanPost();
    }

    @Override
    public void onResume() {
        super.onResume();

        RefreshScheduler2.addThread(boardName, threadId);
    }

    @Override
    public void onStop() {
        super.onStop();

        viewModel.setFirstFetchComplete(false);

        if (scrollReceiver != null) {
            scrollReceiver.destroy();
        }

        getActivity().unregisterReceiver(scrollReceiver);

        RxUtil.safeUnsubscribe(threadSubscription);
        RxUtil.safeUnsubscribe(boardInfoSubscription);
        RxUtil.safeUnsubscribe(putHistorySubscription);
        RxUtil.safeUnsubscribe(fetchHistorySubscription);
        RxUtil.safeUnsubscribe(historyRemovedSubscription);
        RxUtil.safeUnsubscribe(fetchThreadSubscription);
        RxUtil.safeUnsubscribe(threadWatcherSubscription);

        threadWatcherSubscription = null;

        viewModel.setFirstFetchComplete(false);

        if (!stickyAutoRefresh) {
            RefreshScheduler2.removeThread(boardName, threadId);
//            RefreshScheduler.getInstance().removeThread(boardName, threadId);
        }
//        else if (recyclerView != null) {
////            ThreadRegistry.getInstance().setLastReadPosition(threadId, layoutManager.findFirstVisibleItemPosition() - 1);
//            final StringBuilder sb = new StringBuilder("Setting last read thread position: pos=");
//            sb.append(layoutManager.findFirstVisibleItemPosition() - 1).append(", thread size=").append(currentThread.getPosts().size());
//            Log.d(LOG_TAG, sb.toString());
//            viewModel.setLastReadPosition(layoutManager.findFirstVisibleItemPosition() - 1)
//                    .subscribeOn(Schedulers.io())
//                    .observeOn(AndroidSchedulers.mainThread())
//                    .subscribe();
//
//        }

        recyclerView.clearOnScrollListeners();
    }

    @Override
    public void onStart() {
        super.onStart();

        scrollReceiver = new GalleryScrollReceiver(boardName, threadId, id -> {
            if (getUserVisibleHint()) {
                if (currentThread != null) {
                    final int index = MimiUtil.findPostPositionById(id, currentThread.getPosts());
                    if (index >= 0 && recyclerView != null) {
                        scrollListToPosition(recyclerView, index);
                    }
                }
            }

            return Unit.INSTANCE;
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getActivity().registerReceiver(scrollReceiver,
                    new IntentFilter(scrollReceiver.getIntentFilter()), Context.RECEIVER_NOT_EXPORTED);
        } else {
            getActivity().registerReceiver(scrollReceiver, new IntentFilter(scrollReceiver.getIntentFilter()));
        }

        if (recyclerView != null) {
            createRecyclerViewScrollListeners();
        }

        if (threadWatcherSubscription == null && !TextUtils.isEmpty(boardName)) {
            initPosts();
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (MimiUtil.getLayoutType(getActivity()) != LayoutType.TABBED) {
            RefreshScheduler2.removeThread(boardName, threadId);
//            RefreshScheduler.getInstance().removeThread(boardName, threadId);
        }

        if (threadListAdapter != null) {
            threadListAdapter.setOnFilterUpdateCallback(null);
        }

        RxUtil.safeUnsubscribe(fetchPostSubscription);
        fetchPostSubscription = null;

        recyclerView = null;
        layoutManager = null;
        threadListAdapter = null;
        loadingLayout = null;
        messageContainer = null;
        closeMessageButton = null;
        messageText = null;
        swipeRefreshLayout = null;
        bookmarkMenuItem = null;
        toolbar = null;
    }

    @Override
    public boolean onBackPressed() {
        return false;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (getActivity() != null) {
            if (toolbar == null) {
                toolbar = ((MimiActivity) getActivity()).getToolbar();
            }
            if (isVisibleToUser && getActivity() instanceof IToolbarContainer) {
                IToolbarContainer activity = (IToolbarContainer) getActivity();
                activity.setExpandedToolbar(true, true);
            }

            if (!isVisibleToUser && !stickyAutoRefresh) {
                RefreshScheduler2.removeThread(boardName, threadId);
//                RefreshScheduler.getInstance().removeThread(boardName, threadId);
            }
        }
    }

    private void scrollListToPosition(RecyclerView recyclerView, int position) {
        if (recyclerView == null) {
            return;
        }

        try {
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            final int pos = position >= threadListAdapter.getItemCount() && threadListAdapter.getItemCount() > 0 ? threadListAdapter.getItemCount() - 1 : position;
            if (layoutManager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(pos, 0);
            } else if (layoutManager instanceof StaggeredGridLayoutManager) {
                ((StaggeredGridLayoutManager) layoutManager).scrollToPositionWithOffset(pos, 0);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Could not scroll thread", e);
        }
    }

    @Override
    public void onReplyClicked(@NotNull String boardName, long threadId, final long postId, @NotNull List<String> replies) {
        if (threadId == -1) {
            scrollToPost(postId);
            closeRepliesDialogs();
        } else if (threadId == this.getThreadId()) {
            Log.i(LOG_TAG, "Showing replies");
            if (getActivity() != null) {

                final Handler handler = new Handler(getActivity().getMainLooper());
                handler.post(() -> {
                    if (getActivity() == null) {
                        return;
                    }

                    final RepliesDialog repliesDialog;
                    final FragmentManager fm = getChildFragmentManager();
                    final Fragment frag = fm.findFragmentByTag(RepliesDialog.DIALOG_TAG);
                    final boolean found;
                    if (frag != null) {
                        found = true;
                        repliesDialog = (RepliesDialog) frag;
                    } else {
                        found = false;
                        repliesDialog = new RepliesDialog();
                    }

                    final Bundle args = new Bundle();

                    final ArrayList<OutsideLink> outsideLinks = new ArrayList<>();
                    final ArrayList<String> postNumbers = new ArrayList<>();
                    for (final String id : replies) {

                        if (TextUtils.isDigitsOnly(id)) {
                            int index = -1;
                            try {
                                index = MimiUtil.findPostPositionById(Integer.parseInt(id), currentThread.getPosts());
                                ChanPost post = currentThread.getPosts().get(index);

                                if (post != null) {
                                    postNumbers.add(String.valueOf(post.getNo()));
                                }
                            } catch (ArrayIndexOutOfBoundsException e) {
                                Log.e(LOG_TAG, "post id: " + id);
                                Log.i(LOG_TAG, "index: " + index);

                                e.printStackTrace();

                                Toast.makeText(getActivity(), R.string.error_occurred, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            if (id.contains("/")) {
                                final String[] params = id.split("/");

                                if (params.length > 1) {
                                    final String board = params[1];
                                    final String thread;
                                    final OutsideLink link = new OutsideLink();

                                    link.setBoardName(board);

                                    if (params.length > 2) {
                                        thread = params[2];
                                        Log.i(LOG_TAG, "Found board link: board=" + board + ", thread=" + thread);
                                    } else {
                                        thread = null;
                                        Log.i(LOG_TAG, "Found board link: board=" + board);
                                    }

                                    link.setThreadId(thread);
                                    outsideLinks.add(link);
                                }
                            }
                        }
                    }

                    if (postNumbers.size() > 0 || outsideLinks.size() > 0) {

                        args.putString(Extras.EXTRAS_BOARD_NAME, boardName);
                        args.putStringArrayList(Extras.EXTRAS_POST_LIST, postNumbers);
                        args.putParcelableArrayList(Extras.EXTRAS_OUTSIDE_LINK_LIST, outsideLinks);
                        args.putLong(Extras.EXTRAS_THREAD_ID, currentThread.getThreadId());

                        if (found) {
                            ((RepliesDialog) frag).updateReplies(args);
                        } else {
                            repliesDialog.setArguments(args);
                            repliesDialog.show(fm, RepliesDialog.DIALOG_TAG);
                        }
                    }

                });

            }
        }
    }

    private void closeRepliesDialogs() {
        for (Fragment fragment : getChildFragmentManager().getFragments()) {
            if (fragment instanceof RepliesDialog) {
                ((RepliesDialog) fragment).close();
            }
        }
    }

    private void scrollToPost(long postId) {
        try {
            if (getUserVisibleHint() && currentThread != null) {
                final long index = MimiUtil.findPostPositionById(postId, currentThread.getPosts());

                if (index >= 0) {
                    scrollListToPosition(recyclerView, (int) index);
                }
            }
        } catch (Exception e) {
            if (getActivity() != null) {
                Toast.makeText(getActivity(), R.string.unknown_error, Toast.LENGTH_SHORT).show();
            }

            if (currentThread == null) {
                Log.e(LOG_TAG, "Current thread is null");
            } else {
                if (currentThread.getPosts() == null) {
                    Log.e(LOG_TAG, "Thread posts are null");
                }
            }

            Log.e(LOG_TAG, "Caught exception when scrolling to post from reply dialog", e);
        }
    }

//    @Subscribe
//    public void onReplyClicked(final ReplyClickEvent event) {
//        if (event.getPost() == null) {
//            return;
//        }
//
//        try {
//            if (getUserVisibleHint() && currentThread != null) {
//                final long index = MimiUtil.findPostPositionById(event.getPost().getNo(), currentThread.getPosts());
//
//                if (index >= 0) {
//                    scrollListToPosition(recyclerView, (int) index);
//                }
//            }
//        } catch (Exception e) {
//            if (getActivity() != null) {
//                Toast.makeText(getActivity(), R.string.unknown_error, Toast.LENGTH_SHORT).show();
//            }
//
//            if (currentThread == null) {
//                Log.e(LOG_TAG, "Current thread is null");
//            } else {
//                if (currentThread.getPosts() == null) {
//                    Log.e(LOG_TAG, "Thread posts are null");
//                }
//            }
//
//            Log.e(LOG_TAG, "Caught exception", e);
//        }
//
//    }

    public String getBoardName() {
        return boardName;
    }

    @Override
    public String getTitle() {
        return boardTitle;
    }

    public String getSubtitle() {
        if (threadId > 0) {
            return String.valueOf(threadId);
        }

        return null;
    }

    @Override
    public String getPageName() {
        return "thread_detail";
    }

    public long getThreadId() {
        return threadId;
    }

    @Override
    public int getTabId() {
        return (int) threadId;
    }

    @Override
    public void addContent() {
        showPostFragment();
    }

    @Override
    public void goToPost(long postId) {
        scrollToPost(postId);
    }

    private class LastReadRunnable implements Runnable {

        public final long threadId;
        public final String boardName;

        private boolean closed;
        private boolean watched;
        private int size;
        private int pos;

        private LastReadRunnable(long threadId, String boardName) {
            this.threadId = threadId;
            this.boardName = boardName;
        }

        public long getThreadId() {
            return threadId;
        }

        public String getBoardName() {
            return boardName;
        }

        public int getSize() {
            return size;
        }

        public LastReadRunnable setSize(int size) {
            this.size = size;
            return this;
        }

        public LastReadRunnable setPosition(int pos) {
            this.pos = pos;
            return this;
        }

        public boolean isClosed() {
            return closed;
        }

        public LastReadRunnable setClosed(boolean closed) {
            this.closed = closed;
            return this;
        }

        public LastReadRunnable setWatched(boolean watched) {
            this.watched = watched;
            return this;
        }

        @Override
        public void run() {
            if (getActivity() != null) {
                if (layoutManager != null) {
                    int visibleItems = layoutManager.findLastVisibleItemPosition() - layoutManager.findFirstVisibleItemPosition() + 2;
                    Log.d(LOG_TAG, "Visible items: " + visibleItems);
                    viewModel.setLastReadPosition(this.pos, visibleItems)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(new SingleObserver<Boolean>() {
                                @Override
                                public void onSubscribe(Disposable d) {
                                    // no op
                                }

                                @Override
                                public void onSuccess(@NonNull Boolean success) {
                                    Log.d(LOG_TAG, "set last read position: position=" + pos + ", unread posts=" + viewModel.unread());
//                                    BusProvider.getInstance().post(new UpdateHistoryEvent(threadId, boardName, viewModel.unread(), closed, watched));
                                }

                                @Override
                                public void onError(Throwable e) {
                                    Log.e(LOG_TAG, "Unable to set last read position: position=" + pos + ", unread posts=" + viewModel.unread());
                                }
                            });


                }
            }
        }
    }
}
