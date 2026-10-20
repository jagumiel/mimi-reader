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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
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
    public static CaptchaDialog newInstance(String board, long thread) {
        CaptchaDialog dialog = new CaptchaDialog(); Bundle args = new Bundle();
        args.putString("board", PostingProtocol.board(board)); args.putLong("thread", Math.max(0, thread));
        dialog.setArguments(args); return dialog;
    }
    @NonNull @Override public Dialog onCreateDialog(Bundle savedInstanceState) {
        LinearLayout content = new LinearLayout(requireContext()); content.setOrientation(LinearLayout.VERTICAL);
        // AlertDialog measures its custom panel with wrap-content. A zero-height
        // weighted WebView otherwise remains invisible even in a tall window.
        content.setMinimumHeight((int) (getResources().getDisplayMetrics().heightPixels * 0.55));
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        status = new TextView(requireContext()); status.setText(R.string.captcha_instructions); content.addView(status);
        webView = createCaptchaWebView(); webView.setId(R.id.captch_webview);
        content.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        Button browser = new Button(requireContext()); browser.setText(R.string.captcha_open_browser);
        browser.setOnClickListener(view -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PostingProtocol.browserUrl(board(), thread())))); }
            catch (android.content.ActivityNotFoundException error) { status.setText(R.string.error_opening_url); }
        }); content.addView(browser);
        webView.loadCaptchaPage(board(), thread());
        return new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.captcha_title).setView(content)
                .setPositiveButton(R.string.captcha_confirm_send, null).setNeutralButton(R.string.captcha_reload, null)
                .setNegativeButton(android.R.string.cancel, null).create();
    }
    @Override public void onStart() {
        super.onStart(); AlertDialog dialog = (AlertDialog) requireDialog();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (int) (getResources().getDisplayMetrics().heightPixels * 0.85));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> { status.setText(R.string.captcha_instructions); webView.loadCaptchaPage(board(), thread()); });
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> webView.readAnswer(raw -> {
            if (!isAdded() || getParentFragmentManager().isStateSaved()) return;
            try {
                PostingProtocol.Answer answer = PostingProtocol.answerFromWebView(raw, board(), thread(), System.currentTimeMillis());
                Bundle result = new Bundle(); result.putString("challenge", answer.challenge); result.putString("response", answer.response); result.putLong("expiresAt", answer.expiresAt);
                getParentFragmentManager().setFragmentResult(RESULT, result); dismiss();
            } catch (Exception error) { status.setText(R.string.captcha_not_ready); }
        }));
    }
    private String board() { return requireArguments().getString("board"); }
    protected CaptchaWebView createCaptchaWebView() { return new CaptchaWebView(requireContext()); }
    private long thread() { return requireArguments().getLong("thread"); }
    @Override public void onDestroyView() {
        if (webView != null) {
            webView.stopLoading();
            if (webView.getParent() instanceof ViewGroup) ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy(); webView = null;
        }
        super.onDestroyView();
    }
}
