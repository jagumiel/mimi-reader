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

package com.emogoth.android.phone.mimi.dialog;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.fourchan.PostingProtocol;
import com.emogoth.android.phone.mimi.view.CaptchaWebView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class CaptchaDialog extends DialogFragment {
    public static final String RESULT = "mimi_captcha_answer";
    private CaptchaWebView webView;
    private TextView status;
    private final android.os.Handler statusHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable statusCheck = this::checkStatus;
    private boolean statusActive;
    private boolean confirming;
    private int generation;
    private String lastStatus;
    private View actionBar;
    private final View.OnLayoutChangeListener actionLayoutListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> reserveActionSpace(view);
    public static CaptchaDialog newInstance(String board, long thread) {
        CaptchaDialog dialog = new CaptchaDialog(); Bundle args = new Bundle();
        args.putString("board", PostingProtocol.board(board)); args.putLong("thread", Math.max(0, thread));
        dialog.setArguments(args); return dialog;
    }
    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        LinearLayout content = new LinearLayout(requireContext()); content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        status = new TextView(requireContext()); status.setText(R.string.captcha_instructions); content.addView(status);
        webView = createCaptchaWebView(); webView.setId(R.id.captch_webview);
        // A finite height is important: a zero-height weighted WebView collapses
        // in AlertDialog's wrap-content panel, while a minimum on the entire
        // panel pushes the action buttons out of small/IME-resized windows.
        content.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (240 * getResources().getDisplayMetrics().density)));
        Button browser = new Button(requireContext()); browser.setId(R.id.captcha_open_browser);
        browser.setText(R.string.captcha_open_browser);
        browser.setOnClickListener(view -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PostingProtocol.browserUrl(board(), thread())))); }
            catch (android.content.ActivityNotFoundException error) { status.setText(R.string.error_opening_url); }
        }); content.addView(browser);
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setId(R.id.captcha_content_scroll);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        webView.loadCaptchaPage(board(), thread());
        return new MaterialAlertDialogBuilder(requireContext())
                // Material's default 80 dp top/bottom insets on tall displays
                // persist even when an IME makes the actual window short.
                .setBackgroundInsetTop(padding).setBackgroundInsetBottom(padding)
                .setTitle(R.string.captcha_title).setView(scroll)
                .setPositiveButton(R.string.captcha_send_message, null).setNeutralButton(R.string.captcha_reload, null)
                .setNegativeButton(android.R.string.cancel, null).create();
    }
    @Override public void onStart() {
        super.onStart(); AlertDialog dialog = (AlertDialog) requireDialog();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        // The window manager bounds this to the available screen, including IME
        // resize. Only the custom body scrolls; the native actions stay visible.
        dialog.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        // Material wraps its stacking ButtonBarLayout in a ScrollView. Its
        // default minimum reserves only one action plus a peek at the next.
        // Reserve the entire measured bar instead: scrolling belongs to the
        // challenge body, not to the Send / Cancel / Reload actions.
        actionBar = (View) dialog.getButton(AlertDialog.BUTTON_POSITIVE).getParent();
        actionBar.addOnLayoutChangeListener(actionLayoutListener);
        reserveActionSpace(actionBar);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
            generation++;
            lastStatus = null;
            confirming = false;
            status.setText(R.string.captcha_instructions);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            webView.loadCaptchaPage(board(), thread());
            statusHandler.removeCallbacks(statusCheck);
            statusHandler.post(statusCheck);
        });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (confirming) return;
            confirming = true;
            final int attemptGeneration = generation;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            webView.readAnswer(raw -> {
                if (!statusActive || attemptGeneration != generation || !isAdded() || getParentFragmentManager().isStateSaved()) return;
                try {
                    PostingProtocol.Answer answer = PostingProtocol.answerFromWebView(raw, board(), thread(), System.currentTimeMillis());
                    Bundle result = new Bundle();
                    result.putString("challenge", answer.challenge);
                    result.putString("response", answer.response);
                    result.putLong("expiresAt", answer.expiresAt);
                    getParentFragmentManager().setFragmentResult(RESULT, result);
                    dismiss();
                } catch (Exception error) {
                    confirming = false;
                    status.setText(R.string.captcha_not_ready);
                    statusHandler.removeCallbacks(statusCheck);
                    statusHandler.post(statusCheck);
                }
            });
        });
        confirming = false;
        statusActive = true;
        statusHandler.removeCallbacks(statusCheck);
        statusHandler.post(statusCheck);
    }

    private void reserveActionSpace(View actions) {
        if (actions.getMeasuredHeight() == 0 || !(actions.getParent() instanceof ScrollView)) return;
        ScrollView actionPanel = (ScrollView) actions.getParent();
        int requiredHeight = actions.getMeasuredHeight() + actionPanel.getPaddingTop() + actionPanel.getPaddingBottom();
        if (actionPanel.getMinimumHeight() != requiredHeight) {
            actionPanel.setMinimumHeight(requiredHeight);
            // A minimum changed during layout needs another measure outside
            // that pass; post only when it changes, never on every frame.
            actionPanel.post(actionPanel::requestLayout);
        }
    }

    private void checkStatus() {
        if (!statusActive || confirming || webView == null || !isAdded()) return;
        final int checkedGeneration = generation;
        webView.readStatus(raw -> {
            if (!statusActive || checkedGeneration != generation || !isAdded() || confirming) return;
            boolean ready = "\"ready\"".equals(raw);
            AlertDialog dialog = (AlertDialog) getDialog();
            if (dialog == null) return;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(ready);
            if (!java.util.Objects.equals(raw, lastStatus)) {
                lastStatus = raw;
                status.setText(ready ? R.string.captcha_ready_to_send
                        : "\"expired\"".equals(raw) ? R.string.captcha_not_ready : R.string.captcha_instructions);
            }
            statusHandler.postDelayed(statusCheck, 500);
        });
    }

    @Override public void onStop() {
        statusActive = false;
        generation++;
        statusHandler.removeCallbacks(statusCheck);
        if (actionBar != null) actionBar.removeOnLayoutChangeListener(actionLayoutListener);
        actionBar = null;
        super.onStop();
    }
    private String board() { return requireArguments().getString("board"); }
    protected CaptchaWebView createCaptchaWebView() { return new CaptchaWebView(requireContext()); }
    private long thread() { return requireArguments().getLong("thread"); }
    @Override public void onDestroyView() {
        statusActive = false;
        statusHandler.removeCallbacks(statusCheck);
        if (webView != null) {
            webView.stopLoading();
            if (webView.getParent() instanceof ViewGroup) ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy(); webView = null;
        }
        super.onDestroyView();
    }
}
