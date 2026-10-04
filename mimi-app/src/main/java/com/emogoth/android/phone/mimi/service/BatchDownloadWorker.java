package com.emogoth.android.phone.mimi.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.activity.StartupActivity;
import com.emogoth.android.phone.mimi.util.HttpClientFactory;
import com.emogoth.android.phone.mimi.util.MimiPrefs;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import com.emogoth.android.phone.mimi.util.NotificationUtils;
import com.emogoth.android.phone.mimi.viewmodel.ChanDataSource;
import com.mimireader.chanlib.models.ChanPost;
import com.mimireader.chanlib.models.ChanThread;
import com.mimireader.chanlib.models.ErrorChanThread;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public final class BatchDownloadWorker extends Worker {
    private static final String LOG_TAG = BatchDownloadWorker.class.getSimpleName();
    private static final String WORK_TAG = "mimi_batch_download";
    private static final String KEY_DIRECTORY_URI = "directory_uri";
    private static final String KEY_BOARD = "board";
    private static final String KEY_THREAD_ID = "thread_id";
    private static final String KEY_POST_IDS = "post_ids";
    private static final String KEY_PROGRESS = "progress";
    private static final String KEY_COMPLETED = "completed";
    private static final String KEY_FAILED = "failed";
    private static final int IO_BUFFER_SIZE = 8 * 1024;
    private static final int MAX_ATTEMPTS = 3;
    private static final int BASE_NOTIFICATION_ID = 2100;

    private volatile Call currentCall;
    private int lastProgress = -1;

    public BatchDownloadWorker(@NonNull Context appContext,
                               @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    public static void enqueue(@NonNull Context context,
                               @NonNull Uri directoryUri,
                               @NonNull String board,
                               long threadId,
                               @NonNull long[] postIds) {
        final Data input = new Data.Builder()
                .putString(KEY_DIRECTORY_URI, directoryUri.toString())
                .putString(KEY_BOARD, board)
                .putLong(KEY_THREAD_ID, threadId)
                .putLongArray(KEY_POST_IDS, postIds)
                .build();

        final Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        final OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BatchDownloadWorker.class)
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .build();

        final String uniqueName = WORK_TAG + ':' + board + ':' + threadId + ':'
                + directoryUri.hashCode() + ':' + Arrays.hashCode(postIds);
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(uniqueName, androidx.work.ExistingWorkPolicy.KEEP, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        final Context context = getApplicationContext();
        final String directory = getInputData().getString(KEY_DIRECTORY_URI);
        final String board = getInputData().getString(KEY_BOARD);
        final long threadId = getInputData().getLong(KEY_THREAD_ID, -1L);
        final long[] postIds = getInputData().getLongArray(KEY_POST_IDS);

        if (TextUtils.isEmpty(directory) || TextUtils.isEmpty(board)
                || threadId <= 0L || postIds == null || postIds.length == 0) {
            Log.e(LOG_TAG, "Batch download has invalid input data");
            return Result.failure(resultData(0, postIds == null ? 0 : postIds.length));
        }

        final Uri directoryUri = Uri.parse(directory);
        final DocumentFile downloadPath = DocumentFile.fromTreeUri(context, directoryUri);
        if (downloadPath == null || !downloadPath.canWrite()) {
            Log.e(LOG_TAG, "Selected download directory is no longer writable: " + directoryUri);
            showCompletionNotification(false);
            return Result.failure(resultData(0, postIds.length));
        }

        NotificationUtils.ensureDownloadChannel(context);
        setForegroundAsync(createForegroundInfo(0, true));

        final ChanThread thread;
        try {
            thread = new ChanDataSource().fetchThread(board, threadId, 0).blockingGet();
        } catch (Throwable error) {
            Log.e(LOG_TAG, "Could not load thread for batch download", error);
            return retryOrFail(0, postIds.length);
        }

        if (thread == null || (thread instanceof ErrorChanThread && thread.getPosts().isEmpty())) {
            Log.e(LOG_TAG, "No post data available for batch download");
            return retryOrFail(0, postIds.length);
        }

        final Set<Long> selectedIds = new HashSet<>();
        for (long postId : postIds) {
            selectedIds.add(postId);
        }

        final boolean useOriginalFilename = MimiPrefs.userOriginalFilename(context);
        final List<ChanPost> posts = thread.getPosts();
        int completed = 0;
        int failed = 0;
        boolean shouldRetry = false;

        for (ChanPost post : posts) {
            if (!selectedIds.contains(post.getNo())) {
                continue;
            }
            if (isStopped()) {
                return Result.failure(resultData(completed, failed));
            }

            final String extension = post.getExt();
            final String remoteName = useOriginalFilename
                    ? post.getFilename() + extension
                    : post.getTim() + extension;
            final String filename = sanitizeFilename(remoteName);
            final String path = context.getString(
                    R.string.full_image_path, board, post.getTim(), extension);
            final String url = MimiUtil.https() + context.getString(R.string.image_link) + path;

            final DownloadResult downloadResult = downloadFile(
                    downloadPath, filename, url, post.getFsize(), completed, postIds.length);
            if (downloadResult == DownloadResult.SUCCESS) {
                completed++;
            } else {
                failed++;
                shouldRetry = shouldRetry || downloadResult == DownloadResult.RETRY;
            }
            publishProgress(completed + failed, postIds.length, 0L, 0L);
        }

        final int missing = Math.max(0, postIds.length - completed - failed);
        failed += missing;

        if (isStopped()) {
            return Result.failure(resultData(completed, failed));
        }
        if (shouldRetry && getRunAttemptCount() + 1 < MAX_ATTEMPTS) {
            Log.w(LOG_TAG, "Retrying batch download; attempt=" + (getRunAttemptCount() + 1));
            return Result.retry();
        }

        final boolean success = failed == 0;
        showCompletionNotification(success);
        return success
                ? Result.success(resultData(completed, 0))
                : Result.failure(resultData(completed, failed));
    }

    @Override
    public void onStopped() {
        final Call call = currentCall;
        if (call != null) {
            call.cancel();
        }
        super.onStopped();
    }

    private DownloadResult downloadFile(DocumentFile directory,
                                        String filename,
                                        String url,
                                        long expectedLength,
                                        int completedFiles,
                                        int totalFiles) {
        final String extension = MimeTypeMap.getFileExtensionFromUrl(filename)
                .toLowerCase(Locale.ROOT);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (TextUtils.isEmpty(mimeType)) {
            mimeType = "application/octet-stream";
        }

        final DocumentFile existing = directory.findFile(filename);
        if (existing != null) {
            if (isCompleteFile(existing.length(), expectedLength)) {
                return DownloadResult.SUCCESS;
            }
            Log.w(LOG_TAG, "Deleting incomplete existing file " + filename
                    + "; actual=" + existing.length() + ", expected=" + expectedLength);
            if (!existing.delete()) {
                return DownloadResult.FAILURE;
            }
        }

        final String temporaryFilename = temporaryFilename(filename, extension);
        final DocumentFile staleTemporary = directory.findFile(temporaryFilename);
        if (staleTemporary != null && !staleTemporary.delete()) {
            Log.w(LOG_TAG, "Could not remove stale temporary file " + temporaryFilename);
            return DownloadResult.RETRY;
        }

        final DocumentFile target = directory.createFile(mimeType, temporaryFilename);
        if (target == null) {
            return DownloadResult.FAILURE;
        }

        try {
            final Request request = new Request.Builder().url(url).get().build();
            currentCall = HttpClientFactory.getInstance().getClient().newCall(request);
            try (Response response = currentCall.execute()) {
                if (!response.isSuccessful()) {
                    target.delete();
                    return isRetryableHttpStatus(response.code())
                            ? DownloadResult.RETRY
                            : DownloadResult.FAILURE;
                }

                final ResponseBody body = response.body();
                if (body == null) {
                    target.delete();
                    return DownloadResult.RETRY;
                }

                final long responseLength = body.contentLength();
                if (!areLengthsCompatible(expectedLength, responseLength)) {
                    Log.w(LOG_TAG, "Unexpected content length for " + filename
                            + "; response=" + responseLength + ", expected=" + expectedLength);
                    target.delete();
                    return DownloadResult.RETRY;
                }

                long bytesReadTotal = 0L;
                try (OutputStream output = getApplicationContext().getContentResolver()
                        .openOutputStream(target.getUri(), "w")) {
                    if (output == null) {
                        target.delete();
                        return DownloadResult.FAILURE;
                    }

                    try (BufferedSource source = body.source();
                         BufferedSink sink = Okio.buffer(Okio.sink(output))) {
                        long bytesRead;
                        while ((bytesRead = source.read(sink.buffer(), IO_BUFFER_SIZE)) != -1L) {
                            if (isStopped()) {
                                throw new IOException("Batch download was cancelled");
                            }
                            bytesReadTotal += bytesRead;
                            sink.emitCompleteSegments();
                            publishProgress(
                                    completedFiles, totalFiles, bytesReadTotal, responseLength);
                        }
                        sink.flush();
                    }
                }

                if (!isCompleteTransfer(bytesReadTotal, expectedLength, responseLength)) {
                    Log.w(LOG_TAG, "Incomplete download for " + filename
                            + "; received=" + bytesReadTotal + ", response=" + responseLength
                            + ", expected=" + expectedLength);
                    target.delete();
                    return DownloadResult.RETRY;
                }
            } finally {
                currentCall = null;
            }

            if (!target.renameTo(filename)) {
                Log.e(LOG_TAG, "Could not finalize temporary file " + target.getName());
                target.delete();
                return DownloadResult.RETRY;
            }
            if (!isCompleteFile(target.length(), expectedLength)) {
                Log.e(LOG_TAG, "Finalized file has an unexpected size " + filename
                        + "; actual=" + target.length() + ", expected=" + expectedLength);
                target.delete();
                return DownloadResult.RETRY;
            }
            return DownloadResult.SUCCESS;
        } catch (IOException error) {
            Log.e(LOG_TAG, "Error downloading " + url, error);
            target.delete();
            return isStopped() ? DownloadResult.FAILURE : DownloadResult.RETRY;
        } catch (RuntimeException error) {
            Log.e(LOG_TAG, "Could not write " + filename, error);
            target.delete();
            return DownloadResult.FAILURE;
        }
    }

    private void publishProgress(int completedFiles,
                                 int totalFiles,
                                 long currentBytes,
                                 long currentLength) {
        final double currentFraction = currentLength > 0L
                ? Math.min(1.0, (double) currentBytes / (double) currentLength)
                : 0.0;
        final int progress = Math.min(100, (int) Math.round(
                ((completedFiles + currentFraction) / Math.max(1, totalFiles)) * 100.0));
        if (progress == lastProgress || (progress < 100 && progress % 2 != 0)) {
            return;
        }

        lastProgress = progress;
        setProgressAsync(new Data.Builder()
                .putInt(KEY_PROGRESS, progress)
                .putInt(KEY_COMPLETED, completedFiles)
                .build());
        setForegroundAsync(createForegroundInfo(progress, false));
    }

    private ForegroundInfo createForegroundInfo(int progress, boolean indeterminate) {
        final Context context = getApplicationContext();
        final Intent openIntent = new Intent(context, StartupActivity.class);
        final PendingIntent openPendingIntent = PendingIntent.getActivity(
                context, getNotificationId(), openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        final PendingIntent cancelPendingIntent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        final Notification notification = new NotificationCompat.Builder(
                context, NotificationUtils.DOWNLOADER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getString(R.string.content_title))
                .setContentText(indeterminate
                        ? context.getString(R.string.download_ticker)
                        : context.getString(R.string.percent_complete, progress))
                .setContentIntent(openPendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, progress, indeterminate)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        context.getString(R.string.cancel), cancelPendingIntent)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new ForegroundInfo(
                    getNotificationId(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
        return new ForegroundInfo(getNotificationId(), notification);
    }

    private void showCompletionNotification(boolean success) {
        final Context context = getApplicationContext();
        if (!NotificationUtils.canPostNotifications(context)) {
            return;
        }
        NotificationUtils.ensureDownloadChannel(context);

        final Intent intent = new Intent(context, StartupActivity.class);
        final PendingIntent contentIntent = PendingIntent.getActivity(
                context, getNotificationId(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        final Notification notification = new NotificationCompat.Builder(
                context, NotificationUtils.DOWNLOADER_CHANNEL_ID)
                .setSmallIcon(success
                        ? R.drawable.ic_notification_photo
                        : android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.content_title))
                .setContentText(context.getString(success
                        ? R.string.download_complete_success
                        : R.string.download_complete_error))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build();
        final NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(getNotificationId() + 10000, notification);
        }
    }

    private Result retryOrFail(int completed, int failed) {
        if (getRunAttemptCount() + 1 < MAX_ATTEMPTS) {
            return Result.retry();
        }
        showCompletionNotification(false);
        return Result.failure(resultData(completed, failed));
    }

    private Data resultData(int completed, int failed) {
        return new Data.Builder()
                .putInt(KEY_COMPLETED, completed)
                .putInt(KEY_FAILED, failed)
                .build();
    }

    private int getNotificationId() {
        return BASE_NOTIFICATION_ID + Math.abs(getId().hashCode() % 7000);
    }

    private static String sanitizeFilename(String filename) {
        final String leafName = new File(filename == null ? "download" : filename).getName();
        final String sanitized = leafName.replace('/', '_').replace('\\', '_').trim();
        return sanitized.isEmpty() ? "download" : sanitized;
    }

    static boolean isRetryableHttpStatus(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    static boolean isCompleteFile(long actualLength, long expectedLength) {
        return expectedLength > 0L
                ? actualLength == expectedLength
                : actualLength > 0L;
    }

    static boolean areLengthsCompatible(long expectedLength, long responseLength) {
        return expectedLength <= 0L || responseLength <= 0L || expectedLength == responseLength;
    }

    static boolean isCompleteTransfer(long transferredLength,
                                      long expectedLength,
                                      long responseLength) {
        return transferredLength > 0L
                && (expectedLength <= 0L || transferredLength == expectedLength)
                && (responseLength <= 0L || transferredLength == responseLength);
    }

    private static String temporaryFilename(String filename, String extension) {
        return TextUtils.isEmpty(extension)
                ? filename + ".mimi-part"
                : filename + ".mimi-part." + extension;
    }

    private enum DownloadResult {
        SUCCESS,
        RETRY,
        FAILURE
    }
}
