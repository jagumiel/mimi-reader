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
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.app.MimiApplication;
import com.emogoth.android.phone.mimi.fragment.BoardItemListFragment;
import com.emogoth.android.phone.mimi.fragment.HistoryFragment;
import com.emogoth.android.phone.mimi.fragment.PostItemsListFragment;
import com.emogoth.android.phone.mimi.fragment.ThreadDetailFragment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class TabPagerAdapter extends FragmentStatePagerAdapter {
    private static final String LOG_TAG = TabPagerAdapter.class.getSimpleName();
    private final List<TabItem> tabItems = new ArrayList<>();
    private final Map<Long, Fragment> activeFragments = new HashMap<>();

    public TabPagerAdapter(FragmentManager fm) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);

        setup();

        final String title = MimiApplication.getInstance().getApplicationContext().getString(R.string.boards);
        tabItems.add(new TabItem(TabType.BOARDS, null, BoardItemListFragment.TAB_ID, title, null));
    }

    public TabPagerAdapter(FragmentManager fm, List<TabItem> items) {
        super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);

        if (items != null) {
            tabItems.addAll(items);
        }
    }

    private void setup() {

    }

    public TabItem getTab(int position) {
        if (position >= 0 && position < tabItems.size()) {
            return tabItems.get(position);
        }

        return null;
    }

    @Override
    public Fragment getItem(int position) {
        final TabItem item = tabItems.get(position);
        switch (item.getTabType()) {
            case BOARDS:
                return new BoardItemListFragment();
            case POSTS:
                final PostItemsListFragment postItemsListFragment = new PostItemsListFragment();
                postItemsListFragment.setArguments(item.getBundle());
                return postItemsListFragment;
            case THREAD:
                final ThreadDetailFragment threadDetailFragment = new ThreadDetailFragment();
                threadDetailFragment.setArguments(item.getBundle());
                return threadDetailFragment;
            case HISTORY:
                final HistoryFragment historyFragment = new HistoryFragment();
                historyFragment.setArguments(item.getBundle());
                return historyFragment;
        }

        return new Fragment();
    }

    private long getStableItemId(int position) {
        return tabItems.get(position).getStableId();
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        final Object item = super.instantiateItem(container, position);
        if (item instanceof Fragment) {
            activeFragments.put(getStableItemId(position), (Fragment) item);
        }
        return item;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        activeFragments.values().remove(object);
        super.destroyItem(container, position, object);
    }

    @Override
    public int getCount() {
        return tabItems.size();
    }

    @Override
    public int getItemPosition(Object object) {
        Long stableId = null;
        for (Map.Entry<Long, Fragment> entry : activeFragments.entrySet()) {
            if (entry.getValue() == object) {
                stableId = entry.getKey();
                break;
            }
        }

        if (stableId == null) {
            return POSITION_NONE;
        }

        for (int position = 0; position < tabItems.size(); position++) {
            if (tabItems.get(position).getStableId() == stableId) {
                return position;
            }
        }

        return POSITION_NONE;
    }

    public Fragment getActiveFragment(int position) {
        if (position < 0 || position >= tabItems.size()) {
            return null;
        }
        return activeFragments.get(getStableItemId(position));
    }

    public int getIndex(long threadId) {
        boolean done = false;
        int i = 0;
        int val = -1;
        while (!done) {
            if (tabItems.get(i).getId() == threadId) {
                val = i;
                done = true;
            } else {
                i++;
            }

            if (i >= tabItems.size()) {
                done = true;
            }
        }

        return val;
    }

    public int addItem(TabItem item) {
        try {
            final int index = tabItems.indexOf(item);
            if (index >= 0 && tabItems.get(index).getBundle() != null) {
                return index;
            }

            tabItems.add(item);
            notifyDataSetChanged();

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
        tabItems.remove(index);
        tabItems.add(index, item);
        notifyDataSetChanged();
    }

    public void removeItemAtIndex(int index) {
        tabItems.remove(index);
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
        notifyDataSetChanged();
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
