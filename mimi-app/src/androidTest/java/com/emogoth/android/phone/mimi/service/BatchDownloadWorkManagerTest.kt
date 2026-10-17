package com.emogoth.android.phone.mimi.service

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BatchDownloadWorkManagerTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workManager: WorkManager = WorkManager.getInstance(context)
    private val workNames = mutableSetOf<String>()

    @After
    fun cancelTestWork() {
        workNames.forEach { name ->
            workManager.cancelUniqueWork(name).result.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    @Test
    fun enqueuePersistsTheDownloadContractAndKeepsDuplicatesUnique() {
        val fixture = fixture()
        val request = BatchDownloadWorker.createWorkRequest(
                fixture.directoryUri,
                fixture.board,
                fixture.threadId,
                fixture.postIds,
                TEST_INITIAL_DELAY_MILLIS
        )
        val input = request.workSpec.input
        assertEquals(fixture.directoryUri.toString(), input.getString("directory_uri"))
        assertEquals(fixture.board, input.getString("board"))
        assertEquals(fixture.threadId, input.getLong("thread_id", -1L))
        assertArrayEquals(fixture.postIds, input.getLongArray("post_ids"))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(WorkRequest.MIN_BACKOFF_MILLIS, request.workSpec.backoffDelayDuration)

        val firstId = enqueueDelayed(fixture)
        val firstInfo = waitForInfo(firstId)

        assertEquals(WorkInfo.State.ENQUEUED, firstInfo.state)
        assertTrue(firstInfo.tags.contains(BatchDownloadWorker.WORK_TAG))

        val duplicateId = enqueueDelayed(fixture)

        assertNull(workManager.getWorkInfoById(duplicateId)
                .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(WorkInfo.State.ENQUEUED, waitForInfo(firstId).state)
    }

    @Test
    fun activeDownloadCanBeCancelled() {
        val fixture = fixture()
        val workId = enqueueDelayed(fixture)
        waitForInfo(workId)

        workManager.cancelWorkById(workId).result
                .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        assertEquals(WorkInfo.State.CANCELLED, waitForInfo(workId).state)
    }

    @Test
    fun cancelledDownloadCanBeEnqueuedAgainAsRetry() {
        val fixture = fixture()
        val originalId = enqueueDelayed(fixture)
        waitForInfo(originalId)
        workManager.cancelWorkById(originalId).result
                .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertEquals(WorkInfo.State.CANCELLED, waitForInfo(originalId).state)

        val retryId = enqueueDelayed(fixture)
        val retryInfo = waitForInfo(retryId)

        assertNotEquals(originalId, retryId)
        assertEquals(WorkInfo.State.ENQUEUED, retryInfo.state)
        val currentGeneration = workManager.getWorkInfosForUniqueWork(fixture.uniqueName)
                .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertTrue(currentGeneration.any {
            it.id == retryId && it.state == WorkInfo.State.ENQUEUED
        })
    }

    @Test
    fun queuedDownloadCanBeRecoveredByTagAndUniqueName() {
        val fixture = fixture()
        val workId = enqueueDelayed(fixture)
        waitForInfo(workId)

        val recoveredByName = workManager.getWorkInfosForUniqueWork(fixture.uniqueName)
                .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .firstOrNull { it.id == workId }
        val recoveredByTag = workManager.getWorkInfosByTag(BatchDownloadWorker.WORK_TAG)
                .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .firstOrNull { it.id == workId }

        assertNotNull(recoveredByName)
        assertNotNull(recoveredByTag)
        assertEquals(WorkInfo.State.ENQUEUED, recoveredByName?.state)
        assertTrue(recoveredByTag?.tags?.contains(BatchDownloadWorker.WORK_TAG) == true)
    }

    private fun enqueueDelayed(fixture: WorkFixture): UUID {
        workNames.add(fixture.uniqueName)
        return BatchDownloadWorker.enqueue(
                context,
                fixture.directoryUri,
                fixture.board,
                fixture.threadId,
                fixture.postIds,
                TEST_INITIAL_DELAY_MILLIS
        )
    }

    private fun waitForInfo(workId: UUID): WorkInfo {
        var info: WorkInfo? = null
        waitUntil {
            info = workManager.getWorkInfoById(workId)
                    .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            info != null && info?.state != WorkInfo.State.BLOCKED
        }
        return requireNotNull(info)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + STATE_TIMEOUT_MILLIS
        while (!condition() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        assertTrue("WorkManager did not reach the expected state", condition())
    }

    private fun fixture(): WorkFixture {
        val id = SystemClock.elapsedRealtimeNanos()
        val directoryUri = Uri.parse("content://com.emogoth.android.phone.mimi.test/downloads/$id")
        val board = "instrumentation-$id"
        val threadId = id.coerceAtLeast(1L)
        val postIds = longArrayOf(id + 1L, id + 2L)
        val uniqueName = BatchDownloadWorker.uniqueWorkName(
                directoryUri, board, threadId, postIds)
        return WorkFixture(directoryUri, board, threadId, postIds, uniqueName)
    }

    private data class WorkFixture(
            val directoryUri: Uri,
            val board: String,
            val threadId: Long,
            val postIds: LongArray,
            val uniqueName: String
    )

    private companion object {
        val TEST_INITIAL_DELAY_MILLIS = TimeUnit.HOURS.toMillis(1L)
        const val OPERATION_TIMEOUT_SECONDS = 10L
        const val STATE_TIMEOUT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}
