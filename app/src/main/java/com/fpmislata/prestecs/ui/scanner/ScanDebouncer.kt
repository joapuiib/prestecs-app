package com.fpmislata.prestecs.ui.scanner

import android.os.SystemClock

/**
 * Drops repeated reads of the same code. The camera decodes a QR several times
 * per second while it stays in view; it should count once.
 *
 * A code is accepted again only after it has been out of view (not read) for
 * [windowMillis]. A different code is always accepted.
 */
class ScanDebouncer(
    private val windowMillis: Long = 1_500,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private var lastValue: String? = null
    private var lastSeenAt = 0L

    fun accept(value: String): Boolean {
        val time = now()
        val repeated = value == lastValue && time - lastSeenAt < windowMillis
        lastValue = value
        lastSeenAt = time
        return !repeated
    }

    /** Forget the last code, e.g. after rejecting it so it can be scanned again at once. */
    fun reset() {
        lastValue = null
    }
}
