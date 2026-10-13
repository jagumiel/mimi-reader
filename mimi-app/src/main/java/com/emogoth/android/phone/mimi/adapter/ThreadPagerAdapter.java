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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.emogoth.android.phone.mimi.fragment.MimiFragmentBase;
import com.emogoth.android.phone.mimi.fragment.ThreadDetailFragment;
import com.emogoth.android.phone.mimi.model.ThreadInfo;
import com.emogoth.android.phone.mimi.util.Extras;
import com.mimireader.chanlib.models.ChanPost;

import java.util.List;


public class ThreadPagerAdapter extends FragmentStateAdapter {
    private static final String ARG_PAGER_POSITION = "mimi_thread_pager_position";

    private final List<ThreadInfo> threadList;
    private final int[] unreadCountList;
    private final int postPosition;
    private final ChanPost initialPost;
    private final FragmentManager fragmentManager;

    public ThreadPagerAdapter(final Fragment owner, final List<ThreadInfo> threadList,
                              final int[] unreadCountList, final ChanPost initialPost,
                              final int postPosition) {
        super(owner);
        fragmentManager = owner.getChildFragmentManager();
        this.threadList = threadList;
        this.unreadCountList = unreadCountList;
        this.initialPost = initialPost;
        this.postPosition = postPosition;
    }

    @NonNull
    @Override
    public Fragment createFragment(final int position) {
        final MimiFragmentBase fragment = new ThreadDetailFragment();
        final Bundle args = new Bundle();
        final ThreadInfo thread = threadList.get(position);

        args.putString(Extras.EXTRAS_BOARD_NAME, thread.getBoardName());
        args.putString(Extras.EXTRAS_BOARD_TITLE, thread.getBoardTitle());
        args.putLong(Extras.EXTRAS_THREAD_ID, thread.getThreadId());
        args.putInt(Extras.LOADER_ID, position % 3);
        args.putInt(ARG_PAGER_POSITION, position);

        if (unreadCountList != null && position < unreadCountList.length) {
            args.putInt(Extras.EXTRAS_UNREAD_COUNT, unreadCountList[position]);
        }

        if (position == postPosition && initialPost != null) {
            args.putParcelable(Extras.EXTRAS_THREAD_FIRST_POST, initialPost);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getItemCount() {
        return threadList.size();
    }

    public MimiFragmentBase getActiveFragment(final int position) {
        if (position < 0 || position >= threadList.size()) {
            return null;
        }
        for (Fragment fragment : fragmentManager.getFragments()) {
            final Bundle arguments = fragment.getArguments();
            if (fragment instanceof MimiFragmentBase && arguments != null
                    && arguments.getInt(ARG_PAGER_POSITION, -1) == position) {
                return (MimiFragmentBase) fragment;
            }
        }
        return null;
    }
}
