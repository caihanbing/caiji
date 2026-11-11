package app.caiji.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SalaryAmountInputTest {
    @Test fun blankAndZeroValuesEvaluateAsZeroFen() {
        assertEquals(0L, SalaryAmountInput.evaluate(""))
        assertEquals(0L, SalaryAmountInput.evaluate("0.00"))
    }

    @Test fun evaluatesOneAdditionOrSubtractionInFen() {
        assertEquals(12_350L, SalaryAmountInput.evaluate("100+23.50"))
        assertEquals(7_650L, SalaryAmountInput.evaluate("100-23.50"))
    }

    @Test fun allowsNegativeTaxButRejectsNegativeIncomeAndOrdinaryDeduction() {
        assertEquals(-10_000L, SalaryAmountInput.evaluate("-100", allowNegative = true))
        assertThrows(IllegalArgumentException::class.java) {
            SalaryAmountInput.evaluate("-100")
        }
    }

    @Test fun rejectsIncompleteExpressionsExcessPrecisionAndOverflow() {
        listOf("100+", "1.234", "100+20+1").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { SalaryAmountInput.evaluate(value) }
        }
        assertThrows(ArithmeticException::class.java) {
            SalaryAmountInput.evaluate("999999999999999999999999999999999999")
        }
    }
}
