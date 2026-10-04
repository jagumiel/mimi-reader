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

package com.emogoth.android.phone.mimi.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.activity.StartupActivity;
import com.emogoth.android.phone.mimi.app.MimiApplication;
import com.emogoth.android.phone.mimi.util.Extras;
import com.emogoth.android.phone.mimi.util.HttpClientFactory;
import com.emogoth.android.phone.mimi.util.MimiPrefs;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import com.emogoth.android.phone.mimi.util.NotificationUtils;
import com.emogoth.android.phone.mimi.viewmodel.ChanDataSource;
import com.mimireader.chanlib.models.ChanPost;
import com.mimireader.chanlib.models.ChanThread;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;


public class DownloadService extends IntentService {
    public static final String LOG_TAG = DownloadService.class.getSimpleName();
    public static final int IO_BUFFER_SIZE = 1024 * 2;

    public static final int REGISTRY_ID = 1001;

    // keys
    public static final String CONTEXT_KEY = "context"; // The context we will be running in. Usually the application context.
    public static final String RECEIVER_KEY = "receiver";
    public static final String POSITION_KEY = "position";
    public static final String FILENAME_KEY = "filename";
    public static final String FILEURL_KEY = "fileurl";
    public static final String THREADID_KEY = "threadid";
    public static final String THUMBNAIL_KEY = "thumbnailname";
    public static final String THUMBURL_KEY = "thumburl";
    public static final String FILESIZE_KEY = "filesize";
    public static final String STATUS_KEY = "status";
    public static final String ERROR_KEY = "error";
    public static final String WIDTH_KEY = "display_width";
    public static final String PROGRESS_KEY = "progress";
    public static final String FILTER_KEY = "filter";
    public static final String THREAD_KEY = "threadid";
    public static final String DATA_KEY = "data";
    public static final String ITEMS_KEY = "items";
    public static final String DOWNLOAD_TYPE_KEY = "downloadtype";

    // responses
    public static final String COMMAND_RECEIVED = "command_received";
    public static final String COMMAND_SAVE = "command_save";
    public static final String COMMAND_SAVE_DEVICE = "command_save_device";
    public static final String PERCENT_DOWNLOADED = "percent_downloaded";

    // Status Constants
    public static final int STATUS_STARTED = 0x1;
    public static final int STATUS_RUNNING = 0x2;
    public static final int STATUS_FINISHED = 0x3;
    public static final int STATUS_SUCCESS = 0x4;
    public static final int STATUS_ERROR = 0x5;

    // Download Types
    public static final int DOWNLOAD_BATCH = 0x1;
    public static final int DOWNLOAD_SINGLE = 0x2;
    public static final int DOWNLOAD_CANCEL = 0x3;

    // Storage Constants
    public static final int SAVE_CACHE = 0x1;
    public static final int SAVE_SDCARD = 0x2;

    private static final int KB = 1024;
    private static final int MB = KB * 1024;

    public static final String DOWNLOADER_CHANNEL_ID = "mimi_file_downloader";

//    private ArrayList<ChanPost> posts;

    private int position;
    private int fileSize;
    private String intentFilter;
    private MimiApplication app;

    private int NOTIFICATION_ID = 1;
    private NotificationCompat.Builder notificationCompat;
    private Notification notification;
    private NotificationManager notificationManager;

    private ThreadPoolExecutor executor;
    private HashMap<String, Runnable> runnableMap;
    private String downloadDir;

