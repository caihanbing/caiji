package app.caiji.android.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger
import java.time.LocalDate

class SalaryReportsTest {
    @Test fun netIncomeSubtractsSevenDeductionsInFenIncludingNegativeTax() {
        val entry = salaryEntry(gross = 1_600_000, union = 2_800, pension = 115_200,
            medical = 28_800, unemployment = 708, housing = 172_800, annuity = 0, tax = -10_000)
        assertEquals(1_289_692L, entry.netIncomeFen)
    }

    @Test fun reportsPartitionCashAndNonCashAndUseBigIntegerTotals() {
        val entries = listOf(
            salaryEntry(gross = Long.MAX_VALUE, tax = 0, cash = true),
            salaryEntry(gross = 123, tax = 0, cash = false, day = 2)
        )
        val summary = SalaryReports.summarize(entries)
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(123)), summary.grossFen)
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), summary.cashNetFen)
        assertEquals(BigInteger.valueOf(123), summary.nonCashNetFen)
    }

    @Test fun filtersRecordsByCalendarMonthAndYear() {
        val entries = listOf(salaryEntry(day = 1), salaryEntry(date = LocalDate.of(2025, 12, 31), day = 31))
        assertEquals(1, SalaryReports.filter(entries, SalaryPeriod.Month(2026, 11)).size)
        assertEquals(1, SalaryReports.filter(entries, SalaryPeriod.Year(2025)).size)
        assertEquals(2, SalaryReports.filter(entries, SalaryPeriod.All).size)
    }

    private fun salaryEntry(gross: Long = 10_000, union: Long = 0, pension: Long = 0,
        medical: Long = 0, unemployment: Long = 0, housing: Long = 0, annuity: Long = 0,
        tax: Long = 0, cash: Boolean = true, date: LocalDate = LocalDate.of(2026, 11, 1), day: Int = 1) =
        SalaryEntry("${date.year}-$day-$cash-$gross-$tax", date, "salary", gross, union, pension,
            medical, unemployment, housing, annuity, tax, cash, 1L, 1L)
}
