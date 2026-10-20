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

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.emogoth.android.phone.mimi.BuildConfig;
import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.util.MimiUtil;
;

import java.io.File;
import java.io.IOException;


public class MoreInfoPrefsFragment extends PreferenceFragmentCompat {

    public static final String LOG_TAG = MoreInfoPrefsFragment.class.getSimpleName();

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

        final Preference website = findPreference(getString(R.string.website_pref));
        website.setOnPreferenceClickListener(preference -> {

            try {
                final String url = "http://mimireader.com";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
            } catch (final ActivityNotFoundException e) {
                Log.e(LOG_TAG, "Could not find browser to open mimireader.com", e);

                if (getActivity() != null) {
                    Toast.makeText(getActivity(), R.string.error_opening_url, Toast.LENGTH_SHORT).show();
                }
            }

            return true;
        });

        final Preference rate = findPreference(getString(R.string.rate_pref));
        rate.setOnPreferenceClickListener(preference -> {
            MimiUtil.getInstance().openMarketLink(getActivity());
            return true;
        });

        final Preference feedback = findPreference(getString(R.string.feedback_pref));
        feedback.setOnPreferenceClickListener(preference -> {
//            final Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
//                    "mailto", "eli@mimireader.com", null));
//            intent.putExtra(Intent.EXTRA_SUBJECT, "Mimi Feedback");
//            Intent mailer = Intent.createChooser(intent, null);
//            startActivity(mailer);
            sendLogcatMail(false, true);
            return true;
        });


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

    void sendLogcatMail(boolean attachLogcat, boolean attachDeviceInfo) {

        boolean log = attachLogcat;
        File outputFile = null;
        if (log) {
            // save logcat in file
            outputFile = getActivity().getFileStreamPath("logcat.txt");

            try {
                Runtime.getRuntime().exec(
                        "logcat -f " + outputFile.getAbsolutePath());
            } catch (IOException e) {
                Log.e("Feedback", "Error getting logcat for feedback email", e);
                log = false;
            }
        }

        StringBuilder sb = new StringBuilder("\n\n\n-----\n");
        if (attachDeviceInfo) {
            sb.append("Mimi ")
                    .append(BuildConfig.VERSION_NAME).append(" (")
                    .append(BuildConfig.VERSION_CODE).append('-')
                    .append(BuildConfig.BUILD_TYPE).append(")\n");
            sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
            sb.append("Android: ").append(Build.VERSION.CODENAME).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        }

        //send file using email
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        // Set type to "email"
        emailIntent.setType("vnd.android.cursor.dir/email");
        String[] to = {"eli@mimireader.com"};
        emailIntent.putExtra(Intent.EXTRA_EMAIL, to);
        if (log) {
            // the attachment
            emailIntent.putExtra(Intent.EXTRA_STREAM, outputFile.getAbsolutePath());
        }

        if (attachDeviceInfo) {
            emailIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        }
        // the mail subject
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Mimi Feedback");
        startActivity(Intent.createChooser(emailIntent, "Send Feedback"));
    }
}
