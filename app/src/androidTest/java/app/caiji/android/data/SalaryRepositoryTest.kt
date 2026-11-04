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
