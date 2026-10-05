package com.emogoth.android.phone.mimi.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ApiRequestLimiterTest {
    @Test
    public void spacesApiRequestsOneSecondApart() {
        ApiRequestLimiter limiter = new ApiRequestLimiter();

        assertEquals(0L, limiter.reserveDelayMillis("a.4cdn.org", 10_000L));
        assertEquals(1_000L, limiter.reserveDelayMillis("a.4cdn.org", 10_000L));
        assertEquals(2_000L, limiter.reserveDelayMillis("A.4CDN.ORG", 10_000L));
    }

    @Test
    public void doesNotLimitMediaOrUnrelatedHosts() {
        ApiRequestLimiter limiter = new ApiRequestLimiter();

        assertEquals(0L, limiter.reserveDelayMillis("i.4cdn.org", 10_000L));
        assertEquals(0L, limiter.reserveDelayMillis("archived.moe", 10_000L));
        assertEquals(0L, limiter.reserveDelayMillis("a.4cdn.org", 10_000L));
    }

    @Test
    public void recoversWhenEnoughTimeHasElapsed() {
        ApiRequestLimiter limiter = new ApiRequestLimiter();

        assertEquals(0L, limiter.reserveDelayMillis("a.4cdn.org", 10_000L));
        assertEquals(0L, limiter.reserveDelayMillis("a.4cdn.org", 11_500L));
    }
}
