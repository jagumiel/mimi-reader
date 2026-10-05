package com.emogoth.android.phone.mimi.viewmodel;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GalleryAudioStateTest {
    @Test
    public void startsMutedWhenAudioIsNotEnabledByDefault() {
        assertTrue(new GalleryAudioState(false).getMuted());
    }

    @Test
    public void manualChoicesApplyToFollowingVideos() {
        final GalleryAudioState state = new GalleryAudioState(false);

        state.setMuted(false);
        assertFalse(state.getMuted());

        state.setMuted(true);
        assertTrue(state.getMuted());
    }

    @Test
    public void respectsTheExistingAutoPlayAudioPreference() {
        assertFalse(new GalleryAudioState(true).getMuted());
    }
}
