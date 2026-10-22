package com.emogoth.android.phone.mimi.util;

import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.emogoth.android.phone.mimi.R;

/** Keeps attachments without server thumbnails visible and clickable in every media list. */
public final class MediaThumbnail {
    private MediaThumbnail() {}

    public static void load(ImageView view, String url, String extension) {
        boolean flash = ".swf".equals(MediaContract.normalizedExtension(extension));
        int fallback = flash ? R.drawable.thumbnail_flash : R.drawable.placeholder_image;
        Object model = flash && (url == null || url.isEmpty()) ? fallback : url;
        Glide.with(view).load(model).placeholder(fallback).error(fallback).into(view);
    }
}
