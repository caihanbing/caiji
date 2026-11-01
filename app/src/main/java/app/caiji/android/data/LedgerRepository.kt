package app.caiji.android.data

import androidx.room.withTransaction
import android.database.Cursor
import app.caiji.android.domain.Category
import app.caiji.android.domain.CategoryCatalog
import app.caiji.android.domain.EntryType
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class LedgerEntry(
    val id: String,
    val type: EntryType,
    val amountFen: Long,
    val categoryId: String,
    val occurredOn: LocalDate,
    val note: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis
)

object LedgerLimits {
    const val MAX_TEXT_CHARS = 20_000
}

data class MonthTotals(val expenseFen: BigInteger, val incomeFen: BigInteger) {
    companion object {
        fun from(entries: Iterable<LedgerEntry>): MonthTotals {
            var expense = BigInteger.ZERO
            var income = BigInteger.ZERO
            entries.forEach { entry ->
                if (entry.type == EntryType.EXPENSE) expense += BigInteger.valueOf(entry.amountFen)
                else income += BigInteger.valueOf(entry.amountFen)
            }
            return MonthTotals(expense, income)
        }
    }
}

data class MonthSnapshot(val entries: List<LedgerEntry>, val totals: MonthTotals)

interface LedgerRestoreSink {
    suspend fun replaceCategories(categories: List<Category>)
    suspend fun appendEntries(entries: List<LedgerEntry>)
}

typealias LedgerEntryConsumer = suspend (LedgerEntry) -> Unit
typealias LedgerEntryStream = suspend (LedgerEntryConsumer) -> Unit

class RoomLedgerRepository(private val database: LedgerDatabase) {
    private val dao get() = database.ledgerDao()
    private val writeGate = RestoreWriteGate()

    suspend fun initialize() {
        if (dao.categoryCount() == 0) writeGate.withUserWrite {
            database.withTransaction {
                if (dao.categoryCount() == 0) database.seedCategories()
            }
        }
    }

    suspend fun categories(): List<Category> {
        initialize()
        return dao.categories().map(CategoryRow::toDomain)
    }

    suspend fun createCategory(type: EntryType, name: String, iconKey: String): Category {
        initialize()
        return writeGate.withUserWrite {
            database.withTransaction {
            val cleanName = CategoryCatalog.normalizedName(name)
            require(cleanName.isNotEmpty()) { "分类名称不能为空" }
            require(cleanName.length <= LedgerLimits.MAX_TEXT_CHARS) { "分类名称过长" }
            require(CategoryCatalog.isAllowedIcon(iconKey)) { "请选择内置图标" }
            val existing = dao.categories().map(CategoryRow::toDomain)
            require(!CategoryCatalog.isDuplicate(cleanName, type, existing)) { "同类型分类名称不能重复" }
            Category(UUID.randomUUID().toString(), type, cleanName, iconKey,
                existing.filter { it.type == type }.maxOfOrNull { it.sortOrder + 1 } ?: 0).also {
                dao.insertCategory(it.toRow())
            }
            }
        }
    }

    suspend fun renameCategory(id: String, name: String, iconKey: String): Category {
        initialize()
        return writeGate.withUserWrite {
            database.withTransaction {
            val current = dao.categoryById(id)?.toDomain() ?: throw IllegalArgumentException("分类不存在")
            val cleanName = CategoryCatalog.normalizedName(name)
            require(cleanName.isNotEmpty()) { "分类名称不能为空" }
            require(cleanName.length <= LedgerLimits.MAX_TEXT_CHARS) { "分类名称过长" }
            require(CategoryCatalog.isAllowedIcon(iconKey)) { "请选择内置图标" }
            require(!CategoryCatalog.isDuplicate(cleanName, current.type, dao.categories().map(CategoryRow::toDomain), exceptId = id)) { "同类型分类名称不能重复" }
            dao.updateCategory(id, cleanName, iconKey)
            current.copy(name = cleanName, iconKey = iconKey)
            }
        }
    }

    suspend fun setCategoryActive(id: String, active: Boolean) {
        initialize()
        writeGate.withUserWrite {
            database.withTransaction {
            val current = dao.categoryById(id)?.toDomain() ?: throw IllegalArgumentException("分类不存在")
            if (active) {
                require(!CategoryCatalog.isDuplicate(current.name, current.type, dao.categories().map(CategoryRow::toDomain), exceptId = id)) {
                    "分类名称已被使用，重命名后再启用"
                }
            }
            require(dao.setCategoryActive(id, active) == 1)
            }
        }
    }

