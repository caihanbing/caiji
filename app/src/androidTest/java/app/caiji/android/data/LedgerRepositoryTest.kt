package app.caiji.android.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.backup.BackupCodec
import app.caiji.android.backup.BackupException
import app.caiji.android.domain.EntryType
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.YearMonth
import java.math.BigInteger
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerRepositoryTest {
    private lateinit var database: LedgerDatabase
    private lateinit var repository: RoomLedgerRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LedgerDatabase::class.java).build()
        repository = RoomLedgerRepository(database)
    }

    @After fun tearDown() { database.close() }

    @Test fun seedsTheApprovedCategoryCatalogOnce() = runBlocking {
        assertEquals(16, repository.categories().size)
        database.seedCategories()
        assertEquals(16, repository.categories().size)
    }

    @Test fun createsAndEditsEntriesAndAggregatesLocalCalendarMonths() = runBlocking {
        val expense = repository.categories().first { it.type == EntryType.EXPENSE && it.name == "餐饮" }
        val income = repository.categories().first { it.type == EntryType.INCOME && it.name == "工资" }
        repository.addEntry(EntryType.EXPENSE, 1234, expense.id, LocalDate.of(2025, 12, 31), "年末晚餐")
        val edited = repository.addEntry(EntryType.INCOME, 50000, income.id, LocalDate.of(2026, 1, 1), null)
        assertEquals(MonthTotals(BigInteger.ZERO, BigInteger.valueOf(50000)), repository.monthTotals(YearMonth.of(2026, 1)))
        repository.updateEntry(edited.copy(amountFen = 51000, occurredOn = LocalDate.of(2026, 1, 2)))
        assertEquals(MonthTotals(BigInteger.ZERO, BigInteger.valueOf(51000)), repository.monthTotals(YearMonth.of(2026, 1)))
        assertEquals(MonthTotals(BigInteger.valueOf(1234), BigInteger.ZERO), repository.monthTotals(YearMonth.of(2025, 12)))
        assertEquals(LocalDate.of(2026, 1, 2), repository.entriesIn(YearMonth.of(2026, 1)).single().occurredOn)
        repository.deleteEntry(edited.id)
        assertEquals(MonthTotals(BigInteger.ZERO, BigInteger.ZERO), repository.monthTotals(YearMonth.of(2026, 1)))
    }

    @Test fun reportQueriesReturnAllMonthsYearsAndCategoryTotalsFromLocalEntries() = runBlocking {
        val food = repository.categories().first { it.type == EntryType.EXPENSE && it.name == "餐饮" }
        val salary = repository.categories().first { it.type == EntryType.INCOME && it.name == "工资" }
        repository.addEntry(EntryType.EXPENSE, 1200, food.id, LocalDate.of(2024, 2, 29), "闰日")
        repository.addEntry(EntryType.EXPENSE, 500, food.id, LocalDate.of(2024, 2, 29), "另一笔")
        repository.addEntry(EntryType.INCOME, 9000, salary.id, LocalDate.of(2025, 1, 1), "新年")
        repository.setCategoryActive(food.id, false)

        val months = repository.monthlyReports(2024)
        val years = repository.yearlyReports()
        val categories = repository.categoryTotals(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 1), EntryType.EXPENSE)

        assertEquals(12, months.size)
        assertEquals(LedgerPeriodTotals(BigInteger.valueOf(1700), BigInteger.ZERO), months[1].totals)
        assertEquals(listOf(2025, 2024), years.map { it.first })
        assertEquals(LedgerPeriodTotals(BigInteger.valueOf(1700), BigInteger.ZERO), years[1].second)
        assertEquals(food.id, categories.single().category.id)
        assertEquals(BigInteger.valueOf(1700), categories.single().amountFen)
    }

    @Test fun reportQueriesInitializeTheDefaultCategoryCatalogOnFirstLaunch() = runBlocking {
        assertTrue(repository.yearlyReports().isEmpty())
        assertEquals(16, database.ledgerDao().categories().size)
    }

    @Test fun filteredPeriodReportRetainsCategoryLabelWhenItsLastEntryIsDeleted() = runBlocking {
        val food = repository.categories().first { it.type == EntryType.EXPENSE && it.name == "餐饮" }
        val date = LocalDate.of(2024, 5, 3)
        val entry = repository.addEntry(EntryType.EXPENSE, 99, food.id, date, null)
        repository.deleteEntry(entry.id)

        val report = repository.periodReport(LocalDate.of(2024, 5, 1), LocalDate.of(2024, 6, 1), EntryType.EXPENSE, food.id)

        assertEquals(food.name, report.categories.single().category.name)
        assertEquals(BigInteger.ZERO, report.categories.single().amountFen)
        assertTrue(report.entries.isEmpty())
    }

    @Test fun deactivatedCategoryStaysLinkedToHistoryButCannotBePickedForNewEntries() = runBlocking {
        val food = repository.categories().first { it.name == "餐饮" }
        val original = repository.addEntry(EntryType.EXPENSE, 123, food.id, LocalDate.now(), "旧记录")
        repository.setCategoryActive(food.id, false)
        assertEquals(food.id, repository.entriesIn(YearMonth.now()).single().categoryId)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.addEntry(EntryType.EXPENSE, 125, food.id, LocalDate.now(), null) }
        }
        assertEquals(food.id, repository.updateEntry(original.copy(note = "更正后的备注")).categoryId)
        assertTrue(repository.categories().single { it.id == food.id }.active.not())
    }

    @Test fun rejectsDuplicateActiveCategoryNamesAfterTrimming() {
        runBlocking {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.createCategory(EntryType.EXPENSE, " 餐饮 ", "restaurant") }
            }
        }
    }

    @Test fun changingEntryTypeRequiresTheMatchingEnabledCategoryAndMovesMonthlyTotals() = runBlocking {
        val food = repository.categories().first { it.type == EntryType.EXPENSE && it.name == "餐饮" }
        val salary = repository.categories().first { it.type == EntryType.INCOME && it.name == "工资" }
        val original = repository.addEntry(EntryType.EXPENSE, 890, food.id, LocalDate.now(), "更正类型")
        val changed = repository.updateEntry(original.copy(type = EntryType.INCOME, categoryId = salary.id))
        assertEquals(EntryType.INCOME, changed.type)
        assertEquals(EntryType.INCOME, repository.entryById(original.id)?.type)
        assertEquals(BigInteger.ZERO, repository.monthTotals(YearMonth.now()).expenseFen)
        assertEquals(BigInteger.valueOf(890), repository.monthTotals(YearMonth.now()).incomeFen)
    }

    @Test fun rejectsNotesOutsideTheBackupRoundTripLimit() {
        runBlocking {
            val food = repository.categories().first { it.type == EntryType.EXPENSE }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.addEntry(EntryType.EXPENSE, 100, food.id, LocalDate.now(), "x".repeat(20_001)) }
            }
        }
    }

    @Test fun categoryReorderingProducesUniqueStableDisplayPositions() = runBlocking {
        val food = repository.categories().first { it.type == EntryType.EXPENSE && it.name == "餐饮" }
        val shopping = repository.categories().first { it.type == EntryType.EXPENSE && it.name == "购物" }
        repository.reorderCategory(shopping.id, 0)
        assertEquals(listOf("购物", "餐饮"), repository.categories()
            .filter { it.type == EntryType.EXPENSE }.sortedBy { it.sortOrder }.take(2).map { it.name })
        assertEquals(0, repository.categories().single { it.id == shopping.id }.sortOrder)
        assertEquals(1, repository.categories().single { it.id == food.id }.sortOrder)
    }

    @Test fun deactivatedCategoriesRemainIncludedInPersistentReordering() = runBlocking {
        val food = repository.categories().first { it.type == EntryType.EXPENSE && it.name == "餐饮" }
        repository.setCategoryActive(food.id, false)
        repository.reorderCategory(food.id, 2)
        val reordered = repository.categories().filter { it.type == EntryType.EXPENSE }.sortedBy { it.sortOrder }

        assertEquals(food.id, reordered[2].id)
        assertEquals(false, reordered[2].active)
    }

    @Test fun categoryReorderingSurvivesClosingAndReopeningTheRoomDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "caiji-category-order-${UUID.randomUUID()}"
        val first = Room.databaseBuilder(context, LedgerDatabase::class.java, name).build()
        try {
            val repository = RoomLedgerRepository(first)
            val shopping = repository.categories().first { it.type == EntryType.EXPENSE && it.name == "购物" }
            repository.reorderCategory(shopping.id, 0)
            first.close()

            val reopened = Room.databaseBuilder(context, LedgerDatabase::class.java, name).build()
            try {
                val ordered = RoomLedgerRepository(reopened).categories().filter { it.type == EntryType.EXPENSE }.sortedBy { it.sortOrder }
                assertEquals(listOf("购物", "餐饮"), ordered.take(2).map { it.name })
            } finally {
                reopened.close()
            }
        } finally {
            runCatching { first.close() }
            context.deleteDatabase(name)
        }
    }

    @Test fun failedFullReplacementRollsBackTheExistingLedger() = runBlocking {
        val category = repository.categories().first { it.type == EntryType.EXPENSE }
        repository.addEntry(EntryType.EXPENSE, 530, category.id, LocalDate.now(), "保留")
        val before = repository.backupData()
        repository.beginRestoreSession()
        assertThrows(Exception::class.java) {
            runBlocking {
                repository.replaceAllForRestore { sink ->
                    sink.replaceCategories(emptyList())
                    sink.appendEntries(before.second)
                }
            }
        }
        repository.endRestoreSession()
        assertEquals(before, repository.backupData())
        assertEquals(MonthTotals(BigInteger.valueOf(530), BigInteger.ZERO), repository.monthTotals(YearMonth.now()))
        assertEquals(16, repository.categories().size)
    }

    @Test fun wrongPasswordRestoreRollsBackTheExistingLedger() = runBlocking {
        val category = repository.categories().first { it.type == EntryType.EXPENSE }
        repository.addEntry(EntryType.EXPENSE, 530, category.id, LocalDate.now(), "仍要保留")
        val before = repository.backupData()
        val encrypted = BackupCodec().export("correct-password", before.first, before.second)

        repository.beginRestoreSession()
        try {
            assertThrows(BackupException::class.java) {
                runBlocking {
                    repository.replaceAllForRestore { sink ->
                        BackupCodec().restore(ByteArrayInputStream(encrypted), "incorrect-password", sink)
                    }
                }
            }
        } finally {
            repository.endRestoreSession()
        }

        assertEquals(before, repository.backupData())
    }

    @Test fun closingAndReopeningKeepsLedgerDataAndCategoryReferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "caiji-persistence-${UUID.randomUUID()}"
        val first = Room.databaseBuilder(context, LedgerDatabase::class.java, name).build()
        try {
            val firstRepository = RoomLedgerRepository(first)
            val food = firstRepository.categories().first { it.name == "餐饮" }
            val entry = firstRepository.addEntry(EntryType.EXPENSE, 650, food.id, LocalDate.now(), "持久化")
            first.close()

            val reopened = Room.databaseBuilder(context, LedgerDatabase::class.java, name).build()
            try {
                val actual = RoomLedgerRepository(reopened).entriesIn(YearMonth.now()).single()
                assertEquals(entry, actual)
                assertEquals(food, RoomLedgerRepository(reopened).categories().single { it.id == food.id })
            } finally {
                reopened.close()
            }
        } finally {
            runCatching { first.close() }
            context.deleteDatabase(name)
        }
    }
}
