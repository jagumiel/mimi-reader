package com.emogoth.android.phone.mimi.activity;

import com.emogoth.android.phone.mimi.R;

/** Debug-only lifecycle fixture. Never writes MimiUtil or the user's default preferences. */
public class ThemeSettingsTestActivity extends ThemeSettingsActivity {
    public static final String PREFERENCES = "theme-editor-instrumentation";

    private String key(int slot) {
        return getString(new int[]{R.string.quote_color_pref, R.string.reply_color_pref,
                R.string.highlight_color_pref, R.string.link_color_pref}[slot]);
    }

    @Override protected int getSelectedColor(int slot) {
        return getSharedPreferences(PREFERENCES, MODE_PRIVATE).getInt(key(slot), 0xff000000);
    }

    @Override protected void setSelectedColor(int slot, int color) {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putInt(key(slot), color).apply();
    }
}
