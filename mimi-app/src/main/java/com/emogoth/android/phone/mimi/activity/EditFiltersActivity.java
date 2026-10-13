package com.emogoth.android.phone.mimi.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.adapter.BoardDropDownAdapter;
import com.emogoth.android.phone.mimi.adapter.FilterListAdapter;
import com.emogoth.android.phone.mimi.db.BoardTableConnection;
import com.emogoth.android.phone.mimi.db.DatabaseUtils;
import com.emogoth.android.phone.mimi.db.FilterTableConnection;
import com.emogoth.android.phone.mimi.db.models.Filter;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import com.emogoth.android.phone.mimi.util.RxUtil;

import java.util.List;

import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

public class EditFiltersActivity extends MimiActivity {
    public static final String LOG_TAG = EditFiltersActivity.class.getSimpleName();
    public static final String EXTRA_BOARD_NAME = "board_name_extra";

    private RecyclerView filterList;
    private AppCompatSpinner boardSpinner;

    private Disposable filtersSubscription;
    private Disposable boardListSubscription;

    private FilterListAdapter filterListAdapter;
    private BoardDropDownAdapter boardsAdapter;

    private String boardName;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_filter);

        filterList = (RecyclerView) findViewById(R.id.filter_list);
        boardSpinner = (AppCompatSpinner) findViewById(R.id.board_spinner);

        initRecyclerView();

        Toolbar toolbar = (Toolbar) findViewById(R.id.mimi_toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_nav_arrow_back);
        toolbar.setLogo(null);
        toolbar.setTitle(R.string.edit_filters);
        setToolbar(toolbar);

        boardName = getIntent().getStringExtra(EXTRA_BOARD_NAME);
        loadBoards(boardName);
    }

    protected void initRecyclerView() {
        filterList.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        filterList.addItemDecoration(new DividerItemDecoration(this, RecyclerView.VERTICAL));
        ItemTouchHelper.SimpleCallback touchHelperCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                if (viewHolder instanceof FilterListAdapter.FilterViewHolder) {
                    final FilterListAdapter.FilterViewHolder vh = (FilterListAdapter.FilterViewHolder) viewHolder;
                    final Disposable dis = FilterTableConnection.removeFilter(boardName, vh.name.getText().toString())
                            .compose(DatabaseUtils.applySingleSchedulers())
                            .subscribe(success -> filterListAdapter.removeFilter(vh.getAdapterPosition()));
                }
            }
        };

        ItemTouchHelper touchHelper = new ItemTouchHelper(touchHelperCallback);
        touchHelper.attachToRecyclerView(filterList);
    }

    protected void loadBoards(final String boardName) {
        boardListSubscription = BoardTableConnection.fetchBoards(MimiUtil.getBoardOrder())
                .map(BoardTableConnection::convertBoardDbModelsToChanBoards)
                .compose(DatabaseUtils.applySingleSchedulers())
                .subscribe(
                        chanBoards -> {
                            if (boardsAdapter != null) {
                                boardsAdapter.setBoards(chanBoards);
                                return;
                            }

                            boardsAdapter = new BoardDropDownAdapter(EditFiltersActivity.this, R.layout.board_filter_spinner_item, chanBoards, BoardDropDownAdapter.MODE_ACTIONBAR);
                            boardsAdapter.setPromptEnabled(true);
                            boardsAdapter.setPromptText(R.string.all);
                            boardSpinner.setAdapter(boardsAdapter);
                            boardSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                @Override
                                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                    if (position == 0) {
                                        loadFilters(null);
                                        return;
                                    }

                                    String board = chanBoards.get(position - 1).getName();
                                    loadFilters(board);
                                }

                                @Override
                                public void onNothingSelected(AdapterView<?> adapterView) {
                                    loadFilters(null);
                                }
                            });

                            int index = -1;
                            if (!TextUtils.isEmpty(boardName)) {
                                for (int i = 0; i < chanBoards.size(); i++) {
                                    if (boardName.equals(chanBoards.get(i).getName())) {
                                        index = i;
                                        break;
                                    }
                                }
                            }

                            if (index >= 0) {
                                final int pos = index + 1;
                                boardSpinner.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        boardSpinner.setSelection(pos);
                                    }
                                });
                            }

                        },
                        new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) {

                            }
                        });

    }

    protected void loadFilters(String boardName) {
        final Single<List<Filter>> loadFiltersObservable;
        if (TextUtils.isEmpty(boardName)) {
            loadFiltersObservable = FilterTableConnection.fetchFilter();
        } else {
            loadFiltersObservable = FilterTableConnection.fetchFiltersByBoard(boardName);
        }

        filtersSubscription = loadFiltersObservable
                .compose(DatabaseUtils.applySingleSchedulers())
                .subscribe(
                filters -> {
                    if (filterListAdapter == null) {
                        filterListAdapter = new FilterListAdapter(filters);
                        filterList.setAdapter(filterListAdapter);
                    } else {
                        filterListAdapter.setFilters(filters);
                    }
                },
                throwable -> Log.e(LOG_TAG, "Error fetching filters", throwable));

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
        }

        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RxUtil.safeUnsubscribe(filtersSubscription);
        RxUtil.safeUnsubscribe(boardListSubscription);
    }

    public static void start(Activity activity, String boardName) {
        if (activity == null) {
            return;
        }

        Intent intent = new Intent(activity, EditFiltersActivity.class);

        if (!TextUtils.isEmpty(boardName)) {
            intent.putExtra(EXTRA_BOARD_NAME, boardName);
        }

        activity.startActivity(intent);
    }

    @Override
    protected String getPageName() {
        return "edit_filters";
    }
}
