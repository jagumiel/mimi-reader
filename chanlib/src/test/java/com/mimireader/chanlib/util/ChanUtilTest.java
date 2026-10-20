package com.mimireader.chanlib.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ChanUtilTest {
    @Test
    public void emptyNameProducesTranslucentBlack() {
        assertEquals(0x88000000, ChanUtil.calculateColorBase(""));
    }

    @Test
    public void asciiNameUsesItsHashAsRgb() {
        assertEquals(0x88000061, ChanUtil.calculateColorBase("a"));
    }

    @Test
    public void highHashBitsDoNotChangeTheAlphaChannel() {
        assertEquals(0x88e918d2, ChanUtil.calculateColorBase("hello"));
        assertEquals(0x88, ChanUtil.calculateColorBase("hello") >>> 24);
    }

    @Test
    public void negativeHashRetainsItsLow24Bits() {
        assertEquals(0x881fbdc0, ChanUtil.calculateColorBase("zzzzzz"));
    }

    @Test
    public void minimumIntegerHashDoesNotOverflowColorCalculation() {
        assertEquals(Integer.MIN_VALUE, "polygenelubricants".hashCode());
        assertEquals(0x88000000, ChanUtil.calculateColorBase("polygenelubricants"));
    }

    @Test
    public void hashCollisionsKeepTheirExistingSharedColor() {
        assertEquals(0x88000840, ChanUtil.calculateColorBase("Aa"));
        assertEquals(ChanUtil.calculateColorBase("Aa"), ChanUtil.calculateColorBase("BB"));
    }

    @Test
    public void supplementaryUnicodeUsesTheExistingUtf16Hash() {
        assertEquals(0x881b0d63, ChanUtil.calculateColorBase("\ud83d\ude00"));
    }

    @Test
    public void nullNameStillFailsInsteadOfSilentlyChoosingAColor() {
        assertThrows(NullPointerException.class, () -> ChanUtil.calculateColorBase(null));
    }
}
