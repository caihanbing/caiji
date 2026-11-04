package app.caiji.android.data

import androidx.room.withTransaction
import java.util.UUID

class RoomSalaryRepository(private val database: SalaryDatabase) {
    private val dao get() = database.salaryDao()
    suspend fun initialize() = database.seedIncomeTypes()
    suspend fun incomeTypes(): List<SalaryIncomeType> { initialize(); return dao.incomeTypes().map { it.toDomain() } }
    suspend fun entries(): List<SalaryEntry> { initialize(); return dao.entries().map { it.toDomain() } }
    suspend fun entry(id: String): SalaryEntry? { initialize(); return dao.entry(id)?.toDomain() }

    suspend fun createIncomeType(name: String, isCash: Boolean): SalaryIncomeType {
        initialize()
        val clean = name.trim()
        validateIncomeTypeName(clean)
        val type = SalaryIncomeType(UUID.randomUUID().toString(), clean, isCash, false, dao.incomeTypes().maxOfOrNull { it.sortOrder + 1 } ?: 0)
        dao.insertIncomeType(type.toRow())
        return type
    }

    suspend fun save(entry: SalaryEntry): SalaryEntry {
        initialize()
        entry.validate()
        require(dao.incomeType(entry.incomeTypeId) != null) { "请选择有效收入项" }
        database.withTransaction {
            val existing = dao.entry(entry.id)
            val now = System.currentTimeMillis()
            if (existing == null) dao.insertEntry(entry.copy(createdAtMillis = now, updatedAtMillis = now).toRow())
            else require(dao.updateEntry(entry.id, entry.occurredOn.toString(), entry.incomeTypeId, entry.grossFen, entry.unionFen, entry.pensionFen, entry.medicalFen, entry.unemploymentFen, entry.housingFen, entry.annuityFen, entry.taxFen, now) == 1) { "工资记录已删除" }
        }
        return entry
    }

    suspend fun delete(id: String) {
        initialize()
        require(dao.deleteEntry(id) == 1) { "工资记录不存在" }
    }

    suspend fun duplicateCount(incoming: List<SalaryEntry>): Int {
        initialize()
        val available = dao.entries().map { it.toDomain() }.groupingBy { it.duplicateKey() }.eachCount().toMutableMap()
        var duplicates = 0
        incoming.forEach { entry ->
            val key = entry.duplicateKey()
            val count = available[key] ?: 0
            if (count > 0) { available[key] = count - 1; duplicates++ }
        }
        return duplicates
    }

    /** Inserts non-duplicate rows atomically. Identical rows are matched as a multiset. */
    suspend fun importNew(incoming: List<SalaryEntry>, newTypes: List<SalaryIncomeType> = emptyList()): ImportSalaryResult {
        initialize()
        incoming.forEach { it.validate() }
        return database.withTransaction {
            newTypes.forEach { type ->
                validateIncomeTypeName(type.name)
                require(type.name == type.name.trim() && type.id.isNotBlank() && type.id.length <= 128 && !type.builtIn) { "工作簿自定义收入项无效" }
                require(dao.incomeType(type.id) == null && dao.incomeTypes().none { it.name == type.name }) { "收入项已存在，请重新导入" }
                dao.insertIncomeType(type.toRow())
            }
            val types = dao.incomeTypes().associateBy { it.id }
            require(incoming.all { types.containsKey(it.incomeTypeId) }) { "文件含未知收入项，请先创建后重试" }
            require(incoming.all { types.getValue(it.incomeTypeId).isCash == it.isCash }) { "收入项现金性质与本机设置不一致" }
            val available = dao.entries().map { it.toDomain() }.groupingBy { it.duplicateKey() }.eachCount().toMutableMap()
            var skipped = 0
            val toInsert = mutableListOf<SalaryEntry>()
            incoming.forEach { entry ->
                val key = entry.duplicateKey()
                val count = available[key] ?: 0
                if (count > 0) { available[key] = count - 1; skipped++ } else toInsert += entry
            }
            val now = System.currentTimeMillis()
            toInsert.forEach { dao.insertEntry(it.copy(id = UUID.randomUUID().toString(), createdAtMillis = now, updatedAtMillis = now).toRow()) }
            ImportSalaryResult(toInsert.size, skipped)
        }
    }
}

private fun validateIncomeTypeName(name: String) {
    require(name.isNotBlank()) { "收入项名称不能为空" }
    require(name.length <= 100) { "收入项名称不能超过100个字符" }
}

data class ImportSalaryResult(val insertedCount: Int, val skippedDuplicateCount: Int)
