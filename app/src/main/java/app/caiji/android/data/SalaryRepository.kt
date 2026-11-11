package app.caiji.android.data

import androidx.room.withTransaction
import app.caiji.android.domain.CategoryCatalog
import java.util.UUID

class RoomSalaryRepository(private val database: SalaryDatabase) {
    private val dao get() = database.salaryDao()
    suspend fun initialize() = database.seedIncomeTypes()
    suspend fun incomeTypes(): List<SalaryIncomeType> { initialize(); return dao.incomeTypes().map { it.toDomain() } }
    suspend fun entries(): List<SalaryEntry> { initialize(); return dao.entries().map { it.toDomain() } }
    suspend fun entry(id: String): SalaryEntry? { initialize(); return dao.entry(id)?.toDomain() }

    suspend fun incomeTypeImportCatalog(): SalaryImportCatalog {
        initialize()
        return database.withTransaction {
            val types = dao.incomeTypes().map { it.toDomain() }
            SalaryImportCatalog(types, dao.salaryEntryCount() == 0 && isDefaultIncomeTypeCatalog(types))
        }
    }

    suspend fun createIncomeType(name: String, isCash: Boolean, iconKey: String = "other"): SalaryIncomeType {
        initialize()
        val clean = name.trim()
        validateIncomeTypeName(clean)
        validateIncomeTypeIcon(iconKey)
        return database.withTransaction {
            val existing = dao.incomeTypes()
            require(existing.none { it.name == clean }) { "收入项名称已存在" }
            val type = SalaryIncomeType(
                UUID.randomUUID().toString(), clean, isCash, false,
                existing.maxOfOrNull { it.sortOrder + 1 } ?: 0, iconKey
            )
            dao.insertIncomeType(type.toRow())
            type
        }
    }

    suspend fun updateIncomeType(id: String, name: String, iconKey: String): SalaryIncomeType {
        initialize()
        val clean = name.trim()
        validateIncomeTypeName(clean)
        validateIncomeTypeIcon(iconKey)
        return database.withTransaction {
            val existing = dao.incomeType(id)?.toDomain() ?: error("收入项不存在")
            require(dao.incomeTypes().none { it.id != id && it.name == clean }) { "收入项名称已存在" }
            val updated = existing.copy(name = clean, iconKey = iconKey)
            require(dao.updateIncomeType(id, clean, iconKey) == 1) { "收入项已不存在" }
            updated
        }
    }

    suspend fun setIncomeTypeActive(id: String, active: Boolean): SalaryIncomeType {
        initialize()
        return database.withTransaction {
            val current = dao.incomeType(id)?.toDomain() ?: error("收入项不存在")
            if (active) require(dao.incomeTypes().none { it.id != id && it.name == current.name }) { "收入项名称已存在" }
            require(dao.setIncomeTypeActive(id, active) == 1) { "收入项已不存在" }
            current.copy(active = active)
        }
    }

    suspend fun moveIncomeType(id: String, direction: Int): Boolean {
        initialize()
        require(direction == -1 || direction == 1) { "移动方向无效" }
        return database.withTransaction {
            val ordered = dao.incomeTypes().map { it.toDomain() }.sortedWith(compareBy<SalaryIncomeType> { it.sortOrder }.thenBy { it.name })
            val current = ordered.firstOrNull { it.id == id } ?: error("收入项不存在")
            val sameNature = ordered.filter { it.isCash == current.isCash }
            val index = sameNature.indexOfFirst { it.id == id }
            val target = index + direction
            if (target !in sameNature.indices) return@withTransaction false
            val reordered = ordered.toMutableList()
            val currentIndex = reordered.indexOfFirst { it.id == id }
            val targetIndex = reordered.indexOfFirst { it.id == sameNature[target].id }
            reordered[currentIndex] = reordered[targetIndex].also { reordered[targetIndex] = reordered[currentIndex] }
            reordered.forEachIndexed { order, type ->
                require(dao.setIncomeTypeSortOrder(type.id, order) == 1) { "更新收入项顺序失败" }
            }
            true
        }
    }

