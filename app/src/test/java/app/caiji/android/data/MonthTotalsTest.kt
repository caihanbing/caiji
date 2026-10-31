package app.caiji.android.data

import app.caiji.android.domain.EntryType
import java.math.BigInteger
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthTotalsTest {
    @Test fun aggregateTotalsDoNotWrapWhenSeveralValidEntriesExceedLongRange() {
        val entries = listOf(
            LedgerEntry("expense-1", EntryType.EXPENSE, Long.MAX_VALUE, "food", LocalDate.of(2026, 1, 1)),
            LedgerEntry("expense-2", EntryType.EXPENSE, 1, "food", LocalDate.of(2026, 1, 1)),
            LedgerEntry("income-1", EntryType.INCOME, Long.MAX_VALUE, "salary", LocalDate.of(2026, 1, 1)),
            LedgerEntry("income-2", EntryType.INCOME, 2, "salary", LocalDate.of(2026, 1, 1))
        )

        assertEquals(
            MonthTotals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(2))),
            MonthTotals.from(entries)
        )
    }
}
