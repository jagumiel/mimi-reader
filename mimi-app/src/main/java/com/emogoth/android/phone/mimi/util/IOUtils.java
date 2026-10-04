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
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.app.MimiApplication;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
public class IOUtils {
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    private static final int EOF = -1;

    private static final int SKIP_BUFFER_SIZE = 2048;
    private static byte[] SKIP_BYTE_BUFFER;

    /**
     * Simple wrapper around {@link java.io.InputStream#read()} that throws EOFException
     * instead of returning -1.
     */
    public static int read(InputStream is) throws IOException {
        int b = is.read();
        if (b == -1) {
            throw new EOFException();
        }
        return b;
    }

    public static void writeInt(OutputStream os, int n) throws IOException {
        os.write((n >> 0) & 0xff);
        os.write((n >> 8) & 0xff);
        os.write((n >> 16) & 0xff);
        os.write((n >> 24) & 0xff);
    }

    public static int readInt(InputStream is) throws IOException {
        int n = 0;
        n |= (read(is) << 0);
        n |= (read(is) << 8);
        n |= (read(is) << 16);
        n |= (read(is) << 24);
        return n;
    }

    public static void writeLong(OutputStream os, long n) throws IOException {
        os.write((byte) (n >>> 0));
        os.write((byte) (n >>> 8));
        os.write((byte) (n >>> 16));
        os.write((byte) (n >>> 24));
        os.write((byte) (n >>> 32));
        os.write((byte) (n >>> 40));
        os.write((byte) (n >>> 48));
        os.write((byte) (n >>> 56));
    }

    public static long readLong(InputStream is) throws IOException {
        long n = 0;
        n |= ((read(is) & 0xFFL) << 0);
        n |= ((read(is) & 0xFFL) << 8);
        n |= ((read(is) & 0xFFL) << 16);
        n |= ((read(is) & 0xFFL) << 24);
        n |= ((read(is) & 0xFFL) << 32);
        n |= ((read(is) & 0xFFL) << 40);
        n |= ((read(is) & 0xFFL) << 48);
        n |= ((read(is) & 0xFFL) << 56);
        return n;
    }

    public static void writeString(OutputStream os, String s) throws IOException {
        byte[] b = s.getBytes("UTF-8");
        writeLong(os, b.length);
        os.write(b, 0, b.length);
    }

    public static String readString(InputStream is) throws IOException {
        int n = (int) readLong(is);
        byte[] b = streamToBytes(is, n);
        return new String(b, "UTF-8");
    }

    public static void writeStringStringMap(Map<String, String> map, OutputStream os) throws IOException {
        if (map != null) {
            writeInt(os, map.size());
            for (Map.Entry<String, String> entry : map.entrySet()) {
                writeString(os, entry.getKey());
                writeString(os, entry.getValue());
            }
        } else {
            writeInt(os, 0);
        }
    }

    public static Map<String, String> readStringStringMap(InputStream is) throws IOException {
        int size = readInt(is);
        Map<String, String> result = (size == 0)
                ? Collections.<String, String>emptyMap()
                : new HashMap<String, String>(size);
        for (int i = 0; i < size; i++) {
            String key = readString(is).intern();
            String value = readString(is).intern();
            result.put(key, value);
        }
        return result;
    }


    /**
     * Reads the contents of an InputStream into a byte[].
     */
    public static byte[] streamToBytes(InputStream in, int length) throws IOException {
        byte[] bytes = new byte[length];
        int count;
        int pos = 0;
        while (pos < length && ((count = in.read(bytes, pos, length - pos)) != -1)) {
            pos += count;
        }
        if (pos != length) {
            throw new IOException("Expected " + length + " bytes, read " + pos + " bytes");
        }
        return bytes;
    }

    public static class CountingInputStream extends FilterInputStream {
        private int bytesRead = 0;

        public CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int result = super.read();
            if (result != -1) {
                bytesRead++;
            }
            return result;
        }

        @Override
        public int read(byte[] buffer, int offset, int count) throws IOException {
            int result = super.read(buffer, offset, count);
            if (result != -1) {
                bytesRead += result;
            }
            return result;
        }

