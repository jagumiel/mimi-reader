package com.emogoth.android.phone.mimi.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VideoPlaybackErrorTest {

    @Test
    public void classifiesUnsupportedCodecErrors() {
        assertEquals(VideoPlaybackError.Kind.UNSUPPORTED_FORMAT,
                VideoPlaybackError.classify("ERROR_CODE_DECODING_FORMAT_UNSUPPORTED"));
        assertEquals(VideoPlaybackError.Kind.UNSUPPORTED_FORMAT,
                VideoPlaybackError.classify("ERROR_CODE_DECODER_INIT_FAILED"));
    }

    @Test
    public void classifiesMalformedAndIncompleteFiles() {
        assertEquals(VideoPlaybackError.Kind.CORRUPT_OR_INCOMPLETE,
                VideoPlaybackError.classify("ERROR_CODE_PARSING_CONTAINER_MALFORMED"));
        assertEquals(VideoPlaybackError.Kind.CORRUPT_OR_INCOMPLETE,
                VideoPlaybackError.classify("ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED"));
        assertEquals(VideoPlaybackError.Kind.CORRUPT_OR_INCOMPLETE,
                VideoPlaybackError.classify("ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE"));
    }

    @Test
    public void unknownErrorsRemainGeneric() {
        assertEquals(VideoPlaybackError.Kind.GENERIC,
                VideoPlaybackError.classify("ERROR_CODE_UNSPECIFIED"));
        assertEquals(VideoPlaybackError.Kind.GENERIC, VideoPlaybackError.classify(null));
    }
}
