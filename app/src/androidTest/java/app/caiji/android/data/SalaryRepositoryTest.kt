package app.caiji.android.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalaryRepositoryTest {
    private lateinit var db: SalaryDatabase
    private lateinit var repo: RoomSalaryRepository
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SalaryDatabase::class.java).build()
        repo = RoomSalaryRepository(db)
    }
    @After fun tearDown() { db.close() }

    @Test fun createAndEditIncomeTypePreserveIdentityCashAndLinkedSalaryRows() = runBlocking {
        val custom = repo.createIncomeType("旧项目", isCash = false, iconKey = "gift")
        val now = System.currentTimeMillis()
        val row = SalaryEntry(
            "salary-linked-to-custom", LocalDate.now(), custom.id, 25_000,
            isCash = false, createdAtMillis = now, updatedAtMillis = now
        )
        repo.save(row)

        val renamed = repo.updateIncomeType(custom.id, " 新项目 ", "allowance")
        assertEquals(custom.id, renamed.id)
        assertEquals("新项目", renamed.name)
        assertEquals(false, renamed.isCash)
        assertEquals(false, renamed.builtIn)
        assertEquals("allowance", renamed.iconKey)
        assertEquals(custom.sortOrder, renamed.sortOrder)
        val linkedRow = repo.entry(row.id)!!
        assertEquals(row.id, linkedRow.id)
        assertEquals(row.occurredOn, linkedRow.occurredOn)
        assertEquals(row.grossFen, linkedRow.grossFen)
        assertEquals(row.incomeTypeId, linkedRow.incomeTypeId)
        assertEquals(row.isCash, linkedRow.isCash)

        val builtin = repo.incomeTypes().first { it.builtIn && it.isCash }
        val editedBuiltin = repo.updateIncomeType(builtin.id, "新工资名称", "salary")
        assertEquals(builtin.id, editedBuiltin.id)
        assertEquals(builtin.isCash, editedBuiltin.isCash)
        assertEquals(builtin.builtIn, editedBuiltin.builtIn)
        assertEquals(builtin.sortOrder, editedBuiltin.sortOrder)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.updateIncomeType(custom.id, editedBuiltin.name, "gift") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.updateIncomeType(custom.id, "坏图标", "not-a-category-icon") }
        }
        assertEquals(renamed, repo.incomeTypes().first { it.id == custom.id })
    }

    @Test fun incomeTypesCanBeDisabledReorderedAndReenabledTransactionally() = runBlocking {
        val types = repo.incomeTypes()
        val first = types.first { it.id == "builtin-1" }
        val second = types.first { it.id == "builtin-2" }
        repo.setIncomeTypeActive(first.id, false)
        assertEquals(false, repo.incomeTypes().first { it.id == first.id }.active)
        assertEquals(false, repo.moveIncomeType(first.id, -1))

        val custom = repo.createIncomeType("积分奖励", isCash = false, iconKey = "gift")
        assertEquals(true, repo.incomeTypes().last { it.id == custom.id }.active)
        assertEquals(true, repo.moveIncomeType(custom.id, -1))
        val reordered = repo.incomeTypes()
        assertEquals(reordered.size - 2, reordered.indexOfFirst { it.id == custom.id })
        assertEquals(reordered.indices.toList(), reordered.map { it.sortOrder })

        repo.setIncomeTypeActive(first.id, true)
        assertEquals(true, repo.incomeTypes().first { it.id == first.id }.active)
    }

    @Test fun disabledIncomeTypeCannotBeSelectedForNewSalaryButCanRemainOnHistory() = runBlocking {
        val type = repo.incomeTypes().first { it.id == "builtin-1" }
        val now = System.currentTimeMillis()
        val existing = SalaryEntry("salary-before-disable", LocalDate.now(), type.id, 20000,
            isCash = type.isCash, createdAtMillis = now, updatedAtMillis = now)
        repo.save(existing)
        repo.setIncomeTypeActive(type.id, false)
        repo.save(existing.copy(grossFen = 25000))
        assertEquals(25000L, repo.entry(existing.id)!!.grossFen)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.save(existing.copy(id = "new-after-disable", grossFen = 30000)) }
        }
        assertEquals(1, repo.entries().size)
    }

    @Test fun repositoryPreparesImportPolicyAndAtomicallyAppliesDirectoryUpdates() {
        val methods = RoomSalaryRepository::class.java.declaredMethods
        org.junit.Assert.assertNotNull(
            "Import preview needs one consistent local directory/restoration decision",
            methods.firstOrNull { it.name == "incomeTypeImportCatalog" && it.parameterTypes.size == 1 }
        )
        org.junit.Assert.assertNotNull(
            "Directory restoration must be part of the same import transaction as salary rows",
            methods.firstOrNull { it.name == "importNew" && it.parameterTypes.size == 4 }
        )
    }

    @Test fun importCatalogOnlyOffersDefaultRestorationForUntouchedEmptyDatabase() = runBlocking {
        val pristine = repo.incomeTypeImportCatalog()
        assertEquals(true, pristine.restoreDefaultDirectory)
        repo.createIncomeType("本机自定义项", false, "gift")
        val customized = repo.incomeTypeImportCatalog()
        assertEquals(false, customized.restoreDefaultDirectory)
    }

    @Test fun importRestoresAllowedDirectoryAndSalaryRowsInTheSameTransaction() = runBlocking {
        val builtin = repo.incomeTypes().first { it.id == "builtin-1" }
        val restored = builtin.copy(name = "工资（自定义名）", iconKey = "salary")
        val entry = SalaryEntry(
            "restored-builtin-row", LocalDate.now(), builtin.id, 50_000,
            isCash = builtin.isCash, createdAtMillis = 1L, updatedAtMillis = 1L
        )
        val custom = SalaryIncomeType("file-custom", "文件自定义项", false, iconKey = "gift")

        val result = repo.importNew(listOf(entry), listOf(custom), listOf(restored))
        assertEquals(1, result.insertedCount)
        val types = repo.incomeTypes()
        assertEquals(restored, types.first { it.id == builtin.id })
        assertEquals(custom, types.first { it.id == custom.id })
        val saved = repo.entries().single()
        assertEquals(entry.incomeTypeId, saved.incomeTypeId)
        assertEquals(entry.grossFen, saved.grossFen)
        assertEquals(entry.isCash, saved.isCash)
    }

    @Test fun failedSalaryImportRollsBackDefaultDirectoryRestoration() = runBlocking {
        val builtin = repo.incomeTypes().first { it.id == "builtin-1" }
        val restored = builtin.copy(name = "失败后不得保留", iconKey = "salary")
        val invalidRow = SalaryEntry(
            "missing-type-row", LocalDate.now(), "missing-type-id", 50_000,
            isCash = true, createdAtMillis = 1L, updatedAtMillis = 1L
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.importNew(listOf(invalidRow), typesToRestore = listOf(restored)) }
        }
        val after = repo.incomeTypes().first { it.id == builtin.id }
        assertEquals(builtin.name, after.name)
        assertEquals(builtin.iconKey, after.iconKey)
        assertEquals(0, repo.entries().size)
    }

    @Test fun populatedLocalDirectoryWinsOverWorkbookIncomeNameAndIcon() = runBlocking {
        val builtin = repo.incomeTypes().first { it.id == "builtin-1" }
        val entry = SalaryEntry(
            "existing-builtin-row", LocalDate.now(), builtin.id, 50_000,
            isCash = builtin.isCash, createdAtMillis = 1L, updatedAtMillis = 1L
        )
        repo.save(entry)
        val workbookVersion = builtin.copy(name = "工作簿名称", iconKey = "gift")
        repo.importNew(listOf(entry.copy(id = "duplicate-import-row")), typesToRestore = listOf(workbookVersion))
        val local = repo.incomeTypes().first { it.id == builtin.id }
        assertEquals(builtin.name, local.name)
        assertEquals(builtin.iconKey, local.iconKey)
        assertEquals(1, repo.entries().size)
    }

    @Test fun pristineDirectoryRestoreSupportsAValidBuiltInNameExchange() = runBlocking {
        val first = repo.incomeTypes().first { it.id == "builtin-1" }
        val second = repo.incomeTypes().first { it.id == "builtin-2" }
        val exchanged = listOf(
            first.copy(name = second.name),
            second.copy(name = first.name)
        )

        val result = repo.importNew(emptyList(), typesToRestore = exchanged)
        assertEquals(0, result.insertedCount)
        val finalTypes = repo.incomeTypes().associateBy { it.id }
        assertEquals(second.name, finalTypes.getValue(first.id).name)
        assertEquals(first.name, finalTypes.getValue(second.id).name)
        assertEquals(first.isCash, finalTypes.getValue(first.id).isCash)
        assertEquals(second.isCash, finalTypes.getValue(second.id).isCash)
    }

    @Test fun pristineDirectoryRestoreSupportsAcyclicBuiltInNameReuse() = runBlocking {
        val first = repo.incomeTypes().first { it.id == "builtin-1" }
        val second = repo.incomeTypes().first { it.id == "builtin-2" }
        val third = repo.incomeTypes().first { it.id == "builtin-3" }
        val restored = listOf(
            first.copy(name = second.name),
            second.copy(name = third.name),
            third.copy(name = "加班费（新名称）")
        )

        repo.importNew(emptyList(), typesToRestore = restored)
        val finalTypes = repo.incomeTypes().associateBy { it.id }
        assertEquals(second.name, finalTypes.getValue(first.id).name)
        assertEquals(third.name, finalTypes.getValue(second.id).name)
        assertEquals("加班费（新名称）", finalTypes.getValue(third.id).name)
    }

    @Test fun multisetImportSkipsExistingCopiesAndKeepsTheRemainingMultiplicity() = runBlocking {
        val type = repo.incomeTypes().first()
        val now = System.currentTimeMillis()
        val row = SalaryEntry("one", LocalDate.now(), type.id, 10000, isCash = type.isCash, createdAtMillis = now, updatedAtMillis = now)
        repo.save(row)
        val first = repo.importNew(listOf(row, row.copy(id = "two")))
        assertEquals(1, first.insertedCount); assertEquals(1, first.skippedDuplicateCount)
        val second = repo.importNew(listOf(row, row.copy(id = "three")))
        assertEquals(0, second.insertedCount); assertEquals(2, second.skippedDuplicateCount)
        assertEquals(2, repo.entries().size)
    }

    @Test fun importedCustomTypeValidationRollsBackTheWholeImport() = runBlocking {
        val now = System.currentTimeMillis()
        val invalid = SalaryIncomeType("invalid-custom", "补贴".repeat(51), false)
        val row = SalaryEntry("row", LocalDate.now(), invalid.id, 10000, isCash = false, createdAtMillis = now, updatedAtMillis = now)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.importNew(listOf(row), listOf(invalid)) } }
        assertEquals(17, repo.incomeTypes().size)
        assertEquals(0, repo.entries().size)
    }

    @Test fun importedCustomTypeAndRowsCommitTogether() = runBlocking {
        val now = System.currentTimeMillis()
        val custom = SalaryIncomeType("custom-test", "测试福利", false)
        val row = SalaryEntry("row", LocalDate.now(), custom.id, 20000, isCash = false, createdAtMillis = now, updatedAtMillis = now)
        assertEquals(1, repo.importNew(listOf(row), listOf(custom)).insertedCount)
        assertEquals(false, repo.entries().single().isCash)
        assertEquals("测试福利", repo.incomeTypes().single { it.id == custom.id }.name)
    }
}
