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

package com.emogoth.android.phone.mimi.util;


import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.util.Pair;

import com.emogoth.android.phone.mimi.BuildConfig;
import com.emogoth.android.phone.mimi.app.MimiApplication;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class PostUtil {

    private static final String LOG_TAG = PostUtil.class.getSimpleName();
    private static PostUtil instance = new PostUtil();


    private PostUtil() {
    }

    public static PostUtil getInstance() {
        return instance;
    }

    /** Copies content represented by a Uri into the app cache when a File is required. */
    public static Pair<String, Boolean> getPath(final Context context, final Uri uri) {
        return new Pair<>(getImagePathFromInputStreamUri(uri), true);
    }

    public static String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = MimiApplication.getInstance().getContentResolver()
                    .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameColumn >= 0) {
                        result = cursor.getString(nameColumn);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result == null) {
                return null;
            }
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }


    public static String getImagePathFromInputStreamUri(Uri uri) {
        InputStream inputStream = null;
        String filePath = null;

        if (uri.getAuthority() != null) {
            try {
                inputStream = MimiApplication.getInstance().getContentResolver().openInputStream(uri); // context needed

                String fileName = getFileName(uri);
                File photoFile = createTemporalFileFrom(inputStream, fileName);

                filePath = photoFile.getPath();

            } catch (Exception e) {
                if (BuildConfig.DEBUG) {
                    Log.e(LOG_TAG, "Error getting input stream from URI", e);
                }
                // log
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        }

        return filePath;
    }

    private static File createTemporalFileFrom(InputStream inputStream, String filename) throws IOException {
        File targetFile = null;

        if (inputStream != null) {
            int read;
            byte[] buffer = new byte[8 * 1024];

            targetFile = createTemporalFile(filename);
            try (OutputStream outputStream = new FileOutputStream(targetFile)) {
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
            }
        }

        return targetFile;
    }

    private static File createTemporalFile(String filename) {
        final String name;
        if (TextUtils.isEmpty(filename)) {
            name = "temp_file.jpg";
        } else {
            name = new File(filename).getName();
        }

        return new File(MimiUtil.getInstance().getCacheDir(), name); // context needed
    }
}
