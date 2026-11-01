package app.caiji.android.data

import java.util.concurrent.atomic.AtomicLong

/** Prevents stale asynchronous report reads from replacing the most recent selection. */
internal class LatestRequestGate {
    private val sequence = AtomicLong()

    fun begin(): Long = sequence.incrementAndGet()

    fun isCurrent(requestId: Long): Boolean = sequence.get() == requestId
}
