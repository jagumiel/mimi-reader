package com.emogoth.android.phone.mimi.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;

import com.emogoth.android.phone.mimi.R;

/** User-initiated link navigation without retaining activities or binding browser services. */
public final class BrowserNavigator {
    private BrowserNavigator() { }

    public static boolean open(Context context, String link, boolean inCustomTab,
                               @Nullable Integer toolbarColor) {
        Uri uri = Uri.parse(link);
        boolean customTab = inCustomTab && isWebUri(uri);
        if (start(context, createIntent(context, uri, customTab, toolbarColor))) {
            return true;
        }
        if (customTab && start(context, createIntent(context, uri, false, null))) {
            return true;
        }
        Toast.makeText(context, R.string.error_opening_url, Toast.LENGTH_SHORT).show();
        return false;
    }

    static Intent createIntent(Context context, Uri uri, boolean inCustomTab,
                               @Nullable Integer toolbarColor) {
        Intent intent;
        if (inCustomTab && isWebUri(uri)) {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            if (toolbarColor != null) {
                builder.setToolbarColor(toolbarColor);
            }
            intent = builder.build().intent;
            intent.setData(uri);
        } else {
            intent = new Intent(Intent.ACTION_VIEW, uri);
        }
        if (!hasActivityContext(context)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    private static boolean isWebUri(Uri uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
    }

    private static boolean hasActivityContext(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return true;
            }
            Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current) {
                break;
            }
            current = base;
        }
        return false;
    }

    private static boolean start(Context context, Intent intent) {
        try {
            // Do not query installed packages: visibility restrictions must not hide a handler.
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            return false;
        }
    }
}
