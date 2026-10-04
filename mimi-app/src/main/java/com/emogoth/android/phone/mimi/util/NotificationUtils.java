package com.emogoth.android.phone.mimi.util;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.emogoth.android.phone.mimi.R;

public final class NotificationUtils {
    public static final String DOWNLOADER_CHANNEL_ID = "mimi_file_downloader";

    private NotificationUtils() {
    }

    public static boolean canPostNotifications(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void ensureDownloadChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(new NotificationChannel(
                        DOWNLOADER_CHANNEL_ID,
                        context.getString(R.string.mimi_file_downloader),
                        NotificationManager.IMPORTANCE_LOW));
            }
        }
    }
}
