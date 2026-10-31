package app.caiji.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    @Test fun parsesWholeYuanAndFractionalFenExactly() {
        assertEquals(12300L, Money.parseYuanToFen("123"))
        assertEquals(1230L, Money.parseYuanToFen("12.3"))
        assertEquals(1234L, Money.parseYuanToFen("12.34"))
    }

    @Test fun rejectsPrecisionBeyondOneFen() {
        assertThrows(IllegalArgumentException::class.java) { Money.parseYuanToFen("0.001") }
    }

    @Test fun rejectsNonPositiveOrMalformedAmounts() {
        listOf("0", "-0.01", "", ".5", "12.").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { Money.parseYuanToFen(value) }
        }
    }

    @Test fun calculatesAdditionAndSubtractionUsingExactFen() {
        assertEquals(1800L, Money.evaluate("12.30+5.70"))
        assertEquals(655L, Money.evaluate("12.30-5.75"))
    }

    @Test fun rejectsNegativeResultsAndOverflow() {
        assertThrows(IllegalArgumentException::class.java) { Money.evaluate("2-3") }
        assertThrows(ArithmeticException::class.java) { Money.parseYuanToFen("92233720368547758.08") }
    }
}
