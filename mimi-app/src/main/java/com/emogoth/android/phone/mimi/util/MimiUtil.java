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

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.emogoth.android.phone.mimi.BuildConfig;
import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.activity.StartupActivity;
import com.emogoth.android.phone.mimi.app.MimiApplication;
import com.emogoth.android.phone.mimi.db.ArchivedPostTableConnection;
import com.emogoth.android.phone.mimi.db.DatabaseUtils;
import com.emogoth.android.phone.mimi.db.HistoryTableConnection;
import com.emogoth.android.phone.mimi.db.PostTableConnection;
import com.emogoth.android.phone.mimi.db.models.History;
import com.emogoth.android.phone.mimi.viewmodel.GalleryItem;
import com.franmontiel.persistentcookiejar.persistence.CookiePersistor;
import com.google.android.material.appbar.AppBarLayout;
;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonWriter;
import com.mimireader.chanlib.models.ChanPost;
import com.mimireader.chanlib.models.ChanThread;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;
import okhttp3.Cookie;

/**
 * General purpose util class to handle the generic methods used throughout the app
 */
public class MimiUtil {
    private static final String LOG_TAG = MimiUtil.class.getSimpleName();
    private static MimiUtil ourInstance = new MimiUtil();

    private static final int THREAD_POOL_CORE_SIZE = 2;
    private static final int THREAD_POOL_MAX_SIZE = 10;
    private static final int THREAD_POOL_KEEP_ALIVE = 10;

    private static final long AD_TIMEOUT = 30 * 60 * 1000;

    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    public static final int THEME_BLACK = 2;

    public static final int THEME_COLOR_DEFAULT = 0;
    public static final int THEME_COLOR_RED = 1;
    public static final int THEME_COLOR_GREEN = 2;
    public static final int THEME_COLOR_BLUE = 3;
    public static final int THEME_COLOR_INDIGO = 4;
    public static final int THEME_COLOR_PINK = 5;
    public static final int THEME_COLOR_PURPLE = 6;
    public static final int THEME_COLOR_ORANGE = 7;
    public static final int THEME_COLOR_DEEP_ORANGE = 8;
    public static final int THEME_COLOR_BROWN = 9;
    public static final int THEME_COLOR_BLUE_GREY = 10;
    public static final int THEME_COLOR_LIGHT_GREY = 11;
    public static final int THEME_COLOR_GREY = 12;
    public static final int THEME_COLOR_DARK_GREY = 13;
    public static final int THEME_COLOR_BLACK = 14;

    private int toolbarColor;
    private int theme;
    private int themeResource;

    private int nextLoaderId;
    private HashMap<Object, Integer> loaderIdMap;

    private Context context;

    private File cacheDir;
    //    private String boardOrderPrefString;
    private ThreadPoolExecutor executorPool;
    private boolean ready = false;

    private int quoteColor;
    private int replyColor;
    private int highlightColor;
    private int linkColor;

    private static Integer[][] themeArray = new Integer[3][15];

