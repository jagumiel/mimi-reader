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

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.emogoth.android.phone.mimi.BuildConfig;
import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.util.MimiUtil;

import static android.app.Activity.RESULT_OK;

public class GalleryPrefsFragment extends PreferenceFragmentCompat {
    public static final String LOG_TAG = GalleryPrefsFragment.class.getSimpleName();
    private static final String DIR_CHOOSER_TAG = "directory_chooser_tag";
    public static final int REQUEST_CODE_DIR_CHOOSER = 41;

    private Preference saveLocation;

    public GalleryPrefsFragment() {
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.gallery_prefs);
        setupPrefs();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        saveLocation = null;
    }

    private void setupPrefs() {
        saveLocation = findPreference(getString(R.string.image_file_location_pref));
        DocumentFile saveDir = MimiUtil.getSaveDir();
        if (saveDir != null) {
            saveLocation.setSummary(saveDir.getName());
        }
        saveLocation.setOnPreferenceClickListener(preference -> {
            showDirChooser();
            return true;
        });
    }

    private void showDirChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_DIR_CHOOSER);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_DIR_CHOOSER) {
//            final int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION & Intent.FLAG_GRANT_WRITE_URI_PERMISSION & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
            final ContentResolver resolver = getActivity().getContentResolver();
            Uri uriTree = data.getData();
            if (uriTree != null) {
                int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                try {
                    resolver.releasePersistableUriPermission(MimiUtil.getSaveDir().getUri(), flags);
                } catch (Exception e) {
                    if (BuildConfig.DEBUG) {
                        Log.e(LOG_TAG, "Error releasing previous persistable Uri permissions", e);
                    }
                }
                resolver.takePersistableUriPermission(uriTree, flags);
                MimiUtil.setSaveDir(getActivity(), uriTree.toString());
                DocumentFile selectedDirectory = DocumentFile.fromTreeUri(getActivity(), uriTree);
                if (selectedDirectory != null) {
                    saveLocation.setSummary(selectedDirectory.getName());
                }
            }
        }
    }
}
