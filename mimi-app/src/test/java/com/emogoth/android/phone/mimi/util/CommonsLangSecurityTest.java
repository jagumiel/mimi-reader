package com.emogoth.android.phone.mimi.util;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import static org.junit.Assert.assertThrows;

/** Patched non-recursive lookup; these calls are not used by production Mimi code. */
public class CommonsLangSecurityTest {
    @Test(timeout = 5000) public void oversizedClassNamesAndArrayDimensionsFailWithoutRecursion() {
        assertThrows(ClassNotFoundException.class,
                () -> ClassUtils.getClass(StringUtils.repeat('x', 65_536)));
        assertThrows(ClassNotFoundException.class,
                () -> ClassUtils.getClass("java.lang.String" + StringUtils.repeat("[]", 10_000)));
        assertThrows(ClassNotFoundException.class,
                () -> ClassUtils.getClass(StringUtils.repeat("missing.", 512) + "MissingClass"));
    }
}
