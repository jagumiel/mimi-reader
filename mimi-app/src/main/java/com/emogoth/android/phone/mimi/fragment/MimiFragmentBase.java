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

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.emogoth.android.phone.mimi.activity.MimiActivity;
import com.emogoth.android.phone.mimi.util.Extras;

public abstract class MimiFragmentBase extends Fragment {
    private static final String LOG_TAG = MimiFragmentBase.class.getSimpleName();
    private static final boolean LOG_DEBUG = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            Bundle extras = getArguments();

            boolean hasOptionsMenu = extras.getBoolean(Extras.EXTRAS_OPTIONS_MENU_ENABLED, true);
            setHasOptionsMenu(hasOptionsMenu);
        } else {
            setHasOptionsMenu(true);
        }

    }

    public boolean onBackPressed() {
        return false;
    }

    public boolean canHandleBack() {
        return false;
    }

    protected void notifyBackNavigationStateChanged() {
        if (getActivity() instanceof MimiActivity) {
            ((MimiActivity) getActivity()).refreshBackNavigationState();
        }
    }

    public abstract String getTitle();

    public abstract String getSubtitle();

    public abstract String getPageName(); // for logging

    public void initMenu() {
        // empty
    }

    public int getMenuRes() {
        return 0;
    }

    public boolean showFab() {
        return true;
    }
}