    static {
        themeArray[THEME_LIGHT][THEME_COLOR_DEFAULT] = R.style.Theme_Mimi_Light_Toolbar_Default;
        themeArray[THEME_LIGHT][THEME_COLOR_RED] = R.style.Theme_Mimi_Light_Toolbar_Red;
        themeArray[THEME_LIGHT][THEME_COLOR_GREEN] = R.style.Theme_Mimi_Light_Toolbar_Green;
        themeArray[THEME_LIGHT][THEME_COLOR_BLUE] = R.style.Theme_Mimi_Light_Toolbar_Blue;
        themeArray[THEME_LIGHT][THEME_COLOR_INDIGO] = R.style.Theme_Mimi_Light_Toolbar_Indigo;
        themeArray[THEME_LIGHT][THEME_COLOR_PINK] = R.style.Theme_Mimi_Light_Toolbar_Pink;
        themeArray[THEME_LIGHT][THEME_COLOR_PURPLE] = R.style.Theme_Mimi_Light_Toolbar_Purple;
        themeArray[THEME_LIGHT][THEME_COLOR_ORANGE] = R.style.Theme_Mimi_Light_Toolbar_Orange;
        themeArray[THEME_LIGHT][THEME_COLOR_DEEP_ORANGE] = R.style.Theme_Mimi_Light_Toolbar_DeepOrange;
        themeArray[THEME_LIGHT][THEME_COLOR_BROWN] = R.style.Theme_Mimi_Light_Toolbar_Brown;
        themeArray[THEME_LIGHT][THEME_COLOR_BLUE_GREY] = R.style.Theme_Mimi_Light_Toolbar_BlueGrey;
        themeArray[THEME_LIGHT][THEME_COLOR_LIGHT_GREY] = R.style.Theme_Mimi_Light_Toolbar_LightGrey;
        themeArray[THEME_LIGHT][THEME_COLOR_GREY] = R.style.Theme_Mimi_Light_Toolbar_Grey;
        themeArray[THEME_LIGHT][THEME_COLOR_DARK_GREY] = R.style.Theme_Mimi_Light_Toolbar_DarkGrey;
        themeArray[THEME_LIGHT][THEME_COLOR_BLACK] = R.style.Theme_Mimi_Light_Toolbar_Black;

        themeArray[THEME_DARK][THEME_COLOR_DEFAULT] = R.style.Theme_Mimi_Dark_Toolbar_Default;
        themeArray[THEME_DARK][THEME_COLOR_RED] = R.style.Theme_Mimi_Dark_Toolbar_Red;
        themeArray[THEME_DARK][THEME_COLOR_GREEN] = R.style.Theme_Mimi_Dark_Toolbar_Green;
        themeArray[THEME_DARK][THEME_COLOR_BLUE] = R.style.Theme_Mimi_Dark_Toolbar_Blue;
        themeArray[THEME_DARK][THEME_COLOR_INDIGO] = R.style.Theme_Mimi_Dark_Toolbar_Indigo;
        themeArray[THEME_DARK][THEME_COLOR_PINK] = R.style.Theme_Mimi_Dark_Toolbar_Pink;
        themeArray[THEME_DARK][THEME_COLOR_PURPLE] = R.style.Theme_Mimi_Dark_Toolbar_Purple;
        themeArray[THEME_DARK][THEME_COLOR_ORANGE] = R.style.Theme_Mimi_Dark_Toolbar_Orange;
        themeArray[THEME_DARK][THEME_COLOR_DEEP_ORANGE] = R.style.Theme_Mimi_Dark_Toolbar_DeepOrange;
        themeArray[THEME_DARK][THEME_COLOR_BROWN] = R.style.Theme_Mimi_Dark_Toolbar_Brown;
        themeArray[THEME_DARK][THEME_COLOR_BLUE_GREY] = R.style.Theme_Mimi_Dark_Toolbar_BlueGrey;
        themeArray[THEME_DARK][THEME_COLOR_LIGHT_GREY] = R.style.Theme_Mimi_Dark_Toolbar_LightGrey;
        themeArray[THEME_DARK][THEME_COLOR_GREY] = R.style.Theme_Mimi_Dark_Toolbar_Grey;
        themeArray[THEME_DARK][THEME_COLOR_DARK_GREY] = R.style.Theme_Mimi_Dark_Toolbar_DarkGrey;
        themeArray[THEME_DARK][THEME_COLOR_BLACK] = R.style.Theme_Mimi_Dark_Toolbar_Black;

        themeArray[THEME_BLACK][THEME_COLOR_DEFAULT] = R.style.Theme_Mimi_Black_Toolbar_Default;
        themeArray[THEME_BLACK][THEME_COLOR_RED] = R.style.Theme_Mimi_Black_Toolbar_Red;
        themeArray[THEME_BLACK][THEME_COLOR_GREEN] = R.style.Theme_Mimi_Black_Toolbar_Green;
        themeArray[THEME_BLACK][THEME_COLOR_BLUE] = R.style.Theme_Mimi_Black_Toolbar_Blue;
        themeArray[THEME_BLACK][THEME_COLOR_INDIGO] = R.style.Theme_Mimi_Black_Toolbar_Indigo;
        themeArray[THEME_BLACK][THEME_COLOR_PINK] = R.style.Theme_Mimi_Black_Toolbar_Pink;
        themeArray[THEME_BLACK][THEME_COLOR_PURPLE] = R.style.Theme_Mimi_Black_Toolbar_Purple;
        themeArray[THEME_BLACK][THEME_COLOR_ORANGE] = R.style.Theme_Mimi_Black_Toolbar_Orange;
        themeArray[THEME_BLACK][THEME_COLOR_DEEP_ORANGE] = R.style.Theme_Mimi_Black_Toolbar_DeepOrange;
        themeArray[THEME_BLACK][THEME_COLOR_BROWN] = R.style.Theme_Mimi_Black_Toolbar_Brown;
        themeArray[THEME_BLACK][THEME_COLOR_BLUE_GREY] = R.style.Theme_Mimi_Black_Toolbar_BlueGrey;
        themeArray[THEME_BLACK][THEME_COLOR_LIGHT_GREY] = R.style.Theme_Mimi_Black_Toolbar_LightGrey;
        themeArray[THEME_BLACK][THEME_COLOR_GREY] = R.style.Theme_Mimi_Black_Toolbar_Grey;
        themeArray[THEME_BLACK][THEME_COLOR_DARK_GREY] = R.style.Theme_Mimi_Black_Toolbar_DarkGrey;
        themeArray[THEME_BLACK][THEME_COLOR_BLACK] = R.style.Theme_Mimi_Black_Toolbar_Black;
    }

