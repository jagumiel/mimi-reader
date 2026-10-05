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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.emogoth.android.phone.mimi.BuildConfig;
import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.activity.EditFiltersActivity;
import com.emogoth.android.phone.mimi.activity.MimiActivity;
import com.emogoth.android.phone.mimi.adapter.BoardDropDownAdapter;
import com.emogoth.android.phone.mimi.adapter.PostItemsAdapter;
import com.emogoth.android.phone.mimi.db.BoardTableConnection;
import com.emogoth.android.phone.mimi.db.CatalogTableConnection;
import com.emogoth.android.phone.mimi.db.DatabaseUtils;
import com.emogoth.android.phone.mimi.db.FilterTableConnection;
import com.emogoth.android.phone.mimi.db.HiddenThreadTableConnection;
import com.emogoth.android.phone.mimi.db.HistoryTableConnection;
import com.emogoth.android.phone.mimi.db.models.Filter;
import com.emogoth.android.phone.mimi.db.models.HiddenThread;
import com.emogoth.android.phone.mimi.db.models.History;
import com.emogoth.android.phone.mimi.fourchan.FourChanCommentParser;
import com.emogoth.android.phone.mimi.interfaces.ContentInterface;
import com.emogoth.android.phone.mimi.interfaces.PostItemClickListener;
import com.emogoth.android.phone.mimi.interfaces.TabInterface;
import com.emogoth.android.phone.mimi.util.Extras;
import com.emogoth.android.phone.mimi.util.FourChanUtil;
import com.emogoth.android.phone.mimi.util.LayoutType;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import com.emogoth.android.phone.mimi.util.NetworkErrorMessage;
import com.emogoth.android.phone.mimi.util.RxUtil;
import com.emogoth.android.phone.mimi.view.FilterDialog;
import com.emogoth.android.phone.mimi.view.FilterView;
import com.emogoth.android.phone.mimi.viewmodel.CatalogViewModel;
import com.google.android.material.snackbar.Snackbar;
import com.mimireader.chanlib.models.ChanBoard;
import com.mimireader.chanlib.models.ChanCatalog;
import com.mimireader.chanlib.models.ChanPost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.functions.Function3;
import io.reactivex.schedulers.Schedulers;


