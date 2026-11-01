package app.caiji.android.backup

import app.caiji.android.data.LedgerEntry
import app.caiji.android.data.LedgerRestoreSink
import app.caiji.android.domain.CategoryCatalog
import app.caiji.android.domain.EntryType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val categories = CategoryCatalog.defaults()
    private val entry = LedgerEntry("entry-1", EntryType.EXPENSE, 1234, categories.first().id,
        LocalDate.of(2024, 2, 29), "午餐 · ☀")

    @Test fun encryptsAndRestoresAllEntryAndCategoryFields() = runBlocking {
        val codec = BackupCodec()
        val file = codec.export("12345678", categories, listOf(entry))
        val restored = codec.import(file, "12345678")
        assertEquals(categories, restored.categories)
        assertEquals(listOf(entry), restored.entries)
    }

    @Test fun roundTripsCategoriesUsingNewIconKeysWithoutChangingBackupFormat() = runBlocking {
        val iconKey = CategoryCatalog.iconKeys().last().key
        val categoriesWithNewIcon = categories.mapIndexed { index, category ->
            if (index == 0) category.copy(iconKey = iconKey) else category
        }

        val file = BackupCodec().export("12345678", categoriesWithNewIcon, listOf(entry))
        val restored = BackupCodec().import(file, "12345678")

        assertEquals(2, file[8].toInt())
        assertEquals(iconKey, restored.categories.first().iconKey)
    }

    @Test fun randomSaltAndNonceMakeExportsDifferent() = runBlocking {
        val codec = BackupCodec()
        assertFalse(codec.export("12345678", categories, listOf(entry))
            .contentEquals(codec.export("12345678", categories, listOf(entry))))
    }

    @Test fun rejectsWrongPasswordAndModifiedAuthenticatedFile() = runBlocking {
        val codec = BackupCodec()
        val file = codec.export("12345678", categories, listOf(entry))
        assertThrows(BackupException::class.java) { runBlocking { codec.import(file, "abcdefgh") } }
        val changed = file.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(BackupException::class.java) { runBlocking { codec.import(changed, "12345678") } }
        Unit
    }

    @Test fun rejectsPasswordsBelowEightCharactersAndUnsupportedFormats() = runBlocking {
        val codec = BackupCodec()
        assertThrows(BackupException::class.java) { runBlocking { codec.export("1234567", categories, listOf(entry)) } }
        val file = codec.export("12345678", categories, listOf(entry))
        file[8] = 0x7f
        assertThrows(BackupException::class.java) { runBlocking { codec.import(file, "12345678") } }
        Unit
    }

    @Test fun validatesEntryAmountAndCategoryReferencesBeforeExportAndImport() = runBlocking {
        val codec = BackupCodec()
        assertThrows(BackupException::class.java) { runBlocking { codec.export("12345678", categories, listOf(entry.copy(amountFen = 0))) } }
        assertThrows(BackupException::class.java) { runBlocking { codec.export("12345678", categories, listOf(entry.copy(categoryId = "missing"))) } }
        val file = codec.export("12345678", categories, listOf(entry))
        val restored = codec.import(file, "12345678")
        assertArrayEquals(categories.map { it.id }.toTypedArray(), restored.categories.map { it.id }.toTypedArray())
    }

    @Test fun streamsPreviewAndRestoreAndDetectsChangedContent() = runBlocking {
        val codec = BackupCodec()
        val output = ByteArrayOutputStream()
        codec.exportTo("12345678", categories, listOf(entry), output)
        val file = output.toByteArray()
        val preview = codec.preview(ByteArrayInputStream(file), "12345678")
        assertEquals(16, preview.categoryCount)
        assertEquals(1L, preview.entryCount)

        val restoredCategories = mutableListOf<app.caiji.android.domain.Category>()
        val restoredEntries = mutableListOf<LedgerEntry>()
        val summary = codec.restore(ByteArrayInputStream(file), "12345678", object : LedgerRestoreSink {
            override suspend fun replaceCategories(categories: List<app.caiji.android.domain.Category>) {
                restoredCategories.addAll(categories)
            }

            override suspend fun appendEntries(entries: List<LedgerEntry>) { restoredEntries.addAll(entries) }
        })
        assertEquals(preview, summary)
        assertEquals(categories, restoredCategories)
        assertEquals(listOf(entry), restoredEntries)

        val changedEntry = entry.copy(note = "改过的账单")
        val changed = codec.export("12345678", categories, listOf(changedEntry))
        val changedSummary = codec.preview(ByteArrayInputStream(changed), "12345678")
        assertFalse(preview == changedSummary)
    }

    @Test fun refusesNotesThatWouldExceedTheSupportedBackupFieldLimit() = runBlocking {
        val codec = BackupCodec()
        assertThrows(BackupException::class.java) {
            runBlocking { codec.export("12345678", categories, listOf(entry.copy(note = "x".repeat(20_001)))) }
        }
        Unit
    }
}
