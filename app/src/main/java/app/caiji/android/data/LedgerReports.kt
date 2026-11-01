package app.caiji.android.data

import app.caiji.android.domain.Category
import app.caiji.android.domain.EntryType
import java.math.BigInteger
import java.time.YearMonth

data class LedgerPeriodTotals(
    val expenseFen: BigInteger,
    val incomeFen: BigInteger
) {
    val balanceFen: BigInteger get() = incomeFen - expenseFen

    companion object {
        fun from(entries: Iterable<LedgerEntry>): LedgerPeriodTotals {
            var expense = BigInteger.ZERO
            var income = BigInteger.ZERO
            entries.forEach { entry ->
                if (entry.type == EntryType.EXPENSE) expense += BigInteger.valueOf(entry.amountFen)
                else income += BigInteger.valueOf(entry.amountFen)
            }
            return LedgerPeriodTotals(expense, income)
        }
    }
}

data class CategoryPeriodTotal(val category: Category, val amountFen: BigInteger)
data class MonthLedgerTotal(val month: YearMonth, val totals: LedgerPeriodTotals)
data class PeriodLedgerReport(
    val startInclusive: java.time.LocalDate,
    val endExclusive: java.time.LocalDate,
    val totals: LedgerPeriodTotals,
    val categories: List<CategoryPeriodTotal>,
    val entries: List<LedgerEntry>
)

object LedgerReports {
    fun monthlyReports(entries: Iterable<LedgerEntry>, year: Int): List<MonthLedgerTotal> {
        val totalsByMonth = entries.asSequence()
            .filter { it.occurredOn.year == year }
            .groupBy { YearMonth.from(it.occurredOn) }
        return (1..12).map { month ->
            val yearMonth = YearMonth.of(year, month)
            MonthLedgerTotal(yearMonth, LedgerPeriodTotals.from(totalsByMonth[yearMonth].orEmpty()))
        }
    }

    fun yearlyReports(entries: Iterable<LedgerEntry>): List<Pair<Int, LedgerPeriodTotals>> {
        val byYear = entries.groupBy { it.occurredOn.year }
        val years = byYear.keys
        if (years.isEmpty()) return emptyList()
        return (years.minOrNull()!!..years.maxOrNull()!!).toList().asReversed().map { year ->
            year to LedgerPeriodTotals.from(byYear[year].orEmpty())
        }
    }

    fun categoryTotals(
        entries: Iterable<LedgerEntry>,
        categories: List<Category>,
        startInclusive: java.time.LocalDate,
        endExclusive: java.time.LocalDate,
        type: EntryType
    ): List<CategoryPeriodTotal> {
        val categoriesById = categories.associateBy { it.id }
        val amounts = linkedMapOf<String, BigInteger>()
        entries.asSequence()
            .filter { !it.occurredOn.isBefore(startInclusive) && it.occurredOn.isBefore(endExclusive) && it.type == type }
            .forEach { entry -> amounts[entry.categoryId] = (amounts[entry.categoryId] ?: BigInteger.ZERO) + BigInteger.valueOf(entry.amountFen) }
        return amounts.mapNotNull { (categoryId, amount) ->
            categoriesById[categoryId]?.let { CategoryPeriodTotal(it, amount) }
        }.sortedWith(compareByDescending<CategoryPeriodTotal> { it.amountFen }.thenBy { it.category.sortOrder }.thenBy { it.category.name })
    }
}
