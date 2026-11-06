package app.caiji.android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalaryMigrationTest {
    @Test fun migratesV1IncomeTypesAndSalaryRowsWithoutChangingHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "salary-migration-v1-test.db"
        context.deleteDatabase(databaseName)
        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
        createVersion1Schema(legacy)
        val legacyTypes = SalaryIncomeTypes.defaults
        legacyTypes.forEach { type ->
            legacy.execSQL(
                "INSERT INTO salary_income_types (id, name, isCash, builtIn, sortOrder) VALUES (?, ?, ?, ?, ?)",
                arrayOf(type.id, type.name, if (type.isCash) 1 else 0, 1, type.sortOrder)
            )
        }
        legacy.execSQL(
            "INSERT INTO salary_income_types (id, name, isCash, builtIn, sortOrder) VALUES (?, ?, ?, ?, ?)",
            arrayOf("custom-v1", "旧自定义补贴", 0, 0, 17)
        )
        legacy.execSQL(
            "INSERT INTO salary_entries (id, occurredOn, incomeTypeId, grossFen, unionFen, pensionFen, medicalFen, unemploymentFen, housingFen, annuityFen, taxFen, createdAtMillis, updatedAtMillis) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf("salary-v1", "2025-03-05", "builtin-1", 500000L, 1000L, 0L, 2000L, 0L, 0L, 0L, 30000L, 11L, 12L)
        )
        legacy.close()

        val migrationField = SalaryDatabase::class.java.fields.firstOrNull { it.name == "MIGRATION_1_2" }
        assertNotNull("SalaryDatabase must expose MIGRATION_1_2", migrationField)
        val migration = migrationField!!.get(null) as Migration
        val database = Room.databaseBuilder(context, SalaryDatabase::class.java, databaseName)
            .addMigrations(migration)
            .build()
        try {
            val repository = RoomSalaryRepository(database)
            val types = repository.incomeTypes()
            assertEquals(18, types.size)
            val expectedIcons = listOf(
                "salary", "restaurant", "work", "gift", "gift", "gift", "gift", "gift", "gift", "gift",
                "daily", "dessert", "insurance", "travel", "movie", "allowance", "refund"
            )
            SalaryIncomeTypes.defaults.forEachIndexed { index, expected ->
                val actual = types.first { it.id == expected.id }
                assertEquals(expected.name, actual.name)
                assertEquals(expected.isCash, actual.isCash)
                assertEquals(expected.builtIn, actual.builtIn)
                assertEquals(expected.sortOrder, actual.sortOrder)
                assertEquals(expectedIcons[index], iconFor(database, expected.id))
            }
            val migratedCustom = types.first { it.id == "custom-v1" }
            assertEquals("other", iconFor(database, "custom-v1"))
            assertEquals("旧自定义补贴", migratedCustom.name)
            assertEquals(false, migratedCustom.isCash)
            assertEquals(false, migratedCustom.builtIn)
            assertEquals(17, migratedCustom.sortOrder)
            val entry = repository.entry("salary-v1")!!
            assertEquals(1, repository.entries().size)
            assertEquals(LocalDate.parse("2025-03-05"), entry.occurredOn)
            assertEquals(500000L, entry.grossFen)
            assertEquals(1000L, entry.unionFen)
            assertEquals(0L, entry.pensionFen)
            assertEquals(2000L, entry.medicalFen)
            assertEquals(0L, entry.unemploymentFen)
            assertEquals(0L, entry.housingFen)
            assertEquals(0L, entry.annuityFen)
            assertEquals(30000L, entry.taxFen)
            assertEquals("builtin-1", entry.incomeTypeId)
            assertEquals(true, entry.isCash)
            assertEquals(11L, entry.createdAtMillis)
            assertEquals(12L, entry.updatedAtMillis)
            val rows = database.openHelper.readableDatabase.query("PRAGMA user_version")
            rows.use { assertEquals(2, if (it.moveToFirst()) it.getInt(0) else -1) }
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test fun failedV1MigrationRollsBackTheSchemaVersionAndAddedColumn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "salary-migration-failure-test.db"
        context.deleteDatabase(databaseName)
        val legacy = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
        createVersion1Schema(legacy)
        legacy.execSQL(
            "INSERT INTO salary_income_types (id, name, isCash, builtIn, sortOrder) VALUES (?, ?, ?, ?, ?)",
            arrayOf("builtin-1", "工资（交行 到账时间）", 1, 1, 0)
        )
        legacy.execSQL("CREATE TRIGGER fail_salary_icon_migration BEFORE UPDATE ON salary_income_types BEGIN SELECT RAISE(ABORT, 'intentional migration failure'); END")
        legacy.close()

        val migrationField = SalaryDatabase::class.java.fields.firstOrNull { it.name == "MIGRATION_1_2" }
        assertNotNull("SalaryDatabase must expose MIGRATION_1_2", migrationField)
        val migrated = Room.databaseBuilder(context, SalaryDatabase::class.java, databaseName)
            .addMigrations(migrationField!!.get(null) as androidx.room.migration.Migration)
            .build()
        val failed = runCatching { migrated.openHelper.writableDatabase }.isFailure
        migrated.close()
        assertTrue("The injected migration failure must be surfaced", failed)

        val reopened = SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            assertEquals(1, reopened.version)
            val columns = reopened.rawQuery("PRAGMA table_info(salary_income_types)", null)
            columns.use {
                var hasIconKey = false
                while (it.moveToNext()) if (it.getString(1) == "iconKey") hasIconKey = true
                assertEquals(false, hasIconKey)
            }
            val rows = reopened.rawQuery("SELECT COUNT(*) FROM salary_income_types", null)
            rows.use { assertTrue(it.moveToFirst() && it.getInt(0) == 1) }
        } finally {
            reopened.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun iconFor(database: SalaryDatabase, id: String): String {
        val cursor = database.openHelper.readableDatabase.query(
            "SELECT iconKey FROM salary_income_types WHERE id = ?", arrayOf(id)
        )
        return cursor.use { if (it.moveToFirst()) it.getString(0) else error("Missing income type $id") }
    }

    private fun createVersion1Schema(database: SQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS `salary_income_types` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `isCash` INTEGER NOT NULL, `builtIn` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_salary_income_types_name` ON `salary_income_types` (`name`)")
        database.execSQL("CREATE TABLE IF NOT EXISTS `salary_entries` (`id` TEXT NOT NULL, `occurredOn` TEXT NOT NULL, `incomeTypeId` TEXT NOT NULL, `grossFen` INTEGER NOT NULL, `unionFen` INTEGER NOT NULL, `pensionFen` INTEGER NOT NULL, `medicalFen` INTEGER NOT NULL, `unemploymentFen` INTEGER NOT NULL, `housingFen` INTEGER NOT NULL, `annuityFen` INTEGER NOT NULL, `taxFen` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`incomeTypeId`) REFERENCES `salary_income_types`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_salary_entries_occurredOn_createdAtMillis` ON `salary_entries` (`occurredOn`, `createdAtMillis`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_salary_entries_incomeTypeId` ON `salary_entries` (`incomeTypeId`)")
        database.execSQL("CREATE TABLE IF NOT EXISTS `room_master_table` (`id` INTEGER PRIMARY KEY, `identity_hash` TEXT)")
        database.execSQL("INSERT OR REPLACE INTO `room_master_table` (`id`, `identity_hash`) VALUES (42, '6cab9de3bca4b59fea6a8805cac7d10c')")
        database.version = 1
    }
}
