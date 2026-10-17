package com.emogoth.android.phone.mimi.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class UrlRouterTest {
    @Test
    public void matchesSupportedBoardCatalogAndThreadRoutes() {
        assertEquals(UrlRouter.SINGLE_BOARD,
                UrlRouter.match("boards.4chan.org", Collections.singletonList("g")));
        assertEquals(UrlRouter.CATALOG,
                UrlRouter.match("boards.4chan.org", Arrays.asList("g", "catalog")));
        assertEquals(UrlRouter.THREAD,
                UrlRouter.match("boards.4chan.org", Arrays.asList("g", "thread", "123")));
        assertEquals(UrlRouter.THREAD,
                UrlRouter.match("BOARDS.4CHAN.ORG",
                        Arrays.asList("g", "thread", "123", "thread-title")));
    }

    @Test
    public void rejectsOtherHostsAndMalformedRoutes() {
        assertEquals(UrlRouter.UNSET,
                UrlRouter.match("example.org", Collections.singletonList("g")));
        assertEquals(UrlRouter.UNSET,
                UrlRouter.match("boards.4chan.org", Collections.<String>emptyList()));
        assertEquals(UrlRouter.UNSET,
                UrlRouter.match("boards.4chan.org", Arrays.asList("g", "thread", "abc")));
        assertEquals(UrlRouter.UNSET,
                UrlRouter.match("boards.4chan.org", Arrays.asList("g", "thread", "0")));
        assertEquals(UrlRouter.UNSET,
                UrlRouter.match("boards.4chan.org", Arrays.asList("g", "unknown")));
    }
}
