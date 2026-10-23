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

import android.os.Bundle;

import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.emogoth.android.phone.mimi.BuildConfig;
import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.util.BrowserNavigator;


public class MoreInfoPrefsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.more_info_prefs);
        setupPrefs();
    }

    private void setupPrefs() {
        final Preference changelogPreference = findPreference(getString(R.string.changelog_pref));
        if (changelogPreference != null) {
            changelogPreference.setOnPreferenceClickListener(preference -> {
                // Create and show the dialog.
                LicensesFragment.displayLicensesFragment(getParentFragmentManager(), R.raw.changelog, "ChangeLog");
                return true;
            });
        }

        bindProjectLink(R.string.website_pref, R.string.project_repository_url);
        bindProjectLink(R.string.project_license_pref, R.string.project_license_url);
        bindProjectLink(R.string.credit_eli_pref, R.string.credit_eli_url);
        bindProjectLink(R.string.credit_original_account_pref, R.string.credit_original_account_url);
        bindProjectLink(R.string.credit_blatzar_pref, R.string.credit_blatzar_url);
        bindProjectLink(R.string.credit_maintainer_pref, R.string.credit_maintainer_url);
        bindProjectLink(R.string.credit_provenance_pref, R.string.credit_provenance_url);

        final Preference privacy = findPreference(getString(R.string.privacy_pref));
        privacy.setOnPreferenceClickListener(preference -> {
            // Create & show a licenses fragment just as you would any other DialogFragment.
            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            Fragment prev = getParentFragmentManager().findFragmentByTag("privacyDialogFragment");
            if (prev != null) {
                ft.remove(prev);
            }
            ft.addToBackStack(null);

            // Create and show the dialog.
            DialogFragment newFragment = PrivacyPolicyFragment.newInstance();
            newFragment.show(ft, "privacyDialogFragment");
            return true;
        });

        final Preference license = findPreference(getString(R.string.licenses_pref));
        license.setOnPreferenceClickListener(preference -> {
            // Create & show a licenses fragment just as you would any other DialogFragment.
            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            Fragment prev = getParentFragmentManager().findFragmentByTag("licensesDialogFragment");
            if (prev != null) {
                ft.remove(prev);
            }
            ft.addToBackStack(null);

            // Create and show the dialog.
            DialogFragment newFragment = LicensesFragment.newInstance(R.raw.licenses);
            newFragment.show(ft, "licensesDialogFragment");
            return true;
        });

        final Preference version = findPreference(getString(R.string.version_pref));
        version.setSummary(BuildConfig.VERSION_NAME);
    }

    private void bindProjectLink(@StringRes int key, @StringRes int url) {
        final Preference link = findPreference(getString(key));
        if (link != null) {
            link.setOnPreferenceClickListener(preference -> {
                // Explicit navigation only: no email, diagnostics, or automatic browser requests.
                BrowserNavigator.open(preference.getContext(), getString(url), false, null);
                return true;
            });
        }
    }
}
