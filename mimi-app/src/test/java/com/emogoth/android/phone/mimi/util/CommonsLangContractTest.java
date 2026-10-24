package com.emogoth.android.phone.mimi.util;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.*;

/** Freeze the two production StringUtils contracts before changing the dependency. */
public class CommonsLangContractTest {
    @Test public void containsIgnoreCasePreservesNullEmptyAndSequenceSemantics() {
        assertFalse(StringUtils.containsIgnoreCase(null, null));
        assertFalse(StringUtils.containsIgnoreCase(null, ""));
        assertFalse(StringUtils.containsIgnoreCase("sage", null));
        assertTrue(StringUtils.containsIgnoreCase("", ""));
        assertTrue(StringUtils.containsIgnoreCase("sage", ""));
        assertFalse(StringUtils.containsIgnoreCase("", "sage"));
        assertTrue(StringUtils.containsIgnoreCase(new StringBuilder("prefix SAGE suffix"), new StringBuffer("sage")));
        assertFalse(StringUtils.containsIgnoreCase("sage", "sages"));
    }

    @Test public void containsIgnoreCasePreservesUnicodeWithoutAccentOrExpansionFolding() {
        assertTrue(StringUtils.containsIgnoreCase("Álvaro", "álV"));
        assertFalse(StringUtils.containsIgnoreCase("Álvaro", "alv"));
        assertTrue(StringUtils.containsIgnoreCase("Σ", "ς"));
        assertTrue(StringUtils.containsIgnoreCase("İSTANBUL", "istanbul"));
        assertFalse(StringUtils.containsIgnoreCase("Straße", "STRASSE"));
        assertTrue(StringUtils.containsIgnoreCase("hello \uD83D\uDE00", "\uD83D\uDE00"));
    }

    @Test public void containsIgnoreCaseDoesNotDependOnDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            for (Locale locale : new Locale[]{Locale.ROOT, Locale.US, new Locale("tr", "TR")}) {
                Locale.setDefault(locale);
                assertTrue(StringUtils.containsIgnoreCase("TITLE", "title"));
                assertTrue(StringUtils.containsIgnoreCase("İSTANBUL", "istanbul"));
                assertTrue(StringUtils.containsIgnoreCase("I", "ı"));
            }
        } finally { Locale.setDefault(previous); }
    }

    @Test public void numericSpacePreservesNullEmptyAndAsciiSpaceSemantics() {
        assertFalse(StringUtils.isNumericSpace(null));
        assertTrue(StringUtils.isNumericSpace(""));
        assertTrue(StringUtils.isNumericSpace("   "));
        assertTrue(StringUtils.isNumericSpace(" 12 34 "));
        assertTrue(StringUtils.isNumericSpace(new StringBuilder("123")));
        for (String input : new String[]{"-1", "+1", "1.2", "12a", "1\t2", "1\n2", "1\u00A02", "1\u20032"}) {
            assertFalse(input, StringUtils.isNumericSpace(input));
        }
    }

    @Test public void numericSpacePreservesBmpDigitRulesAndRejectsSurrogateDigits() {
        assertTrue(StringUtils.isNumericSpace("١٢٣"));
        assertTrue(StringUtils.isNumericSpace("１２３"));
        assertTrue(StringUtils.isNumericSpace("१२३"));
        assertFalse(StringUtils.isNumericSpace("\uD835\uDFD9"));
        assertFalse(StringUtils.isNumericSpace("\uD83D\uDE00"));
    }

    @Test public void quoteTokenClassificationPreservesParserAssumptions() {
        for (String token : new String[]{"123", "١٢٣", "１２３"}) {
            assertTrue(StringUtils.isNumericSpace(token));
            assertEquals(123L, Long.parseLong(token));
        }
        assertFalse(StringUtils.isNumericSpace(">/g/123"));
        assertFalse(StringUtils.isNumericSpace("123abc"));
        // The production parser splits suffixes on ASCII space before classifying.
        String token = "123 (OP)".split(" ")[0];
        assertTrue(StringUtils.isNumericSpace(token));
        assertEquals(123L, Long.parseLong(token));
    }
}