    suspend fun save(entry: SalaryEntry): SalaryEntry {
        initialize()
        entry.validate()
        database.withTransaction {
            val selectedType = dao.incomeType(entry.incomeTypeId)?.toDomain() ?: error("请选择有效收入项")
            val existing = dao.entry(entry.id)
            require(selectedType.active || existing?.incomeTypeId == entry.incomeTypeId) { "请选择启用的收入项" }
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

    /** Applies permitted catalog restoration and inserts non-duplicate rows atomically. */
    suspend fun importNew(
        incoming: List<SalaryEntry>,
        newTypes: List<SalaryIncomeType> = emptyList(),
        typesToRestore: List<SalaryIncomeType> = emptyList()
    ): ImportSalaryResult {
        initialize()
        incoming.forEach { it.validate() }
        newTypes.forEach(::validateIncomeType)
        typesToRestore.forEach(::validateIncomeType)
        return database.withTransaction {
            val currentTypes = dao.incomeTypes().map { it.toDomain() }
            val canRestoreCatalog = typesToRestore.isNotEmpty() && dao.salaryEntryCount() == 0 &&
                isDefaultIncomeTypeCatalog(currentTypes)
            if (canRestoreCatalog) {
                val defaults = SalaryIncomeTypes.defaults.associateBy { it.id }
                require(typesToRestore.map { it.id }.distinct().size == typesToRestore.size) { "收入目录包含重复 ID" }
                val restoreById = typesToRestore.associateBy { it.id }
                val resultingNames = currentTypes.map { restoreById[it.id]?.name ?: it.name }
                require(resultingNames.distinct().size == resultingNames.size) { "收入目录名称冲突" }
                typesToRestore.forEach { type ->
                    val default = defaults[type.id] ?: error("只能恢复预置收入项设置")
                    require(type.builtIn && type.isCash == default.isCash) { "内置收入项现金性质不能修改" }
                }
                val currentById = currentTypes.associateBy { it.id }
                val temporaryNames = mutableSetOf<String>()
                typesToRestore.filter { currentById[it.id]?.name != it.name }.forEach { type ->
                    var temporary = "__salary_restore_${UUID.randomUUID()}"
                    while (currentTypes.any { it.name == temporary } || !temporaryNames.add(temporary)) {
                        temporary = "__salary_restore_${UUID.randomUUID()}"
                    }
                    val current = currentById[type.id] ?: error("内置收入项已不存在")
                    require(dao.updateIncomeType(type.id, temporary, current.iconKey) == 1) { "内置收入项已不存在" }
                }
                typesToRestore.forEach { type ->
                    require(dao.updateIncomeTypeDirectory(type.id, type.name, type.iconKey, type.active, type.sortOrder) == 1) {
                        "内置收入项已不存在"
                    }
                }
            }
            newTypes.forEach { type ->
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

private fun validateIncomeTypeIcon(iconKey: String) {
    require(CategoryCatalog.isAllowedIcon(iconKey)) { "请选择有效图标" }
}

private fun validateIncomeType(type: SalaryIncomeType) {
    validateIncomeTypeName(type.name)
    require(type.name == type.name.trim() && type.id.isNotBlank() && type.id.length <= 128) { "工作簿收入项无效" }
    validateIncomeTypeIcon(type.iconKey)
}

private fun isDefaultIncomeTypeCatalog(types: List<SalaryIncomeType>): Boolean {
    if (types.size != SalaryIncomeTypes.defaults.size) return false
    val byId = types.associateBy { it.id }
    return SalaryIncomeTypes.defaults.all { expected ->
        val actual = byId[expected.id] ?: return@all false
        actual.name == expected.name && actual.isCash == expected.isCash && actual.active &&
            actual.builtIn && actual.sortOrder == expected.sortOrder && actual.iconKey == expected.iconKey
    }
}

data class ImportSalaryResult(val insertedCount: Int, val skippedDuplicateCount: Int)