public class PostItemsListFragment extends MimiFragmentBase implements
        Spinner.OnItemSelectedListener,
        TabInterface,
        ContentInterface {

    private final static String LOG_TAG = PostItemsListFragment.class.getSimpleName();
    private static final String POST_FRAGMENT_TAG = "post_fragment";

    /**
     * The serialization (saved instance state) Bundle key representing the
     * activated item position. Only used on tablets.
     */
    private static final String STATE_ACTIVATED_POSITION = "activated_position";

    private static final String CATALOG_PREF_STRING = "catalog_grid";
    private static final String SORT_PREF_STRING = "sort_type_pref";
    private static final String INVERT_SORT_PREF_STRING = "invert_sort_pref";

    private static final int SORT_TYPE_DEFAULT = 0;
    private static final int SORT_TYPE_THREAD_ID = 1;
    private static final int SORT_TYPE_IMAGE_COUNT = 2;
    private static final int SORT_TYPE_REPLY_COUNT = 3;

    public static final int TAB_ID = 101;
    private static final String LIST_VIEW_STATE = "post_list_state";

    private RecyclerView listView;

    private String boardName;
    private String boardTitle;

    private PostItemsAdapter postItemsAdapter;

    private View loadingLayout;
    private View errorContainer;
    private TextView errorText;

    private int currentPage = 1;
    private int currentPositionY = 0;

    private SearchView searchView;
    private PostFragment postFragment;
    private ArrayList<ChanPost> postList = new ArrayList<>();
    private int sortType;
    private boolean invertSort;
    private TextView errorRefreshButton;
    private SwipeRefreshLayout listRefreshLayout;

    private PostItemsAdapter.ManagerType adapterType;

    private Spinner toolbarSpinner;
    private Toolbar toolbar;
    private PostItemClickListener clickListener;

    private Disposable catalogSubscription;
    private Disposable fetchBoardsSubscription;
    private Disposable fetchHistorySubscription;
    private Disposable lastAccessSubscription;

    private Bundle postState;
    private boolean createNewPostFragment;
    private Disposable fetchBoardsDisposable;

    private CatalogViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.fragment_catalog_list, container, false);

        listRefreshLayout = v.findViewById(R.id.list_refresh_layout);
        listView = v.findViewById(R.id.catalog_list);
        errorText = v.findViewById(R.id.error_text);
        errorRefreshButton = v.findViewById(R.id.refresh_on_error_button);

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        boolean forceRefresh = false;
        if (savedInstanceState == null) {
            extractExtras(getArguments());
            RxUtil.safeUnsubscribe(lastAccessSubscription);
            lastAccessSubscription = BoardTableConnection.updateLastAccess(boardName)
                    .compose(DatabaseUtils.applySingleSchedulers())
                    .subscribe();
        } else {
            String boardNameFromSavedInstance = savedInstanceState.getString(Extras.EXTRAS_BOARD_NAME, "");
            String boardNameFromArguments = getArguments().getString(Extras.EXTRAS_BOARD_NAME, null);
            forceRefresh = !boardNameFromSavedInstance.equals(boardNameFromArguments);
            if (savedInstanceState.containsKey(Extras.EXTRAS_BOARD_NAME)
                    && getArguments() != null
                    && getArguments().containsKey(Extras.EXTRAS_BOARD_NAME)
                    && TextUtils.equals(savedInstanceState.getString(Extras.EXTRAS_BOARD_NAME), getArguments().getString(Extras.EXTRAS_BOARD_NAME))) {
                extractExtras(savedInstanceState);
            } else {
                extractExtras(getArguments());
                RxUtil.safeUnsubscribe(lastAccessSubscription);
                lastAccessSubscription = BoardTableConnection.updateLastAccess(boardName)
                        .compose(DatabaseUtils.applySingleSchedulers())
                        .subscribe();
            }
        }

        ViewModelProvider.Factory factory = new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) new CatalogViewModel();
            }
        };
        viewModel = ViewModelProviders
                .of(this, factory)
                .get(CatalogViewModel.class);

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sortType = preferences.getInt(SORT_PREF_STRING, SORT_TYPE_DEFAULT);
        invertSort = preferences.getBoolean(INVERT_SORT_PREF_STRING, false);

        TypedValue typedValue = new TypedValue();
        getActivity().getTheme().resolveAttribute(R.attr.actionBarSize, typedValue, true);
        int pixels = getResources().getDimensionPixelSize(typedValue.resourceId);

        if (getActivity() instanceof PostItemClickListener) {
            clickListener = (PostItemClickListener) getActivity();
        }

        listRefreshLayout.setOnRefreshListener(() -> {
            listRefreshLayout.setRefreshing(true);
            refreshBoard(false);
        });

        listRefreshLayout.setProgressViewOffset(false, pixels, pixels + 170);

        final boolean isGrid = preferences.getBoolean(CATALOG_PREF_STRING, false);
        RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
        if (isGrid) {
            if (layoutManager instanceof LinearLayoutManager || layoutManager == null) {
                layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
                ((StaggeredGridLayoutManager) layoutManager).setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
                layoutManager.setItemPrefetchEnabled(false);
                listView.setLayoutManager(layoutManager);
            }
            adapterType = PostItemsAdapter.ManagerType.STAGGERED_GRID;
        } else {
            if (layoutManager instanceof StaggeredGridLayoutManager || layoutManager == null) {
                layoutManager = new LinearLayoutManager(getActivity(), RecyclerView.VERTICAL, false);
                listView.setLayoutManager(layoutManager);
            }
            adapterType = PostItemsAdapter.ManagerType.LIST;
            listRefreshLayout.setVisibility(View.VISIBLE);
        }

        initAdapter(false);

        loadingLayout = view.findViewById(R.id.loading_layout);
        errorContainer = view.findViewById(R.id.error_container);

        errorRefreshButton.setOnClickListener(v -> refreshBoard(true));

        if (savedInstanceState == null || forceRefresh) {
            CatalogTableConnection.clear()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe();


            fetchCatalog(true);

            Log.i(LOG_TAG, "Fetching catalog");
            showLoadingLayout();


        } else {
            Parcelable listState = savedInstanceState.getParcelable(LIST_VIEW_STATE);
            fetchFromDb(listState);
        }

        if (getUserVisibleHint()) {
            initMenu();
        } else if (toolbarSpinner != null) {
            toolbarSpinner.setOnItemSelectedListener(null);
        }
    }

    private void switchToList() {
        if (getActivity() == null) {
            return;
        }

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        preferences.edit().putBoolean(CATALOG_PREF_STRING, false).apply();

        adapterType = PostItemsAdapter.ManagerType.LIST;
        postItemsAdapter = null;
        RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
        if (layoutManager instanceof StaggeredGridLayoutManager || layoutManager == null) {
            layoutManager = new LinearLayoutManager(getActivity(), RecyclerView.VERTICAL, false);
            listView.setLayoutManager(layoutManager);

            initAdapter(true);

            refreshBoard(true);
        }
    }

    private void switchToGrid() {
        if (getActivity() == null) {
            return;
        }

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        preferences.edit().putBoolean(CATALOG_PREF_STRING, true).apply();

        adapterType = PostItemsAdapter.ManagerType.STAGGERED_GRID;
        RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager || layoutManager == null) {

            layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
            ((StaggeredGridLayoutManager) layoutManager).setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
            layoutManager.setItemPrefetchEnabled(false);
            listView.setLayoutManager(layoutManager);

            initAdapter(true);

            refreshBoard(true);
        }
    }

    private void initAdapter(boolean force) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Initializing adapter");
        }

        if (postItemsAdapter == null || force) {
            postItemsAdapter = new PostItemsAdapter(boardName, boardTitle, postList, clickListener);
            postItemsAdapter.setLayoutManager(listView.getLayoutManager());
            listView.setAdapter(postItemsAdapter);

            if (currentPositionY > 0) {
                RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
                if (layoutManager instanceof LinearLayoutManager) {
                    listView.post(() -> layoutManager.scrollToPosition(currentPositionY));
                } else if (layoutManager instanceof StaggeredGridLayoutManager) {
                    listView.post(() -> layoutManager.scrollToPosition(currentPositionY));
                }
//                listView.post(() -> listView.scrollTo(0, currentPositionY));
            }
        }
    }

    private void catalogResponse(final ChanCatalog catalog, @Nullable final Parcelable listState, final boolean refreshing) {
        if (catalog == null) {
            Log.e(LOG_TAG, "Error loading catalog response", new IllegalStateException("Error loading catalog response"));
            return;
        }

        if (getActivity() != null) {
            listRefreshLayout.setRefreshing(false);

            try {
                if (getActivity() != null) {
                    postList.clear();

                    RxUtil.safeUnsubscribe(fetchHistorySubscription);
                    fetchHistorySubscription = HistoryTableConnection.fetchHistory()
                            .compose(DatabaseUtils.applySingleSchedulers())
                            .subscribe(histories -> {
                                List<ChanPost> posts = catalog.getPosts();
                                for (History history : histories) {
                                    for (ChanPost post : posts) {
                                        if (post.getNo() == history.getThreadId()) {
                                            post.setWatched(history.getWatched());
                                        }
                                    }
                                }

                                if (BuildConfig.DEBUG) {
                                    Log.d(LOG_TAG, "Setting items to the adapter");
                                }

                                postList.addAll(posts);
                                postItemsAdapter.setPosts(postList, boardName, boardTitle);

                                if (refreshing) {
                                    scrollListToTop(listView);
                                } else if (listState != null) {
                                    listView.getLayoutManager().onRestoreInstanceState(listState);
                                }

                                showContent();
                            });


                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error loading board", e);
                showError(getString(R.string.generic_board_load_error));
            }

        }
    }

    private void scrollListToTop(final RecyclerView recyclerView) {
        recyclerView.post(() -> recyclerView.scrollToPosition(0));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PostFragment.PICK_IMAGE) {
            final FragmentManager fm = getChildFragmentManager();
            final PostFragment fragment = (PostFragment) fm.findFragmentByTag(POST_FRAGMENT_TAG);

            if (fragment != null) {
                fragment.onActivityResult(requestCode, resultCode, data);
            }

        }

        Log.i(LOG_TAG, "Activity result called");
    }

    public void refreshBoard(final boolean showLoading) {

        if (getActivity() == null) {
            return;
        }

        CatalogTableConnection.clear()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();

        if (showLoading) {
            showLoadingLayout();
        }

        currentPage = 1;

        fetchCatalog(true);
    }

    private void extractExtras(final Bundle bundle) {
        if (bundle.containsKey(Extras.EXTRAS_BOARD_NAME)) {
            boardName = bundle.getString(Extras.EXTRAS_BOARD_NAME);
        }
        if (bundle.containsKey(Extras.EXTRAS_BOARD_TITLE)) {
            boardTitle = bundle.getString(Extras.EXTRAS_BOARD_TITLE);
        }
        if (bundle.containsKey(Extras.EXTRAS_PAGE)) {
            currentPage = bundle.getInt(Extras.EXTRAS_PAGE);
        }
        if (bundle.containsKey(Extras.EXTRAS_POSITION)) {
            currentPositionY = bundle.getInt(Extras.EXTRAS_POSITION);
            Log.i(LOG_TAG, "scroll position=" + currentPositionY);
        }
        if (bundle.containsKey(Extras.EXTRAS_POST_LIST)) {
            bundle.setClassLoader(ChanPost.class.getClassLoader());
            postList = bundle.getParcelableArrayList(Extras.EXTRAS_POST_LIST);
        }
    }

    public void setBoard(final String boardName) {
        this.boardName = boardName;
    }

    private ChanCatalog processCatalog(ChanCatalog catalog) {
        List<ChanPost> posts = catalog.getPosts();
        if (posts != null) {
            FourChanCommentParser.Builder parserBuilder = new FourChanCommentParser.Builder();
            parserBuilder.setContext(getActivity())
                    .setBoardName(boardName)
                    .setQuoteColor(MimiUtil.getInstance().getQuoteColor())
                    .setReplyColor(MimiUtil.getInstance().getReplyColor())
                    .setHighlightColor(MimiUtil.getInstance().getHighlightColor())
                    .setLinkColor(MimiUtil.getInstance().getLinkColor());
            for (int i = 0; i < posts.size(); i++) {

                if (posts.get(i).getCom() != null) {
                    parserBuilder.setComment(posts.get(i).getCom())
                            .setThreadId(posts.get(i).getResto())
                            .setReplies(posts.get(i).getRepliesTo());

                    posts.get(i).setComment(parserBuilder.build().parse());
                }

                if (posts.get(i).getName() != null) {
                    final CharSequence nameSpan = FourChanUtil.getUserName(getResources(), posts.get(i).getName(), posts.get(i).getCapcode());
                    posts.get(i).setDisplayedName(nameSpan);
                }

                if (posts.get(i).getSub() != null) {
                    posts.get(i).setSubject(Html.fromHtml(posts.get(i).getSub()));
                }
            }
        }

        catalog.setPosts(posts);
        return catalog;
    }

    private void fetchFromDb(final Parcelable listState) {
        RxUtil.safeUnsubscribe(catalogSubscription);
        catalogSubscription = CatalogTableConnection.fetchPosts()
                .map(CatalogTableConnection.convertDbPostsToChanPosts())
                .flatMap((Function<List<ChanPost>, Single<ChanCatalog>>) posts -> {
                    ChanCatalog catalog = new ChanCatalog();
                    catalog.setBoardName(boardName);
                    catalog.setPosts(posts == null ? new ArrayList<>() : posts);

                    final FragmentManager fm = getChildFragmentManager();
                    postFragment = (PostFragment) fm.findFragmentByTag(POST_FRAGMENT_TAG);
                    if (postFragment != null) {
                        postFragment.setPostListener(createPostCompleteListener());
                    }

                    return Single.just(catalog);
                })
                .flatMap(sortPosts())
                .map(chanCatalog -> processCatalog(chanCatalog))
                .compose(DatabaseUtils.applySingleSchedulers())
                .subscribe(chanCatalog -> {
                    if (chanCatalog.getPosts().size() > 0) {
                        catalogResponse(chanCatalog, listState, false);
                    } else {
                        refreshBoard(true);
                    }
                }, throwable -> {
                    Log.w(LOG_TAG, "Error fetching boards from the database", throwable);
                    showError(throwable.getLocalizedMessage());
                });
    }

    private void fetchCatalog(final boolean refreshing) {
        RxUtil.safeUnsubscribe(catalogSubscription);
        Log.w(LOG_TAG, viewModel == null ? "Viewmodel is null" : "Viewmodel is not null");
        catalogSubscription = Single.zip(
                viewModel.fetchCatalog(boardName),
                HiddenThreadTableConnection.fetchHiddenThreads(boardName),
                FilterTableConnection.fetchFiltersByBoard(boardName),
                hideThreads())
                .map(catalog -> {
                    if (catalog != null) {
                        Log.d(LOG_TAG, "Processing catalog");
                        return processCatalog(catalog);
                    }
                    Log.d(LOG_TAG, "No catalog to process");
                    return new ChanCatalog();
                })
                .map(chanCatalog -> {
                    Log.d(LOG_TAG, "Putting catalog into database");
                    CatalogTableConnection.putPosts(chanCatalog).subscribe();
                    return chanCatalog;
                })
                .flatMap(sortPosts())
                .compose(DatabaseUtils.applySingleSchedulers())
                .subscribe(catalog -> {
                    Log.d(LOG_TAG, "Finished fetching catalog");
                    if (catalog.getPosts().size() > 0) {
                        catalogResponse(catalog, null, refreshing);
                    } else {
                        throw new Exception("Catalog response is empty");
                    }
                }, throwable -> {
                    showError(getString(NetworkErrorMessage.resourceFor(throwable)));
                    Log.e(LOG_TAG, "Error fetching catalog", throwable);
                });
    }

    private Function3<ChanCatalog, List<HiddenThread>, List<Filter>, ChanCatalog> hideThreads() {
        return (chanCatalog, hiddenThreads, filters) -> {
            if (chanCatalog != null) {
                List<ChanPost> posts = new ArrayList<>();
                for (ChanPost post : chanCatalog.getPosts()) {
                    boolean found = false;
                    for (HiddenThread hiddenThread : hiddenThreads) {
                        if (hiddenThread.getThreadId() == post.getNo()) {
                            found = true;
                            break;
                        }
                    }

                    if (filters != null && filters.size() > 0 && !found) {
                        for (Filter filter : filters) {
                            if (found) {
                                break;
                            }

                            Pattern filterPattern = Pattern.compile(filter.getFilter(), Pattern.CASE_INSENSITIVE);

                            Matcher matcher;
                            if (post.getCom() != null) {
                                matcher = filterPattern.matcher(post.getCom());
                                found = matcher.find();
                            }

                            if (post.getSub() != null && !found) {
                                matcher = filterPattern.matcher(post.getSub());
                                found = matcher.find();
                            }

                            if (post.getName() != null && !found) {
                                matcher = filterPattern.matcher(post.getName());
                                found = matcher.find();
                            }
                        }
                    }

                    if (!found) {
                        posts.add(post);
                    }
                }

                chanCatalog.setPosts(posts);

            }
            return chanCatalog;
        };
    }

    private Function<ChanCatalog, Flowable<ChanCatalog>> hidePosts() {
        return chanCatalog -> null;
    }

    private Function<ChanCatalog, Single<ChanCatalog>> sortPosts() {
        return chanCatalog -> {
            if (chanCatalog != null) {
                List<ChanPost> posts = chanCatalog.getPosts();
                chanCatalog.setPosts(sortPosts(posts, sortType, invertSort));
            }

            return Single.just(chanCatalog);
        };
    }

    private List<ChanPost> sortPosts(List<ChanPost> posts, int type, boolean invert) {
        if (posts == null || posts.size() == 0) {
            return Collections.emptyList();
        }

        final List<ChanPost> updatedPostList = new ArrayList<>(posts);
        if (type == SORT_TYPE_THREAD_ID) {
            Collections.sort(updatedPostList, new ChanPost.ThreadIdComparator());
        } else if (type == SORT_TYPE_IMAGE_COUNT) {
            Collections.sort(updatedPostList, new ChanPost.ImageCountComparator());
        } else if (type == SORT_TYPE_REPLY_COUNT) {
            Collections.sort(updatedPostList, new ChanPost.ReplyCountComparator());
        }

        if (invert) {
            Collections.reverse(updatedPostList);
        }

        return updatedPostList;

    }

    private void showLoadingLayout() {
        listRefreshLayout.setVisibility(View.GONE);
        errorContainer.setVisibility(View.GONE);
        if (loadingLayout != null) {
            loadingLayout.setVisibility(View.VISIBLE);
        }

    }

    private void showContent() {

        if (getActivity() == null) {
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "Not showing content because activity is null");
            }
            return;
        }

        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Showing content");
        }

        listRefreshLayout.setRefreshing(false);
        listRefreshLayout.setVisibility(View.VISIBLE);

        if (errorContainer != null) {
            errorContainer.setVisibility(View.GONE);
        }

        if (loadingLayout != null) {
            loadingLayout.setVisibility(View.GONE);
        }

    }

    private void showError(final String msg) {
        if (getActivity() == null) {
            return;
        }

        if (loadingLayout != null) {
            loadingLayout.setVisibility(View.GONE);
        }

        if (errorContainer != null) {
            errorText.setText(msg);
            errorContainer.setVisibility(View.VISIBLE);
        }

        listRefreshLayout.setRefreshing(false);
        listRefreshLayout.setVisibility(View.GONE);
    }

    @Override
    public int getMenuRes() {
        return R.menu.post_list;
    }

    @Override
    public void initMenu() {
        super.initMenu();

        setupActionBar(true);
        if (getActivity() != null) {
            getActivity().invalidateOptionsMenu();
        }
    }

    private void setupActionBar(final boolean fullSetup) {
        if (getActivity() != null && ((MimiActivity) getActivity()).getToolbar() != null) {
            MimiActivity activity = (MimiActivity) getActivity();
            toolbar = activity.getToolbar();
            toolbar.getMenu().clear();
            activity.getSupportActionBar().setSubtitle(null);

            if (toolbarSpinner == null) {
                toolbarSpinner = (Spinner) toolbar.findViewById(R.id.board_spinner);
                toolbarSpinner.setOnItemSelectedListener(this);

                if (toolbarSpinner != null) {
                    RxUtil.safeUnsubscribe(fetchBoardsDisposable);
                    fetchBoardsDisposable = BoardTableConnection.fetchBoards(MimiUtil.getBoardOrder())
                            .compose(DatabaseUtils.applySingleSchedulers())
                            .subscribe(boards -> {
                                if (boards != null) {
                                    int boardPos = -1;
                                    for (int i = 0; i < boards.size(); i++) {
                                        if (boards.get(i).getName().equals(boardName)) {
                                            boardPos = i;
                                        }
                                    }

                                    if (toolbarSpinner != null) {
                                        toolbarSpinner.setSelection(boardPos);
                                    }
                                }
                            }, throwable -> Log.e(LOG_TAG, "Error fetching boards"));

                }
            }

            if (toolbarSpinner != null && MimiUtil.getLayoutType(getActivity()) != LayoutType.TABBED) {
                activity.getSupportActionBar().setTitle(null);
                toolbarSpinner.setVisibility(View.VISIBLE);
            } else {
                activity.getSupportActionBar().setTitle(boardTitle);

                if (toolbarSpinner != null) {
                    toolbarSpinner.setVisibility(View.GONE);
                }
            }

            activity.getSupportActionBar().setSubtitle(null);

            if (fullSetup) {
                final int boardOrder = MimiUtil.getBoardOrder();

                RxUtil.safeUnsubscribe(fetchBoardsSubscription);
                fetchBoardsSubscription = BoardTableConnection.fetchBoards(boardOrder)
                        .flatMap((Function<List<com.emogoth.android.phone.mimi.db.models.Board>, Single<List<ChanBoard>>>) boards -> Single.just(BoardTableConnection.convertBoardDbModelsToChanBoards(boards)))
                        .compose(DatabaseUtils.applySingleSchedulers())
                        .subscribe(this::initSpinner);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();

        inflater.inflate(getMenuRes(), menu);

        final MenuItem catalogItem = menu.findItem(R.id.catalog_menu);
        final MenuItem listItem = menu.findItem(R.id.list_menu);
        setupListType(catalogItem, listItem);

        final MenuItem invertSortItem = menu.findItem(R.id.invert_sort_menu);
        invertSortItem.setChecked(invertSort);

        final MenuItem searchItem = menu.findItem(R.id.search_menu);
        setupSearchMenu(searchItem);

        final MenuItem sortMenu = menu.findItem(R.id.sort_menu);
        final MenuItem[] sortMenuItems = new MenuItem[4];
        sortMenuItems[SORT_TYPE_DEFAULT] = menu.findItem(R.id.sort_default);
        sortMenuItems[SORT_TYPE_THREAD_ID] = menu.findItem(R.id.sort_time);
        sortMenuItems[SORT_TYPE_IMAGE_COUNT] = menu.findItem(R.id.sort_image_count);
        sortMenuItems[SORT_TYPE_REPLY_COUNT] = menu.findItem(R.id.sort_reply_count);

        setupSortType(sortMenuItems);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.refresh_menu:
                refreshBoard(true);
                break;

            case R.id.catalog_menu:
                if (!item.isChecked()) {
                    item.setChecked(true);
                    switchToGrid();
                }
                break;
            case R.id.list_menu:
                if (!item.isChecked()) {
                    item.setChecked(true);
                    switchToList();
                }
                break;
            case R.id.invert_sort_menu:
                if (getActivity() == null) {
                    return false;
                }

                invertSort = !invertSort;
                item.setChecked(invertSort);

                PreferenceManager.getDefaultSharedPreferences(getActivity())
                        .edit()
                        .putBoolean(INVERT_SORT_PREF_STRING, invertSort)
                        .apply();

                if (postItemsAdapter != null) {
                    postList = new ArrayList<>(postItemsAdapter.getPosts());
                    Collections.reverse(postList);
                    postItemsAdapter.setPosts(postList, boardName, boardTitle);
                    listView.smoothScrollToPosition(0);
                }
                break;
            case R.id.filter_menu:
                if (getActivity() != null) {
                    FilterDialog dialog = new FilterDialog(getContext(), boardName, null, new FilterView.ButtonClickListener() {
                        @Override
                        public void onSaveClicked(View v) {
                            Log.d(LOG_TAG, "OK clicked");
                        }

                        @Override
                        public void onEditClicked(View v) {
                            Log.d(LOG_TAG, "Edit clicked");
                            EditFiltersActivity.start(getActivity(), boardName);
                        }

                        @Override
                        public void onCancelClicked(View v) {
                            Log.d(LOG_TAG, "Cancel clicked");
                        }
                    });

                    dialog.show();
                    dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

                    return true;
                }
                break;
            default:
                break;
        }

        return true;
    }

    private void setupSortType(final MenuItem[] sortItems) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sortType = preferences.getInt(SORT_PREF_STRING, SORT_TYPE_DEFAULT);

        sortItems[sortType].setChecked(true);
        for (int i = 0; i < sortItems.length; i++) {
            final int type = i;
            sortItems[i].setOnMenuItemClickListener(item -> {
                if (getActivity() != null && type != sortType) {
                    final SharedPreferences preferences1 = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    sortType = type;

                    preferences1.edit().putInt(SORT_PREF_STRING, sortType).apply();
                    item.setChecked(true);

                    if (sortType != SORT_TYPE_DEFAULT) {
                        if (postItemsAdapter != null) {
                            postItemsAdapter.setPosts(sortPosts(postItemsAdapter.getPosts(), sortType, invertSort), boardName, boardTitle);
                            listView.smoothScrollToPosition(0);
                        }
                    } else {
                        showLoadingLayout();
                        fetchCatalog(true);
                    }
                }
                return true;
            });
        }
    }

    private void initSpinner(List<ChanBoard> boards) {
        if (toolbarSpinner.getAdapter() == null) {
            int boardPos = -1;
            for (int i = 0; i < boards.size(); i++) {
                if (boards.get(i).getName().equals(boardName)) {
                    boardPos = i;
                }
            }

            Log.d(LOG_TAG, "Setting up toolbar spinner");

            final BoardDropDownAdapter adapter = new BoardDropDownAdapter(getActivity(), R.layout.board_spinner_item, boards, BoardDropDownAdapter.MODE_ACTIONBAR);
            toolbarSpinner.setAdapter(adapter);

            if (boardPos != -1) {
                toolbarSpinner.setSelection(boardPos);
            }
        }
    }

    private void setupSearchMenu(MenuItem searchItem) {
        searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String s) {
                    if (TextUtils.isEmpty(s)) {
                        listRefreshLayout.setEnabled(true);

                    } else {
                        listRefreshLayout.setEnabled(false);
                    }

                    if (postItemsAdapter != null) {
                        postItemsAdapter.getFilter().filter(s);
                    }

                    return true;
                }

                @Override
                public boolean onQueryTextChange(String s) {
                    if (TextUtils.isEmpty(s)) {
                        listRefreshLayout.setEnabled(true);
                    } else {
                        listRefreshLayout.setEnabled(false);
                    }

                    if (postItemsAdapter != null) {
                        postItemsAdapter.getFilter().filter(s);
                    }

                    return true;
                }
            });
        }
    }

    private void setupListType(MenuItem catalogItem, MenuItem listItem) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final boolean isGrid = preferences.getBoolean(CATALOG_PREF_STRING, false);

        if (isGrid) {
            catalogItem.setChecked(true);
        } else {
            listItem.setChecked(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        RxUtil.safeUnsubscribe(catalogSubscription);
        RxUtil.safeUnsubscribe(fetchBoardsSubscription);
        RxUtil.safeUnsubscribe(fetchHistorySubscription);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (postItemsAdapter != null) {
            postItemsAdapter.destroy();
            postItemsAdapter = null;
        }

        if (toolbarSpinner != null) {
            toolbarSpinner = null;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (listView != null) {
            RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
            final int position;
            if (layoutManager instanceof LinearLayoutManager) {
                position = ((LinearLayoutManager) layoutManager).findFirstCompletelyVisibleItemPosition();
            } else if (layoutManager instanceof StaggeredGridLayoutManager) {
                int [] pos = null;
                ((StaggeredGridLayoutManager) layoutManager).findFirstCompletelyVisibleItemPositions(pos);
                position = 0;
            } else {
                position = 0;

            }
            outState.putInt(Extras.EXTRAS_POSITION, position);

            if (listView.getLayoutManager() != null) {
                outState.putParcelable(LIST_VIEW_STATE, listView.getLayoutManager().onSaveInstanceState());
            }
        }
        outState.putString(Extras.EXTRAS_BOARD_NAME, boardName);
        outState.putString(Extras.EXTRAS_BOARD_TITLE, boardTitle);
        outState.putInt(Extras.EXTRAS_PAGE, currentPage);

        outState.putBoolean("rotated", true);
    }

    private void showPostForm() {
        if (getActivity() == null || !isAdded()) {
            return;
        }

        try {

            listView.smoothScrollBy(0, 0);

            final FragmentManager fm = getChildFragmentManager();
            final Bundle args;
            if (postState == null || createNewPostFragment) {
                createNewPostFragment = false;

                args = new Bundle();
                args.putString(Extras.EXTRAS_BOARD_NAME, boardName);
                args.putString(Extras.EXTRAS_BOARD_TITLE, boardTitle);
                args.putBoolean(Extras.EXTRAS_POST_NEW, true);
            } else {
                args = new Bundle(postState);
            }

            postFragment = new PostFragment();
            postFragment.setPostListener(createPostCompleteListener());
            postFragment.setArguments(args);

            postFragment.show(fm, POST_FRAGMENT_TAG);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Caught error while showing post form", e);
        }
    }

    private PostFragment.PostListener createPostCompleteListener() {
        return new PostFragment.PostListener() {
            @Override
            public void onStartPost() {
                showPostProgress();
                if (postFragment != null) {
                    postFragment.dismiss();
                }
            }

            @Override
            public void onSuccess(final String postId) {
                try {
                    Log.i(LOG_TAG, "Post ID: " + postId);
                    showPostStatus(getString(R.string.success));
                    postFragment = null;
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error creating a new post", e);
                }

            }

            @Override
            public void onError(Throwable error) {
                error.printStackTrace();
                showPostStatus(error.getLocalizedMessage());
            }

            @Override
            public void onDismiss() {
                if (postFragment != null) {
                    postState = postFragment.saveState();
                }
            }

            @Override
            public void onCanceled() {
                final FragmentTransaction fm = getChildFragmentManager().beginTransaction();
                if (postFragment != null) {
                    postFragment.dismiss();
                    fm.remove(postFragment).commit();
                    postFragment = null;

                    createNewPostFragment = true;
                }
            }
        };
    }

    private void showPostProgress() {
        Snackbar.make(listRefreshLayout, R.string.sending, Snackbar.LENGTH_SHORT).show();
    }

    private void showPostStatus(final String status) {
        Snackbar.make(listRefreshLayout, status, Snackbar.LENGTH_SHORT).show();

    }

    @Override
    public boolean onBackPressed() {
        if (toolbar != null && toolbar.hasExpandedActionView()) {
            toolbar.collapseActionView();

            return true;
        }

        return false;
    }

    @Override
    public int getTabId() {
        return TAB_ID;
    }

    @Override
    public String getTitle() {
        return boardTitle;
    }

    @Override
    public String getSubtitle() {
        return null;
    }

    @Override
    public String getPageName() {
        return "post_list";
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent.getCount() > position) {
            ChanBoard b = (ChanBoard) parent.getItemAtPosition(position);
            boardName = b.getName();
            boardTitle = b.getTitle();

            refreshBoard(true);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void addContent() {
        showPostForm();
    }

    public interface CatalogItemClickListener {
        void onPostItemClick(final List<ChanPost> Posts, final int position, final String boardName, final String boardTitle);
    }
}
