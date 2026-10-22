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

package com.emogoth.android.phone.mimi.span;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.View;
import android.widget.Toast;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.activity.MimiActivity;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import com.emogoth.android.phone.mimi.util.BrowserNavigator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;


public class LinkSpan extends LongClickableSpan {

    private final int linkColor;
    private String link;

    public LinkSpan(String link, int linkColor) {
        this.link = link;
        this.linkColor = linkColor;
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        super.updateDrawState(ds);
        ds.setUnderlineText(true);
        ds.setColor(linkColor);
    }

    @Override
    public void onClick(View v) {
        boolean inCustomTab = !MimiUtil.openLinksExternally(v.getContext());
        openLink(v.getContext(), inCustomTab);
    }

    @Override
    public boolean onLongClick(View v) {
        showChoiceDialog(v.getContext());
        return true;
    }

    private void showChoiceDialog(final Context context) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                new MaterialAlertDialogBuilder(context)
                        .setTitle(link)
                        .setItems(R.array.link_dialog_list, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    openLink(context, false);
                                } else if (which == 1) {
                                    shareLink(context);
                                } else {
                                    ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                                    clipboardManager.setPrimaryClip(ClipData.newPlainText("url_link", link));
                                    Toast.makeText(context, R.string.link_copied_to_clipboard, Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setCancelable(true)
                        .show()
                        .setCanceledOnTouchOutside(true);
            }
        });
    }

    private void openLink(final Context context, boolean inCustomTab) {
        Integer toolbarColor = null;
        if (context instanceof MimiActivity) {
            MimiActivity activity = (MimiActivity) context;
            if (activity.getToolbar() != null && activity.getToolbar().getBackground() instanceof ColorDrawable) {
                toolbarColor = ((ColorDrawable) activity.getToolbar().getBackground()).getColor();
            }
        }
        BrowserNavigator.open(context, link, inCustomTab, toolbarColor);
    }

    private void shareLink(Context context) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_TEXT, link);
        shareIntent.setType("text/plain");
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share)));
    }
}
