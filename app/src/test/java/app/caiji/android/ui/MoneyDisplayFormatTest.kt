package app.caiji.android.ui

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyDisplayFormatTest {
    @Test
    fun formatYuanAlwaysGroupsThousandsAndUsesTwoFractionDigits() {
        assertEquals("36.00", formatYuan(3600L))
        assertEquals("8,500.00", formatYuan(850000L))
        assertEquals(
            "92,233,720,368,547,758.08",
            formatYuan(BigInteger("9223372036854775808"))
        )
    }
}
