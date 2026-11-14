package app.caiji.android.domain

import app.caiji.android.ui.maskThousands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseNoteRequirementTest {
    @Test fun masksThousandsAndAboveWhileKeepingLastThreeDigits() {
        assertEquals("¥999.99", maskThousands("¥999.99"))
        assertEquals("¥*,000.00", maskThousands("¥1,000.00"))
        assertEquals("−¥**,345.67", maskThousands("−¥12,345.67"))
        assertEquals("¥*,***,000.00", maskThousands("¥1,234,000.00"))
    }

    @Test fun parsesNonNegativeThresholdInYuanToFen() {
        assertEquals(0L, parseExpenseNoteThreshold("0"))
        assertEquals(12345L, parseExpenseNoteThreshold("123.45"))
        assertEquals(null, parseExpenseNoteThreshold(""))
        assertEquals(null, parseExpenseNoteThreshold("-1"))
        assertEquals(null, parseExpenseNoteThreshold("1.234"))
        assertEquals(null, parseExpenseNoteThreshold("999999999999999999999"))
    }

    @Test fun requiresNoteForExpenseAtOrAboveThresholdOnly() {
        assertTrue(requiresExpenseNote(EntryType.EXPENSE, 50000, 50000))
        assertTrue(requiresExpenseNote(EntryType.EXPENSE, 60000, 50000))
        assertFalse(requiresExpenseNote(EntryType.EXPENSE, 49999, 50000))
        assertTrue(requiresExpenseNote(EntryType.EXPENSE, 1, 0))
        assertFalse(requiresExpenseNote(EntryType.INCOME, 100000, 50000))
        assertFalse(requiresExpenseNote(EntryType.EXPENSE, 50000, null))
    }

    @Test fun rejectsBlankNoteOnlyWhenRequired() {
        requireExpenseNote(EntryType.EXPENSE, 50000, "餐费", 50000)
        requireExpenseNote(EntryType.EXPENSE, 50000, "   ", 50001)
        requireExpenseNote(EntryType.INCOME, 50000, null, 0)
        listOf(null, "", "  ").forEach { note ->
            try {
                requireExpenseNote(EntryType.EXPENSE, 50000, note, 50000)
                throw AssertionError("Expected a required-note failure")
            } catch (expected: IllegalArgumentException) {
                assertEquals("支出达到最低金额，请填写备注", expected.message)
            }
        }
    }
}