        public long getBytesRead() {
            return bytesRead;
        }
    }

    /**
     * Copy bytes from a large (over 2GB) <code>InputStream</code> to an
     * <code>OutputStream</code>.
     * <p>
     * This method buffers the input internally, so there is no need to use a
     * <code>BufferedInputStream</code>.
     * <p>
     * The buffer size is given by {@link #DEFAULT_BUFFER_SIZE}.
     *
     * @param input  the <code>InputStream</code> to read from
     * @param output the <code>OutputStream</code> to write to
     * @return the number of bytes copied
     * @throws NullPointerException if the input or output is null
     * @throws IOException          if an I/O error occurs
     * @since 1.3
     */
    public static long copyLarge(InputStream input, OutputStream output)
            throws IOException {
        return copyLarge(input, output, new byte[DEFAULT_BUFFER_SIZE]);
    }

    /**
     * Copy bytes from a large (over 2GB) <code>InputStream</code> to an
     * <code>OutputStream</code>.
     * <p>
     * This method uses the provided buffer, so there is no need to use a
     * <code>BufferedInputStream</code>.
     * <p>
     *
     * @param input  the <code>InputStream</code> to read from
     * @param output the <code>OutputStream</code> to write to
     * @param buffer the buffer to use for the copy
     * @return the number of bytes copied
     * @throws NullPointerException if the input or output is null
     * @throws IOException          if an I/O error occurs
     * @since 2.2
     */
    public static long copyLarge(InputStream input, OutputStream output, byte[] buffer)
            throws IOException {
        long count = 0;
        int n = 0;
        while (EOF != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    /**
     * Copy some or all bytes from a large (over 2GB) <code>InputStream</code> to an
     * <code>OutputStream</code>, optionally skipping input bytes.
     * <p>
     * This method buffers the input internally, so there is no need to use a
     * <code>BufferedInputStream</code>.
     * <p>
     * The buffer size is given by {@link #DEFAULT_BUFFER_SIZE}.
     *
     * @param input       the <code>InputStream</code> to read from
     * @param output      the <code>OutputStream</code> to write to
     * @param inputOffset : number of bytes to skip from input before copying
     *                    -ve values are ignored
     * @param length      : number of bytes to copy. -ve means all
     * @return the number of bytes copied
     * @throws NullPointerException if the input or output is null
     * @throws IOException          if an I/O error occurs
     * @since 2.2
     */
    public static long copyLarge(InputStream input, OutputStream output, long inputOffset, long length)
            throws IOException {
        return copyLarge(input, output, inputOffset, length, new byte[DEFAULT_BUFFER_SIZE]);
    }

    /**
     * Copy some or all bytes from a large (over 2GB) <code>InputStream</code> to an
     * <code>OutputStream</code>, optionally skipping input bytes.
     * <p>
     * This method uses the provided buffer, so there is no need to use a
     * <code>BufferedInputStream</code>.
     * <p>
     *
     * @param input       the <code>InputStream</code> to read from
     * @param output      the <code>OutputStream</code> to write to
     * @param inputOffset : number of bytes to skip from input before copying
     *                    -ve values are ignored
     * @param length      : number of bytes to copy. -ve means all
     * @param buffer      the buffer to use for the copy
     * @return the number of bytes copied
     * @throws NullPointerException if the input or output is null
     * @throws IOException          if an I/O error occurs
     * @since 2.2
     */
    public static long copyLarge(InputStream input, OutputStream output,
                                 final long inputOffset, final long length, byte[] buffer) throws IOException {
        if (inputOffset > 0) {
            skipFully(input, inputOffset);
        }
        if (length == 0) {
            return 0;
        }
        final int bufferLength = buffer.length;
        int bytesToRead = bufferLength;
        if (length > 0 && length < bufferLength) {
            bytesToRead = (int) length;
        }
        int read;
        long totalRead = 0;
        while (bytesToRead > 0 && EOF != (read = input.read(buffer, 0, bytesToRead))) {
            output.write(buffer, 0, read);
            totalRead += read;
            if (length > 0) { // only adjust length if not reading to the end
                // Note the cast must work because buffer.length is an integer
                bytesToRead = (int) Math.min(length - totalRead, bufferLength);
            }
        }
        return totalRead;
    }

    /**
     * Skip bytes from an input byte stream.
     * This implementation guarantees that it will read as many bytes
     * as possible before giving up; this may not always be the case for
     * subclasses of {@link Reader}.
     *
     * @param input  byte stream to skip
     * @param toSkip number of bytes to skip.
     * @return number of bytes actually skipped.
     * @throws IOException              if there is a problem reading the file
     * @throws IllegalArgumentException if toSkip is negative
     * @see InputStream#skip(long)
     * @since 2.0
     */
    public static long skip(InputStream input, long toSkip) throws IOException {
        if (toSkip < 0) {
            throw new IllegalArgumentException("Skip count must be non-negative, actual: " + toSkip);
        }
        /*
         * N.B. no need to synchronize this because: - we don't care if the buffer is created multiple times (the data
         * is ignored) - we always use the same size buffer, so if it it is recreated it will still be OK (if the buffer
         * size were variable, we would need to synch. to ensure some other thread did not create a smaller one)
         */
        if (SKIP_BYTE_BUFFER == null) {
            SKIP_BYTE_BUFFER = new byte[SKIP_BUFFER_SIZE];
        }
        long remain = toSkip;
        while (remain > 0) {
            long n = input.read(SKIP_BYTE_BUFFER, 0, (int) Math.min(remain, SKIP_BUFFER_SIZE));
            if (n < 0) { // EOF
                break;
            }
            remain -= n;
        }
        return toSkip - remain;
    }

    /**
     * Skip the requested number of bytes or fail if there are not enough left.
     * <p>
     * This allows for the possibility that {@link InputStream#skip(long)} may
     * not skip as many bytes as requested (most likely because of reaching EOF).
     *
     * @param input  stream to skip
     * @param toSkip the number of bytes to skip
     * @throws IOException              if there is a problem reading the file
     * @throws IllegalArgumentException if toSkip is negative
     * @throws EOFException             if the number of bytes skipped was incorrect
     * @see InputStream#skip(long)
     * @since 2.0
     */
    public static void skipFully(InputStream input, long toSkip) throws IOException {
        if (toSkip < 0) {
            throw new IllegalArgumentException("Bytes to skip must not be negative: " + toSkip);
        }
        long skipped = skip(input, toSkip);
        if (skipped != toSkip) {
            throw new EOFException("Bytes to skip: " + toSkip + " actual: " + skipped);
        }
    }

    /**
     * Unconditionally close a <code>Closeable</code>.
     * <p>
     * Equivalent to {@link Closeable#close()}, except any exceptions will be ignored.
     * This is typically used in finally blocks.
     * <p>
     * Example code:
     * <pre>
     *   Closeable closeable = null;
     *   try {
     *       closeable = new FileReader("foo.txt");
     *       // process closeable
     *       closeable.close();
     *   } catch (Exception e) {
     *       // error handling
     *   } finally {
     *       IOUtils.closeQuietly(closeable);
     *   }
     * </pre>
     *
     * @param closeable the object to close, may be null or already closed
     * @since 2.0
     */
    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ioe) {
            // ignore
        }
    }

    private static final int NOTIFICATION_ID = 87;

    private static final int ACTION_CANCEL = 1;
    private static final int ACTION_OVERWITE = 2;
    private static final int ACTION_RENAME = 3;

    public static final int REQUEST_CODE_DIR_CHOOSER_PERSISTENT = 44;

    private static final String LOG_TAG = "IOUtils";

    public static void safeSaveFile(final Activity activity, final DocumentFile saveDir, final File localFile, final String saveFileName, final boolean showNotification) {
        if (saveDir != null && saveDir.canWrite() && localFile != null && localFile.exists()) {
            final DocumentFile potentialFile = saveDir.findFile(saveFileName);
            if (potentialFile != null && potentialFile.exists()) {
                final MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(activity);
                dialogBuilder.setTitle(R.string.copy_file)
                        .setMessage(R.string.file_name_is_taken)
                        .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                        .setNeutralButton(R.string.overwrite, (dialog, which) -> saveFileWithRetry(saveDir, localFile, saveFileName, showNotification, ACTION_OVERWITE, 2))
                        .setPositiveButton(R.string.rename, (dialog, which) -> saveFileWithRetry(saveDir, localFile, saveFileName, showNotification, ACTION_RENAME, 2))
                        .show();
            } else {
                saveFileWithRetry(saveDir, localFile, saveFileName, showNotification, ACTION_OVERWITE, 2);
            }
        } else {
            final MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(activity);
            dialogBuilder.setTitle(R.string.requesting_permissions)
                    .setMessage(R.string.save_permissions_message)
                    .setNegativeButton(R.string.cancel, (dialogInterface, i) -> dialogInterface.cancel())
                    .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        activity.startActivityForResult(intent, REQUEST_CODE_DIR_CHOOSER_PERSISTENT);
                    })
                    .setCancelable(true)
                    .show();
        }
    }

    public static boolean saveFileWithRetry(final DocumentFile dir, final File filePath, final String saveFileName, final boolean showNotification, final int action, final int retries) {
        boolean success = false;
        int count = 0;

        while (!success && count < retries) {
            count++;
            success = saveFile(dir, filePath, saveFileName, showNotification, action);
        }

        return success;
    }

    public static boolean saveFile(final DocumentFile dir, final File filePath, final String saveFileName, final boolean showNotification, final int action) {
        try {
            final DocumentFile saveDir = dir == null ? MimiUtil.getSaveDir() : dir;
            if (saveDir == null || !saveDir.canWrite() || filePath == null || !filePath.exists()
                    || TextUtils.isEmpty(saveFileName)) {
                Toast.makeText(MimiApplication.getInstance().getApplicationContext(),
                        R.string.failed_to_save_file, Toast.LENGTH_LONG).show();
                return false;
            }

            final int extensionIndex = saveFileName.lastIndexOf('.');
            final String baseName = extensionIndex > 0
                    ? saveFileName.substring(0, extensionIndex) : saveFileName;
            final String fileExt = extensionIndex > 0
                    ? saveFileName.substring(extensionIndex + 1) : "";
            String targetName = saveFileName;
            DocumentFile existingFile = saveDir.findFile(targetName);

            if (existingFile != null && action == ACTION_CANCEL) {
                return false;
            }
            if (action == ACTION_RENAME) {
                int suffix = 1;
                while (existingFile != null) {
                    targetName = baseName + "(" + suffix + ")"
                            + (TextUtils.isEmpty(fileExt) ? "" : "." + fileExt);
                    existingFile = saveDir.findFile(targetName);
                    suffix++;
                }
            } else if (existingFile != null && !existingFile.delete()) {
                Log.e(LOG_TAG, "Could not overwrite existing document " + targetName);
                return false;
            }

            String mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(fileExt.toLowerCase(Locale.ROOT));
            if (TextUtils.isEmpty(mimeType)) {
                mimeType = "application/octet-stream";
            }

            final DocumentFile writeFile = saveDir.createFile(mimeType, targetName);
            if (writeFile == null) {
                Log.e(LOG_TAG, "Could not create document " + targetName);
                return false;
            }

            if (!copyFile(filePath, writeFile.getUri())) {
                writeFile.delete();
                return false;
            }

            final Context context = MimiApplication.getInstance().getApplicationContext();
            Toast.makeText(context, R.string.file_saved, Toast.LENGTH_LONG).show();
            if (showNotification) {
                final DocumentFile savedDocument = writeFile;
                MimiUtil.scaleBitmap(filePath)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new SingleObserver<Bitmap>() {
                            @Override
                            public void onSubscribe(Disposable d) {
                            }

                            @Override
                            public void onSuccess(Bitmap bitmap) {
                                showSaveNotification(context, bitmap, savedDocument, fileExt);
                            }

                            @Override
                            public void onError(Throwable e) {
                                Log.w(LOG_TAG, "Saved file without notification preview", e);
                            }
                        });
            }
            return true;

        } catch (final Exception e) {
            Log.e(LOG_TAG, "Error saving document", e);
            return false;
        }
    }

    private static void showSaveNotification(final Context context, final Bitmap bmp, final DocumentFile destPath, final String fileExt) {
        if (context == null) {
            return;
        }

        final String type;
        if (Utils.isVideoExtension(fileExt)) {
            type = "video/*";
        } else {
            type = "image/*";
        }

        final Uri uriToImage = destPath.getUri();

        try {
            final Intent contentIntent = new Intent();
            contentIntent.setAction(Intent.ACTION_VIEW);
            contentIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            contentIntent.setDataAndType(uriToImage, type);
            contentIntent.setClipData(ClipData.newRawUri(destPath.getName(), uriToImage));

            final PendingIntent pendingContentIntent = PendingIntent.getActivity(
                    context, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE);

            final Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.setDataAndType(uriToImage, type);
            shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uriToImage);
            shareIntent.setClipData(ClipData.newRawUri(destPath.getName(), uriToImage));

            final PendingIntent pendingShareIntent = PendingIntent.getActivity(
                    context, 0, shareIntent, PendingIntent.FLAG_IMMUTABLE);

            NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            final NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
            builder.setContentTitle(context.getString(R.string.file_saved));
            builder.setContentText(destPath.getName());
            builder.setSubText(MimiUtil.humanReadableByteCount(destPath.length(), true));
            builder.setSmallIcon(R.drawable.ic_notification_photo);
            builder.setLargeIcon(bmp);
            builder.setStyle(new NotificationCompat.BigPictureStyle()
                    .bigPicture(bmp));
            builder.setContentIntent(pendingContentIntent);
            builder.addAction(R.drawable.ic_notification_share, context.getString(R.string.share), pendingShareIntent);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String channelName = context.getString(R.string.mimi_file_downloader);

                NotificationChannel saveFileChannel = new NotificationChannel(NotificationUtils.DOWNLOADER_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW);

                builder.setChannelId(NotificationUtils.DOWNLOADER_CHANNEL_ID);
                notificationManager.createNotificationChannel(saveFileChannel);
            }

            final Notification saveFileNotification = builder.build();

            if (NotificationUtils.canPostNotifications(context)) {
                notificationManager.notify(NOTIFICATION_ID, saveFileNotification);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error creating notification", e);
        }
    }

    public static boolean copyFile(final File copyFrom, final Uri copyTo) {
        final Context context = MimiApplication.getInstance().getApplicationContext();
        if (context == null) {
            return false;
        }

        try (InputStream input = new FileInputStream(copyFrom);
             OutputStream output = context.getContentResolver().openOutputStream(copyTo, "w")) {
            if (output == null) {
                Log.e(LOG_TAG, "Could not open destination document for writing");
                return false;
            }

            final byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != EOF) {
                output.write(buffer, 0, count);
            }
            output.flush();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error copying file", e);
            return false;
        }

        return true;
    }
}
