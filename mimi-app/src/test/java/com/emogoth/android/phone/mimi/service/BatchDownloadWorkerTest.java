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

    @Test
    public void acceptsOnlyExistingFilesWithTheExpectedSize() {
        assertTrue(BatchDownloadWorker.isCompleteFile(6_210_678L, 6_210_678L));
        assertFalse(BatchDownloadWorker.isCompleteFile(5_010_799L, 6_210_678L));
        assertFalse(BatchDownloadWorker.isCompleteFile(0L, 0L));
        assertTrue(BatchDownloadWorker.isCompleteFile(1L, 0L));
    }

    @Test
    public void rejectsConflictingExpectedAndHttpLengths() {
        assertTrue(BatchDownloadWorker.areLengthsCompatible(6_210_678L, 6_210_678L));
        assertTrue(BatchDownloadWorker.areLengthsCompatible(6_210_678L, -1L));
        assertTrue(BatchDownloadWorker.areLengthsCompatible(0L, 6_210_678L));
        assertFalse(BatchDownloadWorker.areLengthsCompatible(6_210_678L, 5_010_799L));
    }

    @Test
    public void acceptsOnlyCompleteTransfers() {
        assertTrue(BatchDownloadWorker.isCompleteTransfer(
                6_210_678L, 6_210_678L, 6_210_678L));
        assertTrue(BatchDownloadWorker.isCompleteTransfer(
                6_210_678L, 6_210_678L, -1L));
        assertFalse(BatchDownloadWorker.isCompleteTransfer(
                5_010_799L, 6_210_678L, 6_210_678L));
        assertFalse(BatchDownloadWorker.isCompleteTransfer(0L, 0L, -1L));
    }
}