    public DownloadService() {
        super("MimiDownloadService");

        executor = new ThreadPoolExecutor(2, 4, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(2));
        runnableMap = new HashMap<>();
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        boolean is_err = false;
        final Bundle serviceData = intent.getExtras();

        if (serviceData == null) {
            return;
        }

        final int downloadType = serviceData.getInt(DownloadService.DOWNLOAD_TYPE_KEY);

        app = (MimiApplication) getApplication();
        intentFilter = serviceData.getString(FILTER_KEY);

        switch (downloadType) {
            case DownloadService.DOWNLOAD_SINGLE:

                final String fileUrl = serviceData.getString(FILEURL_KEY);

                final Runnable downloadRunnable = () -> {
                    position = serviceData.getInt(POSITION_KEY);
                    String downloadFileName = serviceData.getString(FILENAME_KEY);
                    fileSize = serviceData.getInt(FILESIZE_KEY);

                    downloadDir = serviceData.getString(COMMAND_SAVE);

                    final boolean isError = downloadFile(downloadDir, downloadFileName, fileUrl);

                    final Intent returnIntent = new Intent();

                    returnIntent.setAction(intentFilter);
                    returnIntent.addCategory(Intent.CATEGORY_DEFAULT);
                    returnIntent.putExtra(POSITION_KEY, position);
                    returnIntent.putExtra(ERROR_KEY, isError);
                    returnIntent.putExtra(STATUS_KEY, STATUS_FINISHED); // complete
                    LocalBroadcastManager.getInstance(app).sendBroadcast(returnIntent);

                    runnableMap.remove(fileUrl);
                };
                runnableMap.put(fileUrl, downloadRunnable);
                executor.execute(downloadRunnable);

//            sendBroadcast(returnIntent);
                break;
            case DownloadService.DOWNLOAD_BATCH:
                serviceData.setClassLoader(ChanPost.class.getClassLoader());
//                final List<ChanPost> batchData;
//
//                if (serviceData.containsKey(DATA_KEY)) {
//                    batchData = serviceData.getParcelableArrayList(DATA_KEY);
//                } else {
//                    batchData = ThreadRegistry.getInstance().getPosts(REGISTRY_ID);
//                    ThreadRegistry.getInstance().clearPosts(REGISTRY_ID);
//                }

                final long[] postIds = serviceData.getLongArray(Extras.EXTRAS_POST_LIST);

                final String board = serviceData.getString(Extras.EXTRAS_BOARD_NAME);
                final long threadId = serviceData.getLong(Extras.EXTRAS_THREAD_ID);
                downloadDir = serviceData.getString(COMMAND_SAVE);

                final String protocol = MimiUtil.https();
                final String baseUrl = getString(R.string.image_link);

                final String[] location = new String[postIds.length];

                final boolean useOriginalFilename = MimiPrefs.userOriginalFilename(this);

                createNotification();

                final ChanDataSource dataSource = new ChanDataSource();
                dataSource.fetchThread(board, threadId, 0)
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.io())
                        .subscribe(new SingleObserver<ChanThread>() {
                            @Override
                            public void onSubscribe(@NonNull Disposable d) {

                            }

                            @Override
                            public void onSuccess(@NonNull ChanThread chanThread) {
                                final DocumentFile downloadPath = DocumentFile.fromTreeUri(
                                        DownloadService.this, Uri.parse(downloadDir));
                                if (downloadPath == null || !downloadPath.canWrite()) {
                                    notificationCompleted(false);
                                    return;
                                }

                                List<ChanPost> posts = chanThread.getPosts();
                                boolean hadErrors = false;
                                for (int i = 0; i < posts.size(); i++) {
                                    final ChanPost post = posts.get(i);
                                    final int idPos = MimiUtil.arrayLocation(postIds, post.getNo());
                                    if (idPos < 0) {
                                        continue;
                                    }

                                    final String basePath = getString(R.string.full_image_path, board, post.getTim(), post.getExt());
                                    final String url = protocol + baseUrl + basePath;

                                    Log.i(LOG_TAG, "Downloading image: url=" + url);

                                    final Intent startedIntent = new Intent();
                                    String fileName;
                                    if (useOriginalFilename) {
                                        fileName = post.getFilename() + post.getExt();
                                    } else {
                                        fileName = post.getTim() + post.getExt();
                                    }

                                    final DocumentFile existingFile = downloadPath.findFile(fileName);

                                    final float fProgress = (float) idPos / (float) postIds.length;
                                    final int iProgress = (int) (fProgress * 100.0);

                                    boolean isError;

                                    notificationProgressUpdate(iProgress);

                                    if (!TextUtils.isEmpty(intentFilter)) {
                                        startedIntent.setAction(intentFilter); // com.emogoth.android.phone.mimi.donate.intent.action.BatchDownloadResponse
                                        startedIntent.addCategory(Intent.CATEGORY_DEFAULT);
                                        startedIntent.putExtra(THREADID_KEY, post.getNo());
                                        startedIntent.putExtra(POSITION_KEY, i);
                                        startedIntent.putExtra(ERROR_KEY, false);
                                        startedIntent.putExtra(STATUS_KEY, STATUS_STARTED); // complete

                                        Log.i(LOG_TAG, "Sent: STATUS_STARTED [" + i + "]");
                                        LocalBroadcastManager.getInstance(app.getApplicationContext()).sendBroadcast(startedIntent);
                                    }

                                    if (existingFile == null) {
                                        fileSize = post.getFsize();
                                        isError = downloadFile(downloadDir, fileName, url);
                                        hadErrors = hadErrors || isError;

                                        if (isError) {
                                            Log.w(LOG_TAG, "Error downloading file: " + fileName + " to " + downloadDir, new Exception());
                                        }

                                        if (!TextUtils.isEmpty(intentFilter)) {
                                            final Intent finishedIntent = new Intent();

                                            finishedIntent.setAction(intentFilter);
                                            finishedIntent.addCategory(Intent.CATEGORY_DEFAULT);
                                            finishedIntent.putExtra(THREADID_KEY, post.getNo());
                                            finishedIntent.putExtra(POSITION_KEY, i);
                                            finishedIntent.putExtra(ERROR_KEY, isError);
                                            finishedIntent.putExtra(STATUS_KEY, STATUS_FINISHED); // complete

                                            Log.i(LOG_TAG, "Sent: STATUS_FINISHED[" + i + "]");
                                            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(finishedIntent);
                                        }
                                    }

                                    location[idPos] = downloadDir;

                                }

                                notificationCompleted(!hadErrors);
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.e(LOG_TAG, "Could not load thread for batch download", e);
                                notificationCompleted(false);
                            }
                        });
                break;
            case DOWNLOAD_CANCEL:

            default:
                break;
        }

    }

    private boolean downloadFile(String dir, String filename, String urlStr) {
        if (TextUtils.isEmpty(dir) || TextUtils.isEmpty(filename) || TextUtils.isEmpty(urlStr)) {
            return true;
        }

        String ext = filename.substring(filename.lastIndexOf(".") + 1);
        DocumentFile documentPath = DocumentFile.fromTreeUri(this, Uri.parse(dir));
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (documentPath == null || !documentPath.canWrite()) {
            return true;
        }
        if (TextUtils.isEmpty(type)) {
            type = "application/octet-stream";
        }
        if (documentPath.findFile(filename) != null) {
            return false;
        }

        final DocumentFile targetFile = documentPath.createFile(type, filename);
        if (targetFile == null) {
            return true;
        }

        try {
            OkHttpClient httpClient = HttpClientFactory.getInstance().getClient();
            Request.Builder requestBuilder = new Request.Builder();
            requestBuilder.url(urlStr)
                    .tag(urlStr)
                    .get();

            Request request = requestBuilder.build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.e(LOG_TAG, "Http status code: " + response.code());
                    targetFile.delete();
                    sendDownloadError();
                    return true;
                }

                ResponseBody body = response.body();
                if (body == null) {
                    targetFile.delete();
                    return true;
                }

                try (OutputStream output = getContentResolver().openOutputStream(targetFile.getUri(), "w")) {
                    if (output == null) {
                        targetFile.delete();
                        return true;
                    }

                    try (BufferedSource source = body.source();
                         BufferedSink sink = Okio.buffer(Okio.sink(output))) {
                    long totalBytes = 0L;
                    long bytesRead;
                    int previousPercent = -1;
                    while ((bytesRead = source.read(sink.buffer(), IO_BUFFER_SIZE)) != -1) {
                        totalBytes += bytesRead;
                        sink.emitCompleteSegments();

                        if (!TextUtils.isEmpty(intentFilter) && fileSize > 0) {
                            final int percent = Math.min(100,
                                    (int) ((totalBytes * 100L) / fileSize));
                            if (percent % 5 == 0 && percent != previousPercent) {
                                final Intent returnIntent = new Intent();
                                returnIntent.setAction(intentFilter);
                                returnIntent.addCategory(Intent.CATEGORY_DEFAULT);
                                returnIntent.putExtra(POSITION_KEY, position);
                                returnIntent.putExtra(ERROR_KEY, false);
                                returnIntent.putExtra(STATUS_KEY, STATUS_RUNNING);
                                returnIntent.putExtra(PROGRESS_KEY, percent);
                                LocalBroadcastManager.getInstance(DownloadService.this)
                                        .sendBroadcast(returnIntent);
                                previousPercent = percent;
                            }
                        }
                    }
                        sink.flush();
                    }
                }
            }

            if (!filename.equals(targetFile.getName())) {
                targetFile.renameTo(filename);
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "Error Downloading Image: ", e);
            targetFile.delete();
            return true;
        }

        return false;
    }

    private void sendDownloadError() {
        if (TextUtils.isEmpty(intentFilter)) {
            return;
        }
        final Intent returnIntent = new Intent();
        returnIntent.setAction(intentFilter);
        returnIntent.addCategory(Intent.CATEGORY_DEFAULT);
        returnIntent.putExtra(POSITION_KEY, position);
        returnIntent.putExtra(ERROR_KEY, true);
        returnIntent.putExtra(STATUS_KEY, STATUS_ERROR);
        returnIntent.putExtra(PROGRESS_KEY, 0);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(returnIntent);
    }

    public void createNotification() {
        //get the notification manager
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        //create the notification
        int icon = android.R.drawable.stat_sys_download;
        CharSequence tickerText = getString(R.string.download_ticker); //Initial text that appears in the status bar
        long when = System.currentTimeMillis();

        notificationCompat = new NotificationCompat.Builder(app);

        //create the content which is shown in the notification pulldown
        final CharSequence contentTitle = getString(R.string.content_title);
        final CharSequence contentText = getString(R.string.percent_complete, 0); //Text of the notification in the pull down

        //you have to set a PendingIntent on a notification to tell the system what you want it to do when the notification is selected
        //I don't want to use this here so I'm just creating a blank one
        final Intent notificationIntent = new Intent(app, StartupActivity.class);
        final PendingIntent contentIntent = PendingIntent.getActivity(
                app, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        notificationCompat.setContentIntent(contentIntent)
                .setSmallIcon(icon)
                .setTicker(tickerText)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setAutoCancel(true)
                .setWhen(when);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelName = getString(R.string.mimi_file_downloader);

            NotificationChannel saveFileChannel = new NotificationChannel(DownloadService.DOWNLOADER_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW);

            notificationCompat.setChannelId(DownloadService.DOWNLOADER_CHANNEL_ID);
            notificationManager.createNotificationChannel(saveFileChannel);
        }

        notification = notificationCompat.build();
        notification.flags = Notification.FLAG_ONGOING_EVENT;

        //show the notification
        if (NotificationUtils.canPostNotifications(this)) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    public void notificationProgressUpdate(int percentageComplete) {
        //build up the new status message
//        CharSequence contentText = fileName;

        // set up the notification
        notificationCompat
                .setContentText(getString(R.string.percent_complete, percentageComplete))
                .setProgress(100, percentageComplete, false);

        //publish it to the status bar
        notification = notificationCompat.build();
        if (NotificationUtils.canPostNotifications(this)) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }

        Log.i(LOG_TAG, "Notification update: " + percentageComplete + "%");
    }

    public void notificationCompleted(boolean success) {

        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Log.i(LOG_TAG, "Notification completed");

        CharSequence tickerText = getString(R.string.download_ticker); //Initial text that appears in the status bar
        long when = System.currentTimeMillis();

        notificationCompat = new NotificationCompat.Builder(app);

        //create the content which is shown in the notification pulldown
        final CharSequence contentTitle = getString(R.string.content_title);
        final CharSequence contentText = success ? getString(R.string.download_complete_success) : getString(R.string.download_complete_error); //Text of the notification in the pull down

        //you have to set a PendingIntent on a notification to tell the system what you want it to do when the notification is selected
        //I don't want to use this here so I'm just creating a blank one
        final Intent notificationIntent = new Intent(app, StartupActivity.class);
        final PendingIntent contentIntent = PendingIntent.getActivity(
                app, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        notificationCompat.setContentIntent(contentIntent)
//                .setProgress(100, 100, false)
                .setSmallIcon(R.drawable.ic_notification_photo)
                .setTicker(tickerText)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setAutoCancel(true)
                .setWhen(when);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelName = getString(R.string.mimi_file_downloader);

            NotificationChannel saveFileChannel = new NotificationChannel(DownloadService.DOWNLOADER_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW);

            notificationCompat.setChannelId(DownloadService.DOWNLOADER_CHANNEL_ID);
            notificationManager.createNotificationChannel(saveFileChannel);
        }

        notification = notificationCompat.build();
//        notification.flags = Notification.FLAG_ONGOING_EVENT;

        //show the notification
        if (NotificationUtils.canPostNotifications(this)) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }

//        notification.flags = Notification.DEFAULT_ALL;
//        notificationCompat.setContentText(getString(R.string.download_complete));
//        notificationManager.notify(NOTIFICATION_ID, notification);
//        notificationManager.cancel(NOTIFICATION_ID);
    }

}
