package com.emogoth.android.phone.mimi.util;

import android.content.Context;
import android.net.Uri;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayList;

public class MediaPlayerHelper implements Player.Listener {

    private final ExoPlayer player;
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private Uri currentVideo;
    private TextureView currentTextureView;
    private boolean released;

    public MediaPlayerHelper(Context context) {
        player = new ExoPlayer.Builder(context).build();
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.addListener(this);
    }

    public void playVideo(Uri videoUrl, TextureView view, boolean forceReload) {
        if (released) {
            return;
        }

        if (currentTextureView != view) {
            if (currentTextureView != null) {
                player.clearVideoTextureView(currentTextureView);
            }
            player.setVideoTextureView(view);
            currentTextureView = view;
        }

        if (forceReload || !videoUrl.equals(currentVideo)) {
            currentVideo = videoUrl;
            player.setMediaItem(MediaItem.fromUri(videoUrl));
            player.prepare();
        }
        player.play();
    }

    public void detachVideo(TextureView view) {
        if (released || currentTextureView != view) {
            return;
        }
        player.pause();
        player.clearVideoTextureView(view);
        currentTextureView = null;
    }

    public boolean ownsVideo(Uri videoUrl, TextureView view) {
        return !released && videoUrl.equals(currentVideo) && currentTextureView == view;
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        player.setPlayWhenReady(playWhenReady);
    }

    public void seekTo(long positionMs) {
        player.seekTo(positionMs);
    }

    public void release() {
        if (released) {
            return;
        }
        listeners.clear();
        currentTextureView = null;
        currentVideo = null;
        player.release();
        released = true;
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void clearListeners() {
        listeners.clear();
    }

    public void start() {
        if (!released) {
            player.play();
        }
    }

    public void pause() {
        if (!released) {
            player.pause();
        }
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public void mute(boolean muted) {
        player.setVolume(muted ? 0 : 1);
    }

    public long getDuration() {
        return player.getDuration();
    }

    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }

    public boolean isMuted() {
        return player.getVolume() == 0;
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                || events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            for (Listener listener : new ArrayList<>(listeners)) {
                listener.onStateChanged(player.getPlayWhenReady(), player.getPlaybackState());
            }
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onPlaybackError(error);
        }
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onVideoSizeChanged(videoSize.width, videoSize.height,
                    videoSize.unappliedRotationDegrees, videoSize.pixelWidthHeightRatio);
        }
    }

    @Override
    public void onRenderedFirstFrame() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onRenderedFirstFrame();
        }
    }

    public interface Listener {
        void onStateChanged(boolean playWhenReady, int playbackState);

        void onPlaybackError(PlaybackException error);

        void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                                float pixelWidthHeightRatio);

        void onRenderedFirstFrame();
    }
}
