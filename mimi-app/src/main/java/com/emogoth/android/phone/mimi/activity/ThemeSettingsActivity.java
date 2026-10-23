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
package com.emogoth.android.phone.mimi.activity;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import androidx.appcompat.widget.Toolbar;
import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.adapter.ThreadListAdapter;
import com.emogoth.android.phone.mimi.dialog.ThemeColorPickerDialog;
import com.emogoth.android.phone.mimi.fourchan.FourChanCommentParser;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import com.emogoth.android.phone.mimi.view.ColorImageView;
import java.util.ArrayList;
import java.util.Collections;

public class ThemeSettingsActivity extends MimiActivity {
    private static final String SELECTED_SLOT = "theme_color_slot";
    private static final String DEMO_COMMENT = "<a href=\"#p12345678\" class=\"quotelink\">&gt;&gt;12345678</a><br><a href=\"#p87654321\" class=\"quotelink\">&gt;&gt;87654321</a><br><a href=\"#p88765432\" class=\"quotelink\">&gt;&gt;88765432</a><br><span class=\"quote\">&gt;implying the above post is highlighted</span><br><span class=\"quote\">&gt;second line</span><br><br>This text color cannot be changed. It is set by the theme. I'm including it here so it looks more like a real post. This should be enough text to properly demonstrate how the colors will look in a post.<br>";
    private int selectedItem;
    private ColorImageView selectedColorView;
    private ThreadListAdapter.ViewHolder preview;

    @Override protected void onCreate(Bundle savedInstanceState) {
        setTheme(MimiUtil.getInstance().getThemeResourceId());
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            selectedItem = Math.max(0, Math.min(3, savedInstanceState.getInt(SELECTED_SLOT, 0)));
        }
        setContentView(R.layout.activity_theme_settings);
        Toolbar toolbar = findViewById(R.id.mimi_toolbar);
        toolbar.setLogo(null);
        toolbar.setTitle(R.string.settings);
        toolbar.setNavigationIcon(R.drawable.ic_nav_arrow_back);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        setToolbar(toolbar);

        ViewGroup container = findViewById(R.id.container);
        View postView = LayoutInflater.from(this).inflate(R.layout.thread_post_item, container, false);
        preview = new ThreadListAdapter.ThreadPostViewHolder(postView);
        preview.userName.setText("Anonymous");
        if (preview.flagIcon != null) preview.flagIcon.setVisibility(View.GONE);
        preview.thumbnailContainer.setVisibility(View.GONE);
        preview.threadId.setText("83736278");
        // A visual sample must not navigate to fabricated posts or external sites.
        preview.comment.setMovementMethod(null);
        preview.comment.setLinksClickable(false);
        container.addView(postView);

        selectedColorView = findViewById(R.id.current_color);
        selectedColorView.setOnClickListener(v -> {
            if (!getSupportFragmentManager().isStateSaved() &&
                    getSupportFragmentManager().findFragmentByTag(ThemeColorPickerDialog.TAG) == null) {
                ThemeColorPickerDialog.newInstance(selectedItem, getSelectedColor(selectedItem))
                        .showNow(getSupportFragmentManager(), ThemeColorPickerDialog.TAG);
            }
        });
        getSupportFragmentManager().setFragmentResultListener(ThemeColorPickerDialog.RESULT, this, (key, result) -> {
            int slot = result.getInt(ThemeColorPickerDialog.SLOT, -1);
            if (slot >= 0 && slot <= 3 && result.containsKey(ThemeColorPickerDialog.COLOR)) {
                applyColor(slot, result.getInt(ThemeColorPickerDialog.COLOR));
            }
        });
        for (int id : new int[]{R.id.default_reply, R.id.default_highlight, R.id.default_quote,
                R.id.default_link, R.id.material_blue, R.id.material_red, R.id.material_green,
                R.id.material_yellow, R.id.material_light_grey, R.id.material_dark_grey}) {
            ColorImageView swatch = findViewById(id);
            swatch.setOnClickListener(v -> applyColor(selectedItem, swatch.getBackgroundColor()));
        }
        Spinner spinner = findViewById(R.id.color_type_spinner);
        spinner.setAdapter(ArrayAdapter.createFromResource(this, R.array.color_types_list, android.R.layout.simple_spinner_item));
        spinner.setSelection(selectedItem, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedItem = position;
                selectedColorView.setBackgroundColor(getSelectedColor(position));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { spinner.setSelection(0); }
        });
    }

    private void applyColor(int slot, int color) {
        setSelectedColor(slot, color);
        selectedColorView.setBackgroundColor(getSelectedColor(selectedItem));
        updateDemoView();
    }

    private void updateDemoView() {
        FourChanCommentParser.Builder builder = new FourChanCommentParser.Builder();
        builder.setContext(this).setBoardName("").setReplies(new ArrayList<>())
                .setComment(DEMO_COMMENT + getString(R.string.project_repository_url))
                .setThreadId(12345678L).setHighlightedPosts(Collections.singletonList(88765432L))
                .setQuoteColor(getSelectedColor(0)).setReplyColor(getSelectedColor(1))
                .setHighlightColor(getSelectedColor(2)).setLinkColor(getSelectedColor(3)).setDemoMode(true);
        preview.postTime.setText(DateUtils.getRelativeTimeSpanString(System.currentTimeMillis() - 60000,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));
        // The former Rx map already ran on main. This small fixed local sample
        // needs no scheduled work, retained callbacks or stop-time cancellation.
        preview.comment.setText(builder.build().parse());
    }

    protected int getSelectedColor(int slot) {
        MimiUtil util = MimiUtil.getInstance();
        switch (slot) {
            case 0: return util.getQuoteColor();
            case 1: return util.getReplyColor();
            case 2: return util.getHighlightColor();
            case 3: return util.getLinkColor();
            default: throw new IllegalArgumentException("Unknown color slot");
        }
    }

    protected void setSelectedColor(int slot, int color) {
        MimiUtil util = MimiUtil.getInstance();
        switch (slot) {
            case 0: util.setQuoteColor(color); break;
            case 1: util.setReplyColor(color); break;
            case 2: util.setHighlightColor(color); break;
            case 3: util.setLinkColor(color); break;
            default: throw new IllegalArgumentException("Unknown color slot");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        selectedColorView.setBackgroundColor(getSelectedColor(selectedItem));
        updateDemoView();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt(SELECTED_SLOT, selectedItem);
        super.onSaveInstanceState(state);
    }

    @Override protected String getPageName() { return ThemeSettingsActivity.class.getSimpleName(); }
}
