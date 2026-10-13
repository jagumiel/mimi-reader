/*
 * Copyright (c) 2016. Eli Connelly
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.emogoth.android.phone.mimi.fragment;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.widget.ViewPager2;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.activity.MimiActivity;
import com.emogoth.android.phone.mimi.adapter.ThreadPagerAdapter;
import com.emogoth.android.phone.mimi.interfaces.ContentInterface;
import com.emogoth.android.phone.mimi.interfaces.ReplyClickListener;
import com.emogoth.android.phone.mimi.interfaces.ThreadSelectedListener;
import com.emogoth.android.phone.mimi.model.ThreadInfo;
import com.emogoth.android.phone.mimi.util.Extras;
import com.mimireader.chanlib.models.ChanPost;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


/**
 * A fragment representing a swipeable collection of threads.
 */
public class ThreadPagerFragment extends MimiFragmentBase implements ContentInterface, ReplyClickListener, ThreadSelectedListener {
    public static final String LOG_TAG = ThreadPagerFragment.class.getSimpleName();

    private String boardName;
    private String boardTitle;
    private ArrayList<ThreadInfo> threadList = new ArrayList<>();
    private int currentPosition;
    private MimiFragmentBase currentFragment;
    private int currentPage;
    private ViewPager2 threadPager;
    private ThreadPagerAdapter threadPagerAdapter;
    private int[] unreadCountList;
    private int viewingHistory;
    private ChanPost initialPost;

