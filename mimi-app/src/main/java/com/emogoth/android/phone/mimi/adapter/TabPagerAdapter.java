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
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.app.MimiApplication;
import com.emogoth.android.phone.mimi.fragment.BoardItemListFragment;
import com.emogoth.android.phone.mimi.fragment.HistoryFragment;
import com.emogoth.android.phone.mimi.fragment.PostItemsListFragment;
import com.emogoth.android.phone.mimi.fragment.ThreadDetailFragment;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class TabPagerAdapter extends FragmentStateAdapter {
    private static final String LOG_TAG = TabPagerAdapter.class.getSimpleName();
    private static final String ARG_TAB_STABLE_ID = "mimi_tab_stable_id";
    private final List<TabItem> tabItems = new ArrayList<>();
    private final FragmentManager fragmentManager;

    public TabPagerAdapter(FragmentActivity activity) {
        super(activity);
        fragmentManager = activity.getSupportFragmentManager();

        final String title = MimiApplication.getInstance().getApplicationContext().getString(R.string.boards);
        tabItems.add(new TabItem(TabType.BOARDS, null, BoardItemListFragment.TAB_ID, title, null));
    }

    public TabPagerAdapter(FragmentActivity activity, List<TabItem> items) {
        super(activity);
        fragmentManager = activity.getSupportFragmentManager();

        if (items != null) {
            tabItems.addAll(items);
        }
    }

    public TabItem getTab(int position) {
        if (position >= 0 && position < tabItems.size()) {
            return tabItems.get(position);
        }

        return null;
    }

    @Override
    public Fragment createFragment(int position) {
        final TabItem item = tabItems.get(position);
        final Fragment fragment;
        switch (item.getTabType()) {
            case BOARDS:
                fragment = new BoardItemListFragment();
                break;
            case POSTS:
                fragment = new PostItemsListFragment();
                break;
            case THREAD:
                fragment = new ThreadDetailFragment();
                break;
            case HISTORY:
                fragment = new HistoryFragment();
                break;
            default:
                fragment = new Fragment();
                break;
        }

        final Bundle arguments = item.getBundle() == null
                ? new Bundle()
                : new Bundle(item.getBundle());
        arguments.putLong(ARG_TAB_STABLE_ID, item.getStableId());
        fragment.setArguments(arguments);
        return fragment;
    }

    private long getStableItemId(int position) {
        return tabItems.get(position).getStableId();
    }

    @Override
    public long getItemId(int position) {
        return getStableItemId(position);
    }

    @Override
    public boolean containsItem(long itemId) {
        for (TabItem item : tabItems) {
            if (item.getStableId() == itemId) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getItemCount() {
        return tabItems.size();
    }

    public int getCount() {
        return getItemCount();
    }

    public Fragment getActiveFragment(int position) {
        if (position < 0 || position >= tabItems.size()) {
            return null;
        }
        final long stableId = getStableItemId(position);
        for (Fragment fragment : fragmentManager.getFragments()) {
            final Bundle arguments = fragment.getArguments();
            if (arguments != null && arguments.getLong(ARG_TAB_STABLE_ID, Long.MIN_VALUE) == stableId) {
                return fragment;
            }
        }
        return null;
    }

    public int getIndex(long threadId) {
        for (int i = 0; i < tabItems.size(); i++) {
            if (tabItems.get(i).getId() == threadId) {
                return i;
            }
        }
        return -1;
    }

    public int addItem(TabItem item) {
        try {
            final int index = tabItems.indexOf(item);
            if (index >= 0 && tabItems.get(index).getBundle() != null) {
                return index;
            }

            tabItems.add(item);
            notifyItemInserted(tabItems.size() - 1);

            return tabItems.size() - 1;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error adding tab", e);
        }

        return -1;
    }

    public TabItem getTabItem(int pos) {
        TabItem item = null;
        if (tabItems != null && pos >= 0 && pos < tabItems.size()) {
            item = tabItems.get(pos);
        }

        return item;
    }

    public void setItemAtIndex(int index, TabItem item) {
        if (index >= 0 && index < tabItems.size()) {
            tabItems.set(index, item);
            notifyItemChanged(index);
        }
    }

    public void removeItemAtIndex(int index) {
        if (index >= 0 && index < tabItems.size()) {
            tabItems.remove(index);
            notifyItemRemoved(index);
        }
    }

    public int getPositionById(long id) {
        int index = -1;
        for (int i = 0; i < tabItems.size(); i++) {
            if (tabItems.get(i).getId() == id) {
                index = i;
            }
        }

        return index;
    }

    public void removeItemById(long id) {
        int i = getPositionById(id);
        if (i < 0) {
            return;
        }

        tabItems.remove(i);
        notifyItemRemoved(i);
    }

    public List<TabItem> getItems() {
        return tabItems;
    }

    public enum TabType {
        BOARDS, POSTS, THREAD, HISTORY
    }

    public static class TabItem implements Parcelable {
        private final TabType tabType;
        private final long id;
        private final Bundle bundle;
        private final String title;
        private final String subtitle;

        public TabItem(TabType tabType, Bundle bundle, long id, String title, String subtitle) {
            this.tabType = tabType;
            this.bundle = bundle;
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
        }

        public TabItem(TabType tabType) {
            this.tabType = tabType;
            this.bundle = null;
            this.id = 100;
            this.title = null;
            this.subtitle = null;
        }

        public TabType getTabType() {
            return tabType;
        }

        public Bundle getBundle() {
            return bundle;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public long getId() {
            return id;
        }

        public long getStableId() {
            long result = 1125899906842597L;
            result = 31L * result + (tabType == null ? 0 : tabType.ordinal());
            result = 31L * result + id;
            result = 31L * result + (title == null ? 0 : title.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TabItem tabItem = (TabItem) o;

            return tabType == tabItem.tabType && title != null && (title.equals(tabItem.title) && id == tabItem.id);

            //            return !(bundle != null ? !equalBundles(bundle,tabItem.bundle) : tabItem.bundle != null);

        }

        @Override
        public int hashCode() {
            int result = tabType.hashCode();
            result = 31 * result + (bundle != null ? bundle.hashCode() : 0);
            return result;
        }

        public static boolean equalBundles(Bundle one, Bundle two) {
            if ((one == null && two != null) || (one != null && two == null))
                return false;

            if (one == null && two == null)
                return true;

            if (one.size() != two.size())
                return false;

            Set<String> setOne = one.keySet();
            Object valueOne;
            Object valueTwo;

            for (String key : setOne) {
                valueOne = one.get(key);
                valueTwo = two.get(key);
                if (valueOne instanceof Bundle && valueTwo instanceof Bundle &&
                        !equalBundles((Bundle) valueOne, (Bundle) valueTwo)) {
                    return false;
                } else if (valueOne == null) {
                    if (valueTwo != null || !two.containsKey(key))
                        return false;
                } else if (!valueOne.equals(valueTwo))
                    return false;
            }

            return true;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(this.tabType == null ? -1 : this.tabType.ordinal());
            dest.writeLong(this.id);
            dest.writeBundle(this.bundle);
            dest.writeString(this.title);
            dest.writeString(this.subtitle);
        }

        protected TabItem(Parcel in) {
            int tmpTabType = in.readInt();
            this.tabType = tmpTabType == -1 ? null : TabType.values()[tmpTabType];
            this.id = in.readLong();
            this.bundle = in.readBundle(getClass().getClassLoader());
            this.title = in.readString();
            this.subtitle = in.readString();
        }

        public static final Parcelable.Creator<TabItem> CREATOR = new Parcelable.Creator<TabItem>() {
            @Override
            public TabItem createFromParcel(Parcel source) {
                return new TabItem(source);
            }

            @Override
            public TabItem[] newArray(int size) {
                return new TabItem[size];
            }
        };
    }
}
