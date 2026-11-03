package app.caiji.android.data

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodRankingRequestGateTest {
    @Test fun cachedPeriodSelectionInvalidatesAnInFlightRequestForAnotherPeriod() {
        val gate = PeriodRankingRequestGate()
        val cachedPeriod = month(2026, 1)
        val slowPeriod = month(2026, 2)
        val slowRequest = gate.begin(slowPeriod)

        assertTrue(gate.cancelIfDifferent(cachedPeriod))
        assertFalse(gate.isCurrent(slowRequest))
        assertFalse(gate.isLoading(slowPeriod))
    }

    @Test fun reselectingTheActivePeriodDoesNotCancelItsRequest() {
        val gate = PeriodRankingRequestGate()
        val period = month(2026, 1)
        val request = gate.begin(period)

        assertFalse(gate.cancelIfDifferent(period))
        assertTrue(gate.isCurrent(request))
        assertTrue(gate.isLoading(period))
    }

    @Test fun annualAndJanuaryQueriesUseDistinctPeriods() {
        val gate = PeriodRankingRequestGate()
        val annual = ReportPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1))
        val january = month(2026, 1)
        val annualRequest = gate.begin(annual)

        assertTrue(gate.cancelIfDifferent(january))
        assertFalse(gate.isCurrent(annualRequest))
        assertFalse(gate.isLoading(january))
    }

    private fun month(year: Int, month: Int) = YearMonth.of(year, month).let {
        ReportPeriod(it.atDay(1), it.plusMonths(1).atDay(1))
    }
}
