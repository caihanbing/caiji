package app.caiji.android.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigInteger
import java.time.LocalDate

class SalaryReportsTest {
    @Test fun salaryExportPeriodCanBeRestoredFromAndroidSavedState() {
        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(SalaryPeriod.Year(2025)) }
        }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
        assertEquals(SalaryPeriod.Year(2025), restored)
    }

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

    @Test fun incomeTypeYearTotalsUseNetIncomeIncludeDisabledHistoryAndSortTiesStably() {
        val types = listOf(
            SalaryIncomeType("alpha", "Z工资", isCash = false, sortOrder = 1, active = false),
            SalaryIncomeType("beta", "A津贴", isCash = true, sortOrder = 1),
            SalaryIncomeType("priority", "Y津贴", isCash = false, sortOrder = 0),
            SalaryIncomeType("huge", "巨额", isCash = true, sortOrder = 3),
            SalaryIncomeType("zero", "零收入", isCash = false, sortOrder = 0)
        )
        val date = LocalDate.of(2026, 11, 1)
        val entries = listOf(
            SalaryEntry("a", date, "alpha", 100_000, unionFen = 10_000, isCash = false, createdAtMillis = 1, updatedAtMillis = 1),
            SalaryEntry("b", date, "beta", 100_000, unionFen = 10_000, isCash = true, createdAtMillis = 1, updatedAtMillis = 1),
            SalaryEntry("priority", date, "priority", 100_000, unionFen = 10_000, isCash = false, createdAtMillis = 1, updatedAtMillis = 1),
            SalaryEntry("huge-a", date, "huge", Long.MAX_VALUE, isCash = true, createdAtMillis = 1, updatedAtMillis = 1),
            SalaryEntry("huge-b", date, "huge", Long.MAX_VALUE, isCash = true, createdAtMillis = 2, updatedAtMillis = 2),
            SalaryEntry("zero", date, "zero", 100_000, unionFen = 100_000, isCash = false, createdAtMillis = 1, updatedAtMillis = 1)
        )

        val totals = SalaryReports.incomeTypeTotals(entries, types)

        assertEquals(listOf("huge", "priority", "beta", "alpha", "zero"), totals.map { it.incomeType.id })
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2)), totals.first().netFen)
        assertEquals(BigInteger.valueOf(90_000), totals[1].netFen)
        assertEquals(BigInteger.valueOf(90_000), totals[2].netFen)
        assertEquals(BigInteger.valueOf(90_000), totals[3].netFen)
        assertEquals(BigInteger.ZERO, totals.last().netFen)
        assertEquals(false, totals.single { it.incomeType.id == "alpha" }.incomeType.active)
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
