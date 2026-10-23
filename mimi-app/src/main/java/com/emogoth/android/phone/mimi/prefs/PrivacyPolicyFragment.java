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

package com.emogoth.android.phone.mimi.prefs;

import androidx.fragment.app.FragmentManager;
import com.emogoth.android.phone.mimi.R;

/** Based on Android Licenses Page by Adam Speakman (24 September 2013). */
public class PrivacyPolicyFragment extends LocalDocumentFragment {
    public static PrivacyPolicyFragment newInstance() {
        return new PrivacyPolicyFragment();
    }

    public static void displayLicensesFragment(FragmentManager manager) {
        showDocument(manager, newInstance(), "privacyDialogFragment");
    }

    @Override protected int documentResource() {
        return R.raw.privacypolicy;
    }

    @Override protected String documentTitle() {
        return getString(R.string.more_info_privacy);
    }
}
