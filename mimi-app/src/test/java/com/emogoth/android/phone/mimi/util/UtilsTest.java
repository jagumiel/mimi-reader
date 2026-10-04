package com.emogoth.android.phone.mimi.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UtilsTest {

    @Test
    public void recognizesSupportedVideoExtensions() {
        assertTrue(Utils.isVideoExtension(".webm"));
        assertTrue(Utils.isVideoExtension("webm"));
        assertTrue(Utils.isVideoExtension(".mp4"));
        assertTrue(Utils.isVideoExtension("MP4"));
    }

    @Test
    public void doesNotClassifyImagesOrMissingExtensionsAsVideo() {
        assertFalse(Utils.isVideoExtension(".gif"));
        assertFalse(Utils.isVideoExtension(".jpg"));
        assertFalse(Utils.isVideoExtension(null));
    }

    @Test
    public void normalizesExtensionForMimeLookup() {
        assertEquals("mp4", Utils.normalizeExtension(" .MP4 "));
        assertEquals("webm", Utils.normalizeExtension("..WEBM"));
    }
}
