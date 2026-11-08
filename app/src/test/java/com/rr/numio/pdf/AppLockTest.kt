package com.rr.numio.pdf

import com.rr.numio.pdf.security.AppLock
import com.rr.numio.pdf.security.LockAfter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    private val left = 1_000_000L

    @Test
    fun offNeverLocks() {
        assertFalse(AppLock.shouldLock(false, LockAfter.NOW, left, left + 999_999, outing = false))
    }

    @Test
    fun notLockedBeforeEverLeaving() {
        assertFalse(AppLock.shouldLock(true, LockAfter.NOW, 0L, left, outing = false))
    }

    @Test
    fun locksAfterTheChosenTime() {
        assertTrue(AppLock.shouldLock(true, LockAfter.NOW, left, left + 1, outing = false))
        assertFalse(AppLock.shouldLock(true, LockAfter.MINUTE, left, left + 59_000, outing = false))
        assertTrue(AppLock.shouldLock(true, LockAfter.MINUTE, left, left + 60_000, outing = false))
        assertFalse(AppLock.shouldLock(true, LockAfter.FIVE, left, left + 299_000, outing = false))
    }

    @Test
    fun choosingAFileOrSharingDoesNotLockStraightAway() {
        // "Right away" still gives time to pick a file or type the PIN
        assertFalse(AppLock.shouldLock(true, LockAfter.NOW, left, left + 30_000, outing = true))
        // …but not forever
        assertTrue(AppLock.shouldLock(true, LockAfter.NOW, left, left + 300_000, outing = true))
    }

    @Test
    fun privateClosesWhenLockingOrAfterAMinute() {
        assertTrue(AppLock.shouldClosePrivate(locking = true, leftAt = left, now = left, outing = false))
        assertFalse(AppLock.shouldClosePrivate(false, left, left + 30_000, outing = false))
        assertTrue(AppLock.shouldClosePrivate(false, left, left + 60_000, outing = false))
        assertFalse(AppLock.shouldClosePrivate(false, left, left + 120_000, outing = true))
    }
}
