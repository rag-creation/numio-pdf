package com.rr.numio.pdf.security

/** How long PDF Reader can be away before it locks again. */
enum class LockAfter(val seconds: Long, val label: String) {
    NOW(0, "Right away"),
    MINUTE(60, "1 minute"),
    FIVE(300, "5 minutes"),
}

/** When to lock: plain Kotlin, unit-tested. */
object AppLock {

    /**
     * While the person is choosing a file, sharing, or typing their PIN in Android's
     * own screen, leaving the app is expected; it only locks if they stay away this long.
     */
    const val OUTING_GRACE_SECONDS = 300L

    /** Private PDFs close again after this long away, even without the app lock. */
    const val PRIVATE_SECONDS = 60L

    /**
     * Whether coming back after [leftAt] (0: never left) needs unlocking.
     * [outing]: the app itself sent the person to another screen.
     */
    fun shouldLock(enabled: Boolean, after: LockAfter, leftAt: Long, now: Long, outing: Boolean): Boolean {
        if (!enabled || leftAt <= 0L) return false
        val away = now - leftAt
        val limit = if (outing) maxOf(after.seconds, OUTING_GRACE_SECONDS) else after.seconds
        return away >= limit * 1000
    }

    /** Whether Private closes again: when the app locks, or after a minute away. */
    fun shouldClosePrivate(locking: Boolean, leftAt: Long, now: Long, outing: Boolean): Boolean {
        if (locking) return true
        if (leftAt <= 0L) return false
        val limit = if (outing) OUTING_GRACE_SECONDS else PRIVATE_SECONDS
        return now - leftAt >= limit * 1000
    }
}
