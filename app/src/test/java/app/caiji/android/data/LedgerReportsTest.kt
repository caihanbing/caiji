package app.caiji.android.data

import app.caiji.android.domain.Category
import app.caiji.android.domain.EntryType
import java.math.BigInteger
import java.time.YearMonth
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerReportsTest {
    private val food = Category("food", EntryType.EXPENSE, "餐饮", "restaurant", 0, active = false)
    private val home = Category("home", EntryType.EXPENSE, "住房", "home", 1)
    private val salary = Category("salary", EntryType.INCOME, "工资", "salary", 0)

    @Test fun monthlyReportsUseCalendarMonthsIncludeEmptyMonthsAndHandleLeapDay() {
        val entries = listOf(
            entry("leap", EntryType.EXPENSE, 123, "food", LocalDate.of(2024, 2, 29)),
            entry("march", EntryType.INCOME, 500, "salary", LocalDate.of(2024, 3, 1))
        )

        val months = LedgerReports.monthlyReports(entries, 2024)

        assertEquals((1..12).map { YearMonth.of(2024, it) }, months.map { it.month })
        assertEquals(LedgerPeriodTotals(BigInteger.valueOf(123), BigInteger.ZERO), months[1].totals)
        assertEquals(LedgerPeriodTotals(BigInteger.ZERO, BigInteger.ZERO), months[0].totals)
        assertEquals(LedgerPeriodTotals(BigInteger.ZERO, BigInteger.valueOf(500)), months[2].totals)
    }

    @Test fun yearlyReportsSpanRecordedYearsAndSortNewestFirstWithExactTotals() {
        val entries = listOf(
            entry("old", EntryType.EXPENSE, Long.MAX_VALUE, "food", LocalDate.of(2022, 12, 31)),
            entry("new", EntryType.INCOME, 1, "salary", LocalDate.of(2024, 1, 1)),
            entry("old-2", EntryType.EXPENSE, 1, "home", LocalDate.of(2022, 1, 1))
        )

        val years = LedgerReports.yearlyReports(entries)

        assertEquals(listOf(2024, 2023, 2022), years.map { it.first })
        assertEquals(LedgerPeriodTotals(BigInteger.ZERO, BigInteger.ONE), years[0].second)
        assertEquals(LedgerPeriodTotals(BigInteger.ZERO, BigInteger.ZERO), years[1].second)
        assertEquals(LedgerPeriodTotals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), BigInteger.ZERO), years[2].second)
    }

    @Test fun categoryTotalsIncludeDisabledHistorySortByAmountAndExcludeOtherTypes() {
        val entries = listOf(
            entry("food-1", EntryType.EXPENSE, Long.MAX_VALUE, "food", LocalDate.of(2024, 2, 1)),
            entry("food-2", EntryType.EXPENSE, 1, "food", LocalDate.of(2024, 2, 2)),
            entry("home", EntryType.EXPENSE, 100, "home", LocalDate.of(2024, 2, 2)),
            entry("salary", EntryType.INCOME, 900, "salary", LocalDate.of(2024, 2, 2)),
            entry("outside", EntryType.EXPENSE, 777, "home", LocalDate.of(2024, 3, 1))
        )

        val categories = LedgerReports.categoryTotals(
            entries, listOf(food, home, salary), LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 1), EntryType.EXPENSE
        )

        assertEquals(listOf(food, home), categories.map { it.category })
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), categories[0].amountFen)
        assertEquals(BigInteger.valueOf(100), categories[1].amountFen)
    }

    @Test fun newerPeriodRequestPreventsOlderQueryFromUpdatingTheScreen() {
        val gate = LatestRequestGate()
        val oldMonth = gate.begin()
        val selectedYear = gate.begin()

        assertEquals(false, gate.isCurrent(oldMonth))
        assertEquals(true, gate.isCurrent(selectedYear))
    }

    private fun entry(id: String, type: EntryType, amount: Long, categoryId: String, date: LocalDate) =
        LedgerEntry(id, type, amount, categoryId, date)
}
