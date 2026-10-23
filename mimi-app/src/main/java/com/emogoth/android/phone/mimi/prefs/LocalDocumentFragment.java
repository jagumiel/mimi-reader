/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package com.emogoth.android.phone.mimi.prefs;

import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.util.BrowserNavigator;
import com.emogoth.android.phone.mimi.view.MimiWebView;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/** View-scoped, offline rendering of bundled HTML; never a browser or a posting WebView. */
public abstract class LocalDocumentFragment extends DialogFragment {
    protected static final String RAW_RES_EXTRA = "raw_res_extra";
    protected static final String TITLE_EXTRA = "title_extra";
    protected static final String DOCUMENT_TAG = "nz.net.speakman.androidlicensespage.LicensesFragment";
    static final String DOCUMENT_URL = "https://appassets.androidplatform.net/local-document/";

    private Disposable documentLoad;
    private int generation;
    private ViewGroup documentRoot;
    private volatile WebView documentView;
    private volatile byte[] documentHtml;
    private ProgressBar progress;
    private View errorPanel;

    protected static Bundle documentArguments(@RawRes int resource, @Nullable String title) {
        Bundle args = new Bundle();
        args.putInt(RAW_RES_EXTRA, resource);
        if (title != null) args.putString(TITLE_EXTRA, title);
        return args;
    }

    protected int documentResource() {
        return getArguments() == null ? R.raw.licenses : getArguments().getInt(RAW_RES_EXTRA, R.raw.licenses);
    }

    protected String documentTitle() {
        String title = getArguments() == null ? null : getArguments().getString(TITLE_EXTRA);
        return title == null || title.isEmpty() ? getString(R.string.local_document_licenses_title) : title;
    }

    public static void showDocument(@Nullable FragmentManager manager,
                                    LocalDocumentFragment fragment, String tag) {
        if (manager == null || manager.isStateSaved() || manager.findFragmentByTag(tag) != null) return;
        fragment.show(manager.beginTransaction().addToBackStack(null), tag);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        requireDialog().setTitle(documentTitle());
        return inflater.inflate(R.layout.licenses_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        documentRoot = (ViewGroup) view;
        documentView = view.findViewById(R.id.licensesFragmentWebView);
        progress = view.findViewById(R.id.licensesFragmentIndeterminateProgress);
        errorPanel = view.findViewById(R.id.local_document_error);
        view.findViewById(R.id.local_document_retry).setOnClickListener(ignored -> loadDocument());
        configureDocumentView();
        loadDocument();
    }

    protected String readDocument(Resources resources, @RawRes int resource) throws IOException {
        if (!"raw".equals(resources.getResourceTypeName(resource))) {
            throw new Resources.NotFoundException("Expected a bundled raw document");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resources.openRawResource(resource), StandardCharsets.UTF_8))) {
            StringBuilder html = new StringBuilder();
            char[] buffer = new char[4096];
            int length;
            while ((length = reader.read(buffer)) != -1) html.append(buffer, 0, length);
            return html.toString();
        }
    }

    private void loadDocument() {
        cancelLoad();
        final int currentGeneration = generation;
        if (documentView == null) {
            documentView = new MimiWebView(requireContext());
            documentView.setId(R.id.licensesFragmentWebView);
            documentRoot.addView(documentView, 0, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            configureDocumentView();
        }
        errorPanel.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        documentView.setVisibility(View.INVISIBLE);
        final Resources resources = requireContext().getApplicationContext().getResources();
        final int resource = documentResource();
        documentLoad = Single.fromCallable(() -> readDocument(resources, resource))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(html -> {
                    if (generation == currentGeneration && documentView != null) {
                        documentHtml = html.getBytes(StandardCharsets.UTF_8);
                        documentView.loadUrl(DOCUMENT_URL);
                    }
                }, error -> {
                    if (generation == currentGeneration && documentView != null) showDocumentError();
                });
    }

    @SuppressWarnings("deprecation")
    private void configureDocumentView() {
        WebSettings settings = documentView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBlockNetworkLoads(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        documentView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.isForMainFrame() || navigate(request.getUrl(), request.hasGesture());
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Pre-24 callback: with JavaScript disabled, only explicit links are handled here.
                return navigate(Uri.parse(url), true);
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                byte[] html = documentHtml;
                if (view == documentView && html != null && "GET".equals(request.getMethod()) &&
                        DOCUMENT_URL.equals(request.getUrl().buildUpon().fragment(null).build().toString())) {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Cache-Control", "no-store");
                    headers.put("X-Content-Type-Options", "nosniff");
                    headers.put("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; " +
                            "base-uri 'none'; form-action 'none'; frame-src 'none'");
                    return new WebResourceResponse("text/html", "UTF-8", 200, "OK", headers,
                            new ByteArrayInputStream(html));
                }
                return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", null,
                        new ByteArrayInputStream(new byte[0]));
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (view == documentView && errorPanel.getVisibility() != View.VISIBLE) {
                    progress.setVisibility(View.GONE);
                    view.setVisibility(View.VISIBLE);
                }
            }

            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (view == documentView) {
                    cancelLoad();
                    destroyDocumentView();
                    showDocumentError();
                }
                return true;
            }
        });
    }

    private boolean navigate(Uri uri, boolean userGesture) {
        if (documentRoot == null || !isAdded()) return true;
        String withoutFragment = uri.buildUpon().fragment(null).build().toString();
        if (DOCUMENT_URL.equals(withoutFragment) || "about:blank".equals(uri.toString())) return false;
        boolean web = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
        if (userGesture && web && uri.getHost() != null && uri.getUserInfo() == null) {
            BrowserNavigator.open(requireContext(), uri.toString(), false, null);
        }
        return true;
    }

    private void showDocumentError() {
        if (documentRoot == null) return;
        progress.setVisibility(View.GONE);
        if (documentView != null) documentView.setVisibility(View.INVISIBLE);
        errorPanel.setVisibility(View.VISIBLE);
    }

    private void cancelLoad() {
        generation++;
        documentHtml = null;
        if (documentLoad != null) documentLoad.dispose();
        documentLoad = null;
    }

    private void destroyDocumentView() {
        WebView oldView = documentView;
        documentView = null;
        if (oldView != null) {
            if (oldView.getParent() instanceof ViewGroup) ((ViewGroup) oldView.getParent()).removeView(oldView);
            oldView.destroy();
        }
    }

    @Override public void onDestroyView() {
        cancelLoad();
        destroyDocumentView();
        documentRoot = null;
        progress = null;
        errorPanel = null;
        super.onDestroyView();
    }
}