    suspend fun reorderCategory(id: String, sortOrder: Int) {
        initialize()
        writeGate.withUserWrite {
            database.withTransaction {
            val categories = dao.categories().map(CategoryRow::toDomain)
            val current = categories.firstOrNull { it.id == id } ?: throw IllegalArgumentException("分类不存在")
            val sameType = categories.filter { it.type == current.type }.sortedBy { it.sortOrder }.toMutableList()
            sameType.removeAll { it.id == id }
            sameType.add(sortOrder.coerceIn(0, sameType.size), current)
            sameType.forEachIndexed { index, category -> dao.setCategoryOrder(category.id, index) }
            }
        }
    }

    suspend fun addEntry(
        type: EntryType,
        amountFen: Long,
        categoryId: String,
        occurredOn: LocalDate,
        note: String?
    ): LedgerEntry {
        initialize()
        return writeGate.withUserWrite {
            database.withTransaction {
                validateAmountAndDate(amountFen, occurredOn)
                validateNote(note)
                val category = dao.categoryById(categoryId)
                require(category != null && category.active && category.type == type.name) { "请选择有效的收支分类" }
                val now = System.currentTimeMillis()
                val entry = LedgerEntry(UUID.randomUUID().toString(), type, amountFen, categoryId, occurredOn,
                    note?.trim()?.ifEmpty { null }, now, now)
                dao.insertEntry(entry.toRow())
                entry
            }
        }
    }

    suspend fun updateEntry(entry: LedgerEntry): LedgerEntry {
        initialize()
        return writeGate.withUserWrite {
            database.withTransaction {
                validateAmountAndDate(entry.amountFen, entry.occurredOn)
                validateNote(entry.note)
                val previous = dao.entryById(entry.id)?.toDomain()
                    ?: throw IllegalArgumentException("账单不存在")
                val category = dao.categoryById(entry.categoryId)
                require(category != null && category.type == entry.type.name) { "分类与账单类型不匹配" }
                require(category.active || previous.categoryId == entry.categoryId) { "不能更改为已停用分类" }
                val updated = entry.copy(note = entry.note?.trim()?.ifEmpty { null }, updatedAtMillis = System.currentTimeMillis())
                dao.updateEntry(updated.id, updated.type.name, updated.amountFen, updated.categoryId, updated.occurredOn.toString(),
                    updated.note, updated.updatedAtMillis)
                updated
            }
        }
    }

    suspend fun deleteEntry(id: String) {
        initialize()
        writeGate.withUserWrite { dao.deleteEntry(id) }
    }

    suspend fun entriesIn(month: YearMonth): List<LedgerEntry> {
        initialize()
        return dao.entriesBetween(month.atDay(1).toString(), month.plusMonths(1).atDay(1).toString()).map(EntryRow::toDomain)
    }

    suspend fun monthSnapshot(month: YearMonth): MonthSnapshot {
        initialize()
        val start = month.atDay(1).toString()
        val end = month.plusMonths(1).atDay(1).toString()
        return database.withTransaction {
            dao.entriesBetween(start, end).map(EntryRow::toDomain).let { MonthSnapshot(it, MonthTotals.from(it)) }
        }
    }

    suspend fun monthTotals(month: YearMonth): MonthTotals = monthSnapshot(month).totals

    suspend fun monthlyReports(year: Int): List<MonthLedgerTotal> {
        initialize()
        return database.withTransaction {
            LedgerReports.monthlyReports(dao.allEntries().map(EntryRow::toDomain), year)
        }
    }

    suspend fun yearlyReports(): List<Pair<Int, LedgerPeriodTotals>> {
        initialize()
        return database.withTransaction {
            LedgerReports.yearlyReports(dao.allEntries().map(EntryRow::toDomain))
        }
    }

    suspend fun categoryTotals(
        startInclusive: LocalDate,
        endExclusive: LocalDate,
        type: EntryType
    ): List<CategoryPeriodTotal> {
        initialize()
        return database.withTransaction {
            require(!endExclusive.isBefore(startInclusive)) { "账单期间无效" }
            val entries = dao.entriesBetween(startInclusive.toString(), endExclusive.toString()).map(EntryRow::toDomain)
            LedgerReports.categoryTotals(entries, dao.categories().map(CategoryRow::toDomain), startInclusive, endExclusive, type)
        }
    }

