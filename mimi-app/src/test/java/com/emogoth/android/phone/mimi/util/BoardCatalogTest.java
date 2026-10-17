package com.emogoth.android.phone.mimi.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BoardCatalogTest {
    @Test
    public void containsTheCompleteApiCatalog() {
        final Set<String> expected = new HashSet<>(Arrays.asList(
                "a", "b", "c", "d", "e", "f", "g", "gif", "h", "hr", "k",
                "m", "o", "p", "s", "t", "u", "v", "vg", "vm", "vmg", "vr",
                "vrpg", "vst", "w", "wg", "i", "ic", "r9k", "s4s", "vip", "cm",
                "hm", "lgbt", "y", "3", "aco", "adv", "an", "bant", "biz", "cgl",
                "ck", "co", "diy", "fa", "fit", "gd", "hc", "his", "int", "jp",
                "lit", "mlp", "mu", "n", "news", "out", "po", "pol", "pw", "qst",
                "sci", "soc", "sp", "tg", "toy", "trv", "tv", "vp", "vt", "wsg",
                "wsr", "x", "xs", "r", "trash"));

        assertEquals(77, expected.size());
        assertEquals(expected, BoardCatalog.listedBoardNames());
    }

    @Test
    public void followsTheOfficialHomePageThemes() {
        assertEquals(BoardCatalog.JAPANESE_CULTURE, BoardCatalog.categoryFor("a"));
        assertEquals(BoardCatalog.VIDEO_GAMES, BoardCatalog.categoryFor("vrpg"));
        assertEquals(BoardCatalog.INTERESTS, BoardCatalog.categoryFor("sci"));
        assertEquals(BoardCatalog.CREATIVE, BoardCatalog.categoryFor("wsg"));
        assertEquals(BoardCatalog.OTHER, BoardCatalog.categoryFor("news"));
        assertEquals(BoardCatalog.MISC, BoardCatalog.categoryFor("bant"));
        assertEquals(BoardCatalog.MISC, BoardCatalog.categoryFor("trash"));
        assertEquals(BoardCatalog.ADULT, BoardCatalog.categoryFor("gif"));
        assertEquals(BoardCatalog.ADULT, BoardCatalog.categoryFor("r"));
    }

    @Test
    public void includesApiOnlyAndFutureBoards() {
        assertTrue(BoardCatalog.isPublicBoard("r"));
        assertTrue(BoardCatalog.isPublicBoard("trash"));
        assertTrue(BoardCatalog.isPublicBoard("future"));
        assertEquals(BoardCatalog.OTHER, BoardCatalog.categoryFor("future"));
    }
}
