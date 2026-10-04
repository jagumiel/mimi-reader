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

import android.Manifest;
import android.os.Build;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.db.BoardTableConnection;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import com.emogoth.android.phone.mimi.util.NotificationUtils;
import com.google.android.material.snackbar.Snackbar;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


public class HistoryPrefsFragment extends PreferenceFragmentCompat {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 2101;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.history_prefs);
        setupPrefs();
    }

    private void setupPrefs() {
        final ListPreference refreshInterval = (ListPreference) findPreference(getString(R.string.app_auto_refresh_time));
        refreshInterval.setSummary(refreshInterval.getEntry());
        refreshInterval.setOnPreferenceChangeListener((preference, newValue) -> {
            int index = 0;
            for (int i = 0; i < refreshInterval.getEntryValues().length; i++) {
                if (refreshInterval.getEntryValues()[i].equals(newValue)) {
                    index = i;
                }
            }

            refreshInterval.setSummary(refreshInterval.getEntries()[index]);

            return true;
        });

        final ListPreference backgroundRefreshInterval = (ListPreference) findPreference(getString(R.string.background_auto_refresh_time));
        backgroundRefreshInterval.setSummary(backgroundRefreshInterval.getEntry());
        backgroundRefreshInterval.setOnPreferenceChangeListener((preference, newValue) -> {
            int index = 0;
            for (int i = 0; i < backgroundRefreshInterval.getEntryValues().length; i++) {
                if (backgroundRefreshInterval.getEntryValues()[i].equals(newValue)) {
                    index = i;
                }
            }

            backgroundRefreshInterval.setSummary(backgroundRefreshInterval.getEntries()[index]);

            return true;
        });

        final ListPreference notificationPref = (ListPreference) findPreference(getString(R.string.background_notification_pref));
        notificationPref.setSummary(notificationPref.getEntry());
        notificationPref.setOnPreferenceChangeListener((preference, o) -> {
            int index = 0;
            for (int i = 0; i < notificationPref.getEntryValues().length; i++) {
                if (notificationPref.getEntryValues()[i].equals(o)) {
                    index = i;
                }
            }

            notificationPref.setSummary(notificationPref.getEntries()[index]);

            if (!"1".equals(o.toString())
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && !NotificationUtils.canPostNotifications(requireContext())) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST);
            }

            return true;
        });

        final SwitchPreferenceCompat saveHistory = findPreference(getString(R.string.save_history_pref));
        saveHistory.setOnPreferenceChangeListener((preference, newValue) -> {
            if (!((Boolean) newValue)) {
                Disposable pruneSubscriber = MimiUtil.pruneHistory(0)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aBoolean -> Snackbar.make(getView(), R.string.clearing_history, Snackbar.LENGTH_SHORT).show(), throwable -> Snackbar.make(getView(), R.string.error_while_clearing_history, Snackbar.LENGTH_SHORT).show());
                BoardTableConnection.resetStats()
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .onErrorReturn(throwable -> false)
                        .subscribe();
            }
            return true;
        });

        final ListPreference historyPruneTime = (ListPreference) findPreference(getString(R.string.history_prune_time_pref));
        historyPruneTime.setSummary(getString(R.string.history_prune_time_summary, historyPruneTime.getEntry()));
        historyPruneTime.setOnPreferenceChangeListener((preference, newValue) -> {
            int index = 0;
            for (int i = 0; i < historyPruneTime.getEntryValues().length; i++) {
                if (historyPruneTime.getEntryValues()[i].equals(newValue)) {
                    index = i;
                }
            }

            preference.setSummary(getString(R.string.history_prune_time_summary, historyPruneTime.getEntries()[index]));

            return true;
        });

        final Preference clearHistory = findPreference(getString(R.string.clear_history_pref));
        clearHistory.setOnPreferenceClickListener(preference -> {
            Disposable pruneSubscriber = MimiUtil.pruneHistory(0)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(aBoolean -> Snackbar.make(getView(), R.string.clearing_history, Snackbar.LENGTH_SHORT).show(), throwable -> Snackbar.make(getView(), R.string.error_while_clearing_history, Snackbar.LENGTH_SHORT).show());
            BoardTableConnection.resetStats()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .onErrorReturn(throwable -> false)
                    .subscribe();
            return true;
        });
    }
}
