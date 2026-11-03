package app.caiji.android.data

import java.util.concurrent.atomic.AtomicLong
import java.time.LocalDate

internal data class ReportPeriod(val startInclusive: LocalDate, val endExclusive: LocalDate)

/** Prevents stale asynchronous report reads from replacing the most recent selection. */
internal class LatestRequestGate {
    private val sequence = AtomicLong()

    fun begin(): Long = sequence.incrementAndGet()

    fun isCurrent(requestId: Long): Boolean = sequence.get() == requestId
}

/** Tracks the active report period so returning to a cached period can supersede a slow query. */
internal class PeriodRankingRequestGate {
    private val requests = LatestRequestGate()
    private var activePeriod: ReportPeriod? = null

    fun begin(period: ReportPeriod): Long {
        activePeriod = period
        return requests.begin()
    }

    fun isCurrent(requestId: Long): Boolean = requests.isCurrent(requestId)

    fun isLoading(period: ReportPeriod): Boolean = activePeriod == period

    fun complete(requestId: Long) {
        if (requests.isCurrent(requestId)) activePeriod = null
    }

    fun cancelIfDifferent(period: ReportPeriod): Boolean {
        if (activePeriod == null || activePeriod == period) return false
        requests.begin()
        activePeriod = null
        return true
    }

    fun invalidate() {
        requests.begin()
        activePeriod = null
    }
}
