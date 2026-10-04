package com.emogoth.android.phone.mimi.service;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BatchDownloadWorkerTest {
    @Test
    public void retriesTransientHttpFailures() {
        assertTrue(BatchDownloadWorker.isRetryableHttpStatus(408));
        assertTrue(BatchDownloadWorker.isRetryableHttpStatus(429));
        assertTrue(BatchDownloadWorker.isRetryableHttpStatus(500));
        assertTrue(BatchDownloadWorker.isRetryableHttpStatus(503));
    }

    @Test
    public void doesNotRetryPermanentHttpFailures() {
        assertFalse(BatchDownloadWorker.isRetryableHttpStatus(400));
        assertFalse(BatchDownloadWorker.isRetryableHttpStatus(403));
        assertFalse(BatchDownloadWorker.isRetryableHttpStatus(404));
    }
}