    private static Map<String, Integer> fontSizeMap = new HashMap<>();

    static {
        fontSizeMap.put("0", R.style.FontStyle_Small);
        fontSizeMap.put("1", R.style.FontStyle_Medium);
        fontSizeMap.put("2", R.style.FontStyle_Large);
    }

    private static final Map<String, String> keywordMap = new HashMap<>();

    static {
        keywordMap.put("random", "Anime, Celebrity, Politics");
    }

    public static MimiUtil getInstance() {
        return ourInstance;
    }

    private MimiUtil() {
    }

    public void init(final Context context) {

        if (ready) {
            return;
        }

        this.context = context;

        final SharedPreferences sh = PreferenceManager.getDefaultSharedPreferences(context);

        final String themeShadePrefString = context.getString(R.string.theme_pref);
        final String themeColorPrefString = context.getString(R.string.theme_color_pref);
        int themeId = Integer.valueOf(sh.getString(themeShadePrefString, "0"));
        int colorId = Integer.valueOf(sh.getString(themeColorPrefString, "0"));

        setCurrentTheme(themeId, colorId);
        Log.i(LOG_TAG, "Set theme: id=" + themeId + ", resource=" + getThemeResourceId());

//        if (boardOrderPrefString == null) {
//            boardOrderPrefString = context.getString(R.string.board_order_pref);
//        }

        cacheDir = context.getCacheDir();

        nextLoaderId = 0;
        loaderIdMap = new HashMap<>();

        executorPool = new ThreadPoolExecutor(THREAD_POOL_CORE_SIZE,
                THREAD_POOL_MAX_SIZE,
                THREAD_POOL_KEEP_ALIVE,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(THREAD_POOL_CORE_SIZE));

        quoteColor = sh.getInt(context.getString(R.string.quote_color_pref), context.getResources().getColor(R.color.quote));
        replyColor = sh.getInt(context.getString(R.string.reply_color_pref), context.getResources().getColor(R.color.reply));
        highlightColor = sh.getInt(context.getString(R.string.highlight_color_pref), context.getResources().getColor(R.color.reply_highlight));
        linkColor = sh.getInt(context.getString(R.string.link_color_pref), context.getResources().getColor(R.color.link));

        ready = true;
    }

    public int getQuoteColor() {
        return quoteColor;
    }

    public int getReplyColor() {
        return replyColor;
    }

    public int getHighlightColor() {
        return highlightColor;
    }

    public int getLinkColor() {
        return linkColor;
    }