    private final ViewPager2.OnPageChangeCallback pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            currentPosition = position;
            updateCurrentFragment(position);
        }
    };

    private final FragmentManager.FragmentLifecycleCallbacks fragmentLifecycleCallbacks =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentResumed(@NonNull FragmentManager fragmentManager,
                                              @NonNull Fragment fragment) {
                    if (threadPager == null || threadPagerAdapter == null) {
                        return;
                    }
                    final MimiFragmentBase activeFragment = threadPagerAdapter
                            .getActiveFragment(threadPager.getCurrentItem());
                    if (fragment == activeFragment) {
                        activateFragment(activeFragment);
                    }
                }
            };

    public ThreadPagerFragment() {
    }

    @SuppressWarnings("deprecation")
    private void extractExtras(@Nullable final Bundle bundle) {
        if (bundle == null) {
            return;
        }
        if (bundle.containsKey(Extras.EXTRAS_BOARD_NAME)) {
            boardName = bundle.getString(Extras.EXTRAS_BOARD_NAME);
        }
        if (bundle.containsKey(Extras.EXTRAS_BOARD_TITLE)) {
            boardTitle = bundle.getString(Extras.EXTRAS_BOARD_TITLE);
        }
        if (bundle.containsKey(Extras.EXTRAS_THREAD_LIST)) {
            bundle.setClassLoader(ThreadInfo.class.getClassLoader());
            final ArrayList<ThreadInfo> restoredThreads;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                restoredThreads = bundle.getParcelableArrayList(
                        Extras.EXTRAS_THREAD_LIST, ThreadInfo.class);
            } else {
                restoredThreads = bundle.getParcelableArrayList(Extras.EXTRAS_THREAD_LIST);
            }
            if (restoredThreads != null) {
                threadList = restoredThreads;
            }
        }
        if (bundle.containsKey(Extras.EXTRAS_POSITION)) {
            currentPosition = bundle.getInt(Extras.EXTRAS_POSITION);
        }
        if (bundle.containsKey(Extras.EXTRAS_PAGE)) {
            currentPage = bundle.getInt(Extras.EXTRAS_PAGE);
        }
        if (bundle.containsKey(Extras.EXTRAS_UNREAD_COUNT)) {
            unreadCountList = bundle.getIntArray(Extras.EXTRAS_UNREAD_COUNT);
        }
        if (bundle.containsKey(Extras.EXTRAS_VIEWING_HISTORY)) {
            viewingHistory = bundle.getInt(Extras.EXTRAS_VIEWING_HISTORY);
        }
        if (bundle.containsKey(Extras.EXTRAS_THREAD_FIRST_POST)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                initialPost = bundle.getParcelable(Extras.EXTRAS_THREAD_FIRST_POST, ChanPost.class);
            } else {
                initialPost = bundle.getParcelable(Extras.EXTRAS_THREAD_FIRST_POST);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_postitem_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        extractExtras(savedInstanceState != null ? savedInstanceState : getArguments());

        threadPager = view.findViewById(R.id.thread_pager);
        currentPosition = clampPosition(currentPosition);
        threadPagerAdapter = new ThreadPagerAdapter(
                this, threadList, unreadCountList, initialPost, currentPosition);
        getChildFragmentManager().registerFragmentLifecycleCallbacks(
                fragmentLifecycleCallbacks, false);
        threadPager.setAdapter(threadPagerAdapter);
        threadPager.registerOnPageChangeCallback(pageChangeCallback);

        if (!threadList.isEmpty()) {
            threadPager.setCurrentItem(currentPosition, false);
            updateCurrentFragment(currentPosition);
        }
    }

    private int clampPosition(int position) {
        if (threadList.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(position, threadList.size() - 1));
    }

    private void updateCurrentFragment(final int position) {
        if (threadPager == null) {
            return;
        }
        threadPager.post(() -> {
            if (threadPagerAdapter != null) {
                activateFragment(threadPagerAdapter.getActiveFragment(position));
            }
        });
    }

    private void activateFragment(@Nullable final MimiFragmentBase fragment) {
        if (fragment == null || getActivity() == null) {
            return;
        }
        currentFragment = fragment;
        final MimiActivity activity = (MimiActivity) getActivity();
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(fragment.getTitle());
            activity.getSupportActionBar().setSubtitle(fragment.getSubtitle());
        }
        fragment.initMenu();
    }

    @Override
    public boolean onBackPressed() {
        if (threadPager == null || threadPagerAdapter == null) {
            return false;
        }
        final MimiFragmentBase fragment = threadPagerAdapter
                .getActiveFragment(threadPager.getCurrentItem());
        return fragment != null && fragment.onBackPressed();
    }

    @Override
    public boolean canHandleBack() {
        if (threadPager == null || threadPagerAdapter == null) {
            return false;
        }
        final MimiFragmentBase fragment = threadPagerAdapter
                .getActiveFragment(threadPager.getCurrentItem());
        return fragment != null && fragment.canHandleBack();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (threadPager != null) {
            updateCurrentFragment(threadPager.getCurrentItem());
        }
    }

    @Override
    public void onDestroyView() {
        if (threadPager != null) {
            threadPager.unregisterOnPageChangeCallback(pageChangeCallback);
            threadPager.setAdapter(null);
        }
        getChildFragmentManager().unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallbacks);
        threadPager = null;
        threadPagerAdapter = null;
        currentFragment = null;
        super.onDestroyView();
    }

    @Override
    public String getTitle() {
        return currentFragment == null ? null : currentFragment.getTitle();
    }

    @Override
    public String getSubtitle() {
        return currentFragment == null ? null : currentFragment.getSubtitle();
    }

    @Override
    public String getPageName() {
        return "thread_pager";
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(Extras.EXTRAS_BOARD_NAME, boardName);
        outState.putString(Extras.EXTRAS_BOARD_TITLE, boardTitle);
        outState.putInt(Extras.EXTRAS_POSITION,
                threadPager == null ? currentPosition : threadPager.getCurrentItem());
        outState.putInt(Extras.EXTRAS_PAGE, currentPage);
        outState.putInt(Extras.EXTRAS_VIEWING_HISTORY, viewingHistory);
        outState.putParcelableArrayList(Extras.EXTRAS_THREAD_LIST, threadList);
        if (unreadCountList != null) {
            outState.putIntArray(Extras.EXTRAS_UNREAD_COUNT, unreadCountList);
        }
        if (initialPost != null) {
            outState.putParcelable(Extras.EXTRAS_THREAD_FIRST_POST, initialPost);
        }
        super.onSaveInstanceState(outState);
    }

    public void setBookmarkCount(final int count) {
        if (getActivity() != null) {
            getActivity().supportInvalidateOptionsMenu();
        }
    }

    @Override
    public void onThreadSelected(@NotNull String boardName, long threadId, int position) {
        final ThreadInfo threadInfo = new ThreadInfo(threadId, boardName, "", false);
        final int index = threadList.indexOf(threadInfo);
        if (threadPager != null && index >= 0) {
            threadPager.setCurrentItem(index, true);
        }
    }

    @Override
    public void addContent() {
        if (currentFragment instanceof ContentInterface) {
            ((ContentInterface) currentFragment).addContent();
        }
    }

    @Override
    public void onReplyClicked(@NotNull String boardName, long threadId, long id,
                               @NotNull List<String> replies) {
        if (currentFragment instanceof ReplyClickListener) {
            ((ReplyClickListener) currentFragment).onReplyClicked(
                    boardName, threadId, id, replies);
        }
    }
}
