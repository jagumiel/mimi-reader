package com.emogoth.android.phone.mimi.util;

import android.content.Context;

import com.bumptech.glide.Glide;

import java.io.File;
import java.io.IOException;

/** Clears disposable app data without deleting persistent data or live provider infrastructure. */
public final class AppCacheCleaner {
    private AppCacheCleaner() {
    }

    /** Must run off the main thread because Glide performs blocking disk I/O. */
    public static long clear(Context context) throws IOException {
        final Context appContext = context.getApplicationContext();
        final File internalCache = appContext.getCacheDir();
        final File externalCache = appContext.getExternalCacheDir();
        final long sizeBefore = directorySize(internalCache) + directorySize(externalCache);

        IOException failure = null;
        try {
            HttpClientFactory.getInstance().clearCache();
        } catch (IOException exception) {
            failure = exception;
        }

        try {
            Glide.get(appContext).clearDiskCache();
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, new IOException("Could not clear the image cache", exception));
        }

        final File liveHttpCache = new File(internalCache, HttpClientFactory.HTTP_CACHE_DIRECTORY);
        try {
            deleteChildren(internalCache, liveHttpCache, true);
            deleteChildren(externalCache, null, false);
        } catch (IOException exception) {
            failure = appendFailure(failure, exception);
        }

        if (failure != null) {
            throw failure;
        }

        final long sizeAfter = directorySize(internalCache) + directorySize(externalCache);
        return Math.max(0L, sizeBefore - sizeAfter);
    }

    static long directorySize(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        if (file.isFile()) {
            return file.length();
        }

        long total = 0L;
        final File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                total += directorySize(child);
            }
        }
        return total;
    }

    private static void deleteChildren(File directory, File protectedDirectory,
                                       boolean preserveWebView) throws IOException {
        if (directory == null || !directory.exists()) {
            return;
        }
        final File[] children = directory.listFiles();
        if (children == null) {
            return;
        }

        final String protectedPath = protectedDirectory == null
                ? null
                : protectedDirectory.getCanonicalPath();
        for (File child : children) {
            // Chromium owns these live paths, including suffix-specific WebView profiles.
            // Removing them behind the provider can crash a later renderer in the same process.
            if (preserveWebView && (child.getName().equals("WebView") || child.getName().startsWith("WebView_"))) {
                continue;
            }
            if (protectedPath != null && protectedPath.equals(child.getCanonicalPath())) {
                continue;
            }
            deleteRecursively(child);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            final File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not delete cache entry: " + file.getAbsolutePath());
        }
    }

    private static IOException appendFailure(IOException current, IOException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }
}