    suspend fun periodReport(
        startInclusive: LocalDate,
        endExclusive: LocalDate,
        type: EntryType,
        categoryId: String? = null
    ): PeriodLedgerReport {
        initialize()
        return database.withTransaction {
            require(endExclusive.isAfter(startInclusive)) { "账单期间无效" }
            val entries = dao.entriesBetween(startInclusive.toString(), endExclusive.toString()).map(EntryRow::toDomain)
            val storedCategories = dao.categories().map(CategoryRow::toDomain)
            val categoryTotals = LedgerReports.categoryTotals(entries, storedCategories, startInclusive, endExclusive, type)
            val categoriesForDisplay = if (categoryId != null && categoryTotals.none { it.category.id == categoryId }) {
                storedCategories.firstOrNull { it.id == categoryId && it.type == type }
                    ?.let { categoryTotals + CategoryPeriodTotal(it, BigInteger.ZERO) }
                    ?: categoryTotals
            } else categoryTotals
            PeriodLedgerReport(
                startInclusive = startInclusive,
                endExclusive = endExclusive,
                totals = LedgerPeriodTotals.from(entries),
                categories = categoriesForDisplay,
                entries = if (categoryId == null) emptyList() else entries.filter { it.categoryId == categoryId && it.type == type }
            )
        }
    }

    suspend fun allEntries(): List<LedgerEntry> {
        initialize()
        return dao.allEntries().map(EntryRow::toDomain)
    }

    suspend fun entryById(id: String): LedgerEntry? {
        initialize()
        return dao.entryById(id)?.toDomain()
    }

    suspend fun backupData(): Pair<List<Category>, List<LedgerEntry>> {
        initialize()
        return database.withTransaction {
            dao.categories().map(CategoryRow::toDomain) to dao.allEntries().map(EntryRow::toDomain)
        }
    }

    suspend fun <T> withBackupSnapshot(
        block: suspend (List<Category>, Long, LedgerEntryStream) -> T
    ): T {
        initialize()
        return database.withTransaction {
            val storedCategories = dao.categories().map(CategoryRow::toDomain)
            val entryCount = dao.entryCount()
            val entryStream: LedgerEntryStream = { consume ->
                dao.entriesCursor().use { cursor ->
                    while (cursor.moveToNext()) consume(cursor.toDomain())
                }
            }
            block(storedCategories, entryCount, entryStream)
        }
    }

    suspend fun hasExistingData(): Boolean {
        initialize()
        return database.withTransaction {
            dao.entryCount() > 0 || dao.categories().map(CategoryRow::toDomain) != CategoryCatalog.defaults()
        }
    }

    suspend fun beginRestoreSession() {
        initialize()
        writeGate.beginRestoreSession()
    }

    suspend fun endRestoreSession() = writeGate.endRestoreSession()

    suspend fun replaceAllForRestore(restore: suspend (LedgerRestoreSink) -> Unit) {
        writeGate.withRestoreWrite {
            database.withTransaction {
                dao.clearEntries()
                dao.clearCategories()
                restore(object : LedgerRestoreSink {
                    override suspend fun replaceCategories(categories: List<Category>) {
                        dao.insertCategories(categories.map(Category::toRow))
                    }

                    override suspend fun appendEntries(entries: List<LedgerEntry>) {
                        if (entries.isNotEmpty()) dao.insertEntries(entries.map(LedgerEntry::toRow))
                    }
                })
            }
        }
    }

    suspend fun isEmpty(): Boolean {
        initialize()
        return dao.entryCount() == 0L
    }

    private fun validateAmountAndDate(amountFen: Long, occurredOn: LocalDate) {
        require(amountFen > 0L) { "金额必须大于零" }
        require(!occurredOn.isAfter(LocalDate.now())) { "日期不能晚于今天" }
    }

    private fun validateNote(note: String?) {
        require((note?.length ?: 0) <= LedgerLimits.MAX_TEXT_CHARS) { "备注不能超过 ${LedgerLimits.MAX_TEXT_CHARS} 个字符" }
    }
}

private fun Category.toRow() = CategoryRow(id, type.name, name, iconKey, sortOrder, active)
private fun CategoryRow.toDomain() = Category(id, EntryType.valueOf(type), name, iconKey, sortOrder, active)
private fun LedgerEntry.toRow() = EntryRow(id, type.name, amountFen, categoryId, occurredOn.toString(), note, createdAtMillis, updatedAtMillis)
private fun EntryRow.toDomain() = LedgerEntry(id, EntryType.valueOf(type), amountFen, categoryId, LocalDate.parse(occurredOn), note, createdAtMillis, updatedAtMillis)
private fun Cursor.toDomain(): LedgerEntry = LedgerEntry(
    id = getString(getColumnIndexOrThrow("id")),
    type = EntryType.valueOf(getString(getColumnIndexOrThrow("type"))),
    amountFen = getLong(getColumnIndexOrThrow("amountFen")),
    categoryId = getString(getColumnIndexOrThrow("categoryId")),
    occurredOn = LocalDate.parse(getString(getColumnIndexOrThrow("occurredOn"))),
    note = getColumnIndexOrThrow("note").let { if (isNull(it)) null else getString(it) },
    createdAtMillis = getLong(getColumnIndexOrThrow("createdAtMillis")),
    updatedAtMillis = getLong(getColumnIndexOrThrow("updatedAtMillis"))
)
