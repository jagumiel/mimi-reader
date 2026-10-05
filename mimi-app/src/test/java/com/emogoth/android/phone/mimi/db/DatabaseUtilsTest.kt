package com.emogoth.android.phone.mimi.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class DatabaseUtilsTest {
    @Test
    fun singleOnIoDefersWorkAndRunsItAwayFromTheCaller() {
        val callerThread = Thread.currentThread()
        val invoked = AtomicBoolean(false)
        val operation = DatabaseUtils.singleOnIo {
            invoked.set(true)
            Thread.currentThread()
        }

        assertFalse(invoked.get())

        val workerThread = operation.blockingGet()

        assertTrue(invoked.get())
        assertNotSame(callerThread, workerThread)
    }
}