    public void setQuoteColor(int quoteColor) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putInt(context.getString(R.string.quote_color_pref), quoteColor)
                .apply();
        this.quoteColor = quoteColor;
    }

    public void setReplyColor(int replyColor) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putInt(context.getString(R.string.reply_color_pref), replyColor)
                .apply();
        this.replyColor = replyColor;
    }

    public void setHighlightColor(int highlightColor) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putInt(context.getString(R.string.highlight_color_pref), highlightColor)
                .apply();
        this.highlightColor = highlightColor;
    }

    public void setLinkColor(int linkColor) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putInt(context.getString(R.string.link_color_pref), linkColor)
                .apply();
        this.linkColor = linkColor;
    }

    public static LayoutType getLayoutType(final Context context) {
        final String layoutName = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.start_activity_pref), StartupActivity.getDefaultStartupActivity());

        try {
            LayoutType type = LayoutType.valueOf(layoutName.toUpperCase());
            return type;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error getting layout type", e);
        }

        return StartupActivity.DEFAULT_LAYOUT_TYPE;
    }

    public static boolean historyEnabled(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.save_history_pref), true);
    }

    public int getLoaderId(final Class clazz) {
        Integer loaderId = loaderIdMap.get(clazz);
        if (loaderId == null) {
            loaderId = nextLoaderId;
            nextLoaderId++;
            loaderIdMap.put(clazz, loaderId);
        }

        return loaderId;
    }

    @Nullable
    public static DocumentFile getSaveDir() {
        final Context context = MimiApplication.getInstance().getApplicationContext();
        final String dir = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.image_file_location_pref), null);
        return directoryToDocumentFile(context, dir);
    }

    private static DocumentFile directoryToDocumentFile(final Context context, final String dir) {
        if (!TextUtils.isEmpty(dir)) {
            try {
                if (dir.startsWith(Utils.SCHEME_CONTENT)) {
                    return DocumentFile.fromTreeUri(context, Uri.parse(dir));
                }
                Log.w(LOG_TAG, "Ignoring legacy filesystem save path; a SAF directory must be selected");
                return null;
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error creating DocumentFile from " + dir, e);
                return null;
            }
        }
        return null;
    }

    public static void setSaveDir(final Context context, final String path) {
        final SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString(context.getString(R.string.image_file_location_pref), path).apply();
    }

    public static int getFontStyle(final Context context) {
        final String style = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.font_style_pref), "0");
        return fontSizeMap.get(style);
    }

    public int getTheme() {
        return this.theme;
    }

    public int getThemeResourceId() {
        return themeResource;
    }

    public void setCurrentTheme(int theme, int colorId) {
        this.theme = theme;
        this.toolbarColor = colorId;
        themeResource = themeArray[theme][colorId];
    }

    public static Single<Boolean> removeHistory(final boolean watched) {
        return HistoryTableConnection.fetchHistory(watched)
                .toFlowable()
                .flatMapIterable((Function<List<History>, Iterable<History>>) histories -> histories)
                .flatMap((Function<History, Flowable<History>>) history -> Flowable.just(history))
                .doOnNext(history -> PostTableConnection.removeThread(history.getThreadId()))
                .doOnNext(history -> ArchivedPostTableConnection.removeThread(history.getBoardName(), history.getThreadId()))
                .toList()
                .flatMap((Function<List<History>, Single<Boolean>>) booleans -> HistoryTableConnection.removeAllHistory(watched))
                .compose(DatabaseUtils.applySingleSchedulers());
    }

    public static Single<Boolean> pruneHistory(final int days) {
        return HistoryTableConnection.getHistoryToPrune(days)
                .toFlowable()
                .flatMapIterable((Function<List<History>, Iterable<History>>) histories -> histories)
                .flatMap((Function<History, Flowable<History>>) Flowable::just)
                .doOnNext(history -> PostTableConnection.removeThread(history.getThreadId()))
                .doOnNext(history -> ArchivedPostTableConnection.removeThread(history.getBoardName(), history.getThreadId()))
                .toList()
                .flatMap((Function<List<History>, Single<Boolean>>) booleans -> HistoryTableConnection.pruneHistory(days));
    }

    public static Single<Boolean> removeStalePosts() {
        return Single.zip(HistoryTableConnection.fetchHistory(), PostTableConnection.fetchThreadIds(), (BiFunction<List<History>, List<Long>, List<Long>>) (histories, longs) -> {
            if (longs.size() == 0) {
                return Collections.emptyList();
            }

            List<Long> staleIds = new ArrayList<>(longs.size());
            for (Long id : longs) {
                boolean found = false;
                for (History history : histories) {
                    if (history.getThreadId() == id) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    staleIds.add(id);
                }
            }
            return staleIds;
        })
                .flatMap((Function<List<Long>, SingleSource<Boolean>>) ids -> {
                    if (ids.size() == 0) {
                        return Single.just(true);
                    }

                    return PostTableConnection.removeThreads(ids);
                }).onErrorReturn(throwable -> {
                    Log.e(LOG_TAG, "Error removing stale posts", throwable);
                    return false;
                });

    }

    @Nullable
    public static Uri getFileProvider(File file) {
        try {
            final String authority = MimiApplication.getInstance().getPackageName() + ".fileprovider";
            return FileProvider.getUriForFile(MimiApplication.getInstance(), authority, file);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error getting file provider for " + file, e);
        }

        return null;
    }

    public File getCacheDir() {
        return cacheDir;
    }

    public static int getMaxCacheSize(final Context context) {
        final int memClass = ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
        return 1024 * 1024 * memClass / 8;
    }

    public static int getBoardOrder() {
        final Context context = MimiApplication.getInstance().getApplicationContext();
        try {
            return PreferenceManager.getDefaultSharedPreferences(context).getInt(context.getString(R.string.board_order_pref), 0);
        } catch (final ClassCastException e) {
            PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(context.getString(R.string.board_order_pref), 0).apply();
        }

        return 0;
    }

    public static void setBoardOrder(final Context context, final int order) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(context.getString(R.string.board_order_pref), order).apply();
    }

    public static int getBoardFilter() {
        final Context context = MimiApplication.getInstance().getApplicationContext();
        try {
            return BoardFilter.normalize(PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(context.getString(R.string.board_filter_pref), BoardFilter.ALL));
        } catch (final ClassCastException e) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putInt(context.getString(R.string.board_filter_pref), BoardFilter.ALL).apply();
        }

        return BoardFilter.ALL;
    }

    public static void setBoardFilter(final Context context, final int filter) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putInt(context.getString(R.string.board_filter_pref), BoardFilter.normalize(filter)).apply();
    }

    public static String https() {
        return "https://";
    }

    public static int getDeviceOrientation(Context context) {
        return context.getResources().getConfiguration().orientation;
    }

    public View createActionBarNotification(final LayoutInflater inflater, final ViewGroup container, final int count) {
        final View v = inflater.inflate(R.layout.badge_layout, container, false);

        if (v != null) {
            final TextView bookmarkCountButton = (TextView) v.findViewById(R.id.bookmark_count);
            if (count > 0) {
                bookmarkCountButton.setBackgroundResource(R.drawable.new_unread_count);
            } else {
                bookmarkCountButton.setBackgroundResource(R.drawable.no_unread_count);
            }
            bookmarkCountButton.setText(String.valueOf(count));

            bookmarkCountButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.i(LOG_TAG, "Clicked badge");
                }
            });

            return v;
        }

        return null;
    }

    public static boolean writeThreadToFile(final File saveFile, final ChanThread thread) {
        Gson gson = new Gson();
        try {
            TypeAdapter<ChanThread> adapter = gson.getAdapter(ChanThread.class);
            JsonWriter writer = new JsonWriter(new FileWriter(saveFile, true));
            adapter.write(writer, thread);
            writer.close();
            return true;
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "File not found: " + saveFile.getAbsolutePath(), e);
            return false;
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException caught while writing bookmark file", e);
            return false;
        }
    }

    public boolean isLoggedIn() {
        try {
            CookiePersistor cookieStore = HttpClientFactory.getInstance().getCookieStore();
            if (cookieStore == null) {
                return false;
            }

            for (final Cookie cookie : cookieStore.loadAll()) {
                if ("pass_enabled".equals(cookie.name()) && "1".equals(cookie.value())) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Could not validate logged in status", e);
        }

        return false;
    }

    public static boolean openLinksExternally(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.open_links_externally_pref), false);
    }

    public void logout() {
        CookiePersistor cookieStore = HttpClientFactory.getInstance().getCookieStore();
        Cookie loginCookie = null;
        for (final Cookie cookie : cookieStore.loadAll()) {
            if ("pass_enabled".equals(cookie.name()) && "1".equals(cookie.value())) {
                loginCookie = cookie;
                break;
            }
        }

        if (loginCookie != null) {
            HttpClientFactory.getInstance().getCookieStore().removeAll(Collections.singletonList(loginCookie));
        }
    }

    public static int arrayLocation(final long[] array, final long v) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == v) {
                return i;
            }
        }

        return -1;
    }

    public void openMarketLink(final Context context) {
        final String applicationName = context.getPackageName();
        if (applicationName != null) {
            if (applicationName.toLowerCase().contains("amazon")) {
                try {
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.amazon.com/EmoGoth-Mimi-Reader/dp/B00EGIPHC8/")));
                } catch (ActivityNotFoundException e) {
                    Log.e(LOG_TAG, "Could not open market link to Amazon", e);
                }
            } else {
                try {
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + applicationName)));
                } catch (ActivityNotFoundException e) {
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + applicationName)));
                }
            }
        }

        Log.i(LOG_TAG, "Using package: name=" + applicationName);
    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (width > reqWidth) {
            int remainder = width % reqWidth;
            inSampleSize = Math.round((float) width / (float) reqWidth);
            if (remainder >= 3 || inSampleSize == 0) {
                inSampleSize++;
            }
        }

        if (isSamsung()) {
            inSampleSize *= 2;
        }

        return inSampleSize;
    }

    public static boolean isSamsung() {
        String strManufacturer = android.os.Build.MANUFACTURER;
        if (strManufacturer != null && strManufacturer.contains("samsung")) {
            return false;
        }

        return false;
    }

    public static String removeExtention(String filePath) {
        // These first few lines the same as Justin's
        File f = new File(filePath);

        // if it's a directory, don't remove the extention
        if (f.isDirectory()) return filePath;

        String name = f.getName();

        // Now we know it's a file - don't need to do any special hidden
        // checking or contains() checking because of:
        final int lastPeriodPos = name.lastIndexOf('.');
        if (lastPeriodPos <= 0) {
            // No period after first character - return name as it was passed in
            return filePath;
        } else {
            // Remove the last period and everything after it
            File renamed = new File(f.getParent(), name.substring(0, lastPeriodPos));
            return renamed.getPath();
        }
    }

    public static boolean rememberThreadScrollPosition(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(R.string.remember_thread_position_pref), true);
    }

    public static String parseKeywordsFromBoardTitle(final String boardTitle) {
        if (boardTitle != null) {

            String mappedKeywords = keywordMap.get(boardTitle.toLowerCase());
            if (mappedKeywords != null) {
                return mappedKeywords;
            }

            String[] keywordArray = null;
            if (boardTitle.contains("&")) {
                keywordArray = boardTitle.split("&");
            } else if (boardTitle.contains("/")) {
                keywordArray = boardTitle.split("/");
            }

            if (keywordArray != null && keywordArray.length > 0) {
                StringBuilder keywords = new StringBuilder();

                for (int i = 0; i < keywordArray.length; i++) {
                    keywords.append(keywordArray[i]);
                    if (i + 1 < keywordArray.length) {
                        keywords.append(",");
                    }
                }

                return keywords.toString();
            }
        }

        return boardTitle;
    }

    public static int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    public static boolean isCrappySamsung() {
        if (Build.VERSION.SDK_INT <= 19 && "samsung".equals(Build.MANUFACTURER)) {
            return true;
        }

        return false;
    }

    public static void loadImageWithFallback(final Context context, @NonNull final ImageView into, String url, final String fallback, @DrawableRes final Integer placeholderRes, final RequestListener<Drawable> listener) {
        GlideRequest<Drawable> originalRequest = GlideApp.with(context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL);

        if (!TextUtils.isEmpty(fallback)) {
            GlideRequest<Drawable> stuff = originalRequest.listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(@Nullable GlideException e, Object o, Target<Drawable> target, boolean b) {
                    if (context != null) {
                        GlideRequest requestBuilder = GlideApp.with(context)
                                .load(fallback)
                                .diskCacheStrategy(DiskCacheStrategy.ALL);

                        if (listener != null) {
                            requestBuilder = requestBuilder.listener(listener);
                        }

                        requestBuilder.placeholder(placeholderRes).into(into);

                        return true;
                    }
                    return false;
                }

                @Override
                public boolean onResourceReady(Drawable drawable, Object o, Target<Drawable> target, DataSource dataSource, boolean b) {
                    return false;
                }
            });
        } else {
            if (listener != null) {
                originalRequest.listener(listener);
            }

            if (placeholderRes != null) {
                originalRequest.placeholder(placeholderRes);
            }
        }

        originalRequest.into(into);

    }

    public static void showKeyboard() {
        Context context = MimiApplication.getInstance().getApplicationContext();
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    public static void hideKeyboard(View view) {
        Context context = MimiApplication.getInstance().getApplicationContext();
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static int findPostPositionById(long id, List<ChanPost> posts) {
        if (posts == null) {
            return -1;
        }

        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).getNo() == id) {
                return i;
            }
        }
        return -1;
    }

    public static int findGalleryItemPositionById(long id, List<GalleryItem> posts) {
        if (posts == null || posts.isEmpty()) {
            return -1;
        }

        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).getId() == id) {
                return i;
            }
        }

        return -1;
    }

    public static void setAppBarScrollingEnabled(AppBarLayout appBarLayout, final boolean enabled) {
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) appBarLayout.getLayoutParams();
        AppBarLayout.Behavior behavior = (AppBarLayout.Behavior) params.getBehavior();
        behavior.setDragCallback(new AppBarLayout.Behavior.DragCallback() {
            @Override
            public boolean canDrag(@NonNull AppBarLayout appBarLayout) {
                return enabled;
            }
        });
    }

    public interface OperationCompleteListener {
        void onOperationComplete();

        void onOperationFailed();
    }

    public static void deleteRecursive(File fileOrDirectory, boolean keepDir) {
        if (fileOrDirectory == null) {
            return;
        }

        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) {
                deleteRecursive(child, keepDir);
            }
        }

        if (!keepDir || !fileOrDirectory.isDirectory()) {
            fileOrDirectory.delete();
        } else if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Skipping directory: " + fileOrDirectory.getAbsolutePath() + ", keep=" + keepDir);

        }
    }

    static Single<Bitmap> scaleBitmap(@NonNull final File imageFile) {

        return Single.just(imageFile)
                .map(file -> {
                    if (!file.exists()) {
                        return null;
                    }

                    final String ext = MimeTypeMap.getFileExtensionFromUrl(file.getAbsolutePath());
                    final String type;
                    if (ext != null) {
                        final String t = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                        type = t == null ? "" : t;
                    } else {
                        type = "";
                    }

                    if (type.toLowerCase().contains("video")) {
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        retriever.setDataSource(file.getAbsolutePath());
                        Bitmap bmp = retriever.getFrameAtTime();
                        retriever.release();

                        return bmp;
                    }

                    final BitmapFactory.Options options = new BitmapFactory.Options();

                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
                    options.inSampleSize = MimiUtil.calculateInSampleSize(options, 500, 500);
                    options.inJustDecodeBounds = false;

                    if (imageFile.exists()) {
                        Bitmap bmp = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
                        if (bmp == null) {
                            Log.e(LOG_TAG, "Image file is null", new Exception("Could not decode bitmap from " + imageFile.getAbsolutePath() + " width: " + options.outWidth + ", height: " + options.outHeight));
                        }
                        return bmp;
                    } else {
                        Log.e(LOG_TAG, "Image file not found", new Exception("Image file does not exist"));
                        return null;
                    }

                });

    }

    @Nullable
    public static Activity scanForActivity(@Nullable Context cont) {
        if (cont == null)
            return null;
        else if (cont instanceof Activity)
            return (Activity)cont;
        else if (cont instanceof ContextWrapper)
            return scanForActivity(((ContextWrapper)cont).getBaseContext());

        return null;
    }

    public interface ImageDisplayedListener {
        void onImageDisplayed(final Bitmap bmp);
    }
}
