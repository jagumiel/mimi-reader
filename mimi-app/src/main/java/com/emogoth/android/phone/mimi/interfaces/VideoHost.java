package com.emogoth.android.phone.mimi.interfaces;

import com.emogoth.android.phone.mimi.util.MediaPlayerHelper;

public interface VideoHost {
    MediaPlayerHelper getExoPlayerHelper();
    void clearExoPlayerHelper();
}
