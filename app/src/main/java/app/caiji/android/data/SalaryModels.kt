package app.caiji.android.data

import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth

sealed interface SalaryPeriod : java.io.Serializable {
    data class Month(val year: Int, val month: Int) : SalaryPeriod
    data class Year(val year: Int) : SalaryPeriod
    data object All : SalaryPeriod
}

data class SalaryIncomeType(
    val id: String,
    val name: String,
    val isCash: Boolean,
    val builtIn: Boolean = false,
    val sortOrder: Int = 0,
    val iconKey: String = "other",
    val active: Boolean = true
)

data class SalaryEntry(
    val id: String,
    val occurredOn: LocalDate,
    val incomeTypeId: String,
    val grossFen: Long,
    val unionFen: Long = 0,
    val pensionFen: Long = 0,
    val medicalFen: Long = 0,
    val unemploymentFen: Long = 0,
    val housingFen: Long = 0,
    val annuityFen: Long = 0,
    val taxFen: Long = 0,
    val isCash: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) : java.io.Serializable {
    val deductionsFen: BigInteger
        get() = listOf(unionFen, pensionFen, medicalFen, unemploymentFen, housingFen, annuityFen, taxFen)
            .fold(BigInteger.ZERO) { total, fen -> total + BigInteger.valueOf(fen) }
    val netIncomeFen: Long
        get() {
            validate()
            return BigInteger.valueOf(grossFen).subtract(deductionsFen).toLongExactCompatible()
        }

    fun validate(today: LocalDate = LocalDate.now()) {
        require(grossFen >= 0) { "毛收入不能为负数" }
        require(listOf(unionFen, pensionFen, medicalFen, unemploymentFen, housingFen, annuityFen).all { it >= 0 }) {
            "税额以外的扣款不能为负数"
        }
        require(occurredOn <= today) { "日期不能晚于今天" }
        val net = BigInteger.valueOf(grossFen).subtract(deductionsFen)
        require(net.signum() >= 0) { "纯收入不能为负数" }
        net.toLongExactCompatible()
    }

    fun duplicateKey(): SalaryDuplicateKey = SalaryDuplicateKey(
        occurredOn, incomeTypeId, grossFen, unionFen, pensionFen, medicalFen, unemploymentFen, housingFen, annuityFen, taxFen
    )
}

data class SalaryDuplicateKey(
    val occurredOn: LocalDate,
    val incomeTypeId: String,
    val grossFen: Long,
    val unionFen: Long,
    val pensionFen: Long,
    val medicalFen: Long,
    val unemploymentFen: Long,
    val housingFen: Long,
    val annuityFen: Long,
    val taxFen: Long
)

data class SalaryTotals(
    val grossFen: BigInteger = BigInteger.ZERO,
    val deductionFen: BigInteger = BigInteger.ZERO,
    val netFen: BigInteger = BigInteger.ZERO,
    val cashNetFen: BigInteger = BigInteger.ZERO,
    val nonCashNetFen: BigInteger = BigInteger.ZERO,
    val entryCount: Int = 0
)

internal data class SalaryIncomeTypeTotal(val incomeType: SalaryIncomeType, val netFen: BigInteger)

data class SalaryImportDelta(val entriesToInsert: List<SalaryEntry>, val skippedDuplicateCount: Int)

data class SalaryImportCatalog(
    val incomeTypes: List<SalaryIncomeType>,
    val restoreDefaultDirectory: Boolean
)

object SalaryReports {
    fun importDelta(incoming: List<SalaryEntry>, existing: List<SalaryEntry>): SalaryImportDelta {
        val available = existing.groupingBy { it.duplicateKey() }.eachCount().toMutableMap()
        val insert = mutableListOf<SalaryEntry>()
        var skipped = 0
        incoming.forEach { entry ->
            val key = entry.duplicateKey()
            val count = available[key] ?: 0
            if (count > 0) { available[key] = count - 1; skipped++ } else insert += entry
        }
        return SalaryImportDelta(insert, skipped)
    }

    fun filter(entries: List<SalaryEntry>, period: SalaryPeriod): List<SalaryEntry> = when (period) {
        is SalaryPeriod.Month -> entries.filter { YearMonth.from(it.occurredOn) == YearMonth.of(period.year, period.month) }
        is SalaryPeriod.Year -> entries.filter { it.occurredOn.year == period.year }
        SalaryPeriod.All -> entries
    }

    fun summarize(entries: List<SalaryEntry>): SalaryTotals = entries.fold(SalaryTotals()) { sum, entry ->
        entry.validate()
        val net = BigInteger.valueOf(entry.netIncomeFen)
        sum.copy(
            grossFen = sum.grossFen + BigInteger.valueOf(entry.grossFen),
            deductionFen = sum.deductionFen + entry.deductionsFen,
            netFen = sum.netFen + net,
            cashNetFen = if (entry.isCash) sum.cashNetFen + net else sum.cashNetFen,
            nonCashNetFen = if (!entry.isCash) sum.nonCashNetFen + net else sum.nonCashNetFen,
            entryCount = sum.entryCount + 1
        )
    }

    internal fun incomeTypeTotals(entries: List<SalaryEntry>, incomeTypes: List<SalaryIncomeType>): List<SalaryIncomeTypeTotal> {
        val typesById = incomeTypes.associateBy { it.id }
        val totals = entries.groupBy { it.incomeTypeId }.mapNotNull { (incomeTypeId, rows) ->
            val type = typesById[incomeTypeId] ?: return@mapNotNull null
            val net = rows.fold(BigInteger.ZERO) { total, entry -> total + BigInteger.valueOf(entry.netIncomeFen) }
            SalaryIncomeTypeTotal(type, net)
        }
        return totals.sortedWith(
            compareByDescending<SalaryIncomeTypeTotal> { it.netFen }
                .thenBy { it.incomeType.sortOrder }
                .thenBy { it.incomeType.name }
                .thenBy { it.incomeType.id }
        )
    }
}

private fun BigInteger.toLongExactCompatible(): Long {
    require(this >= BigInteger.valueOf(Long.MIN_VALUE) && this <= BigInteger.valueOf(Long.MAX_VALUE)) { "金额超出可用范围" }
    return toLong()
}
