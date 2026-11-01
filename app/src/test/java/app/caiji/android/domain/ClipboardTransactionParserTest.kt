package app.caiji.android.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardTransactionParserTest {
    private val parser = ClipboardTransactionParser

    @Test fun parsesSalaryAndTakesAmountOnlyFromTransactionAmountField() {
        val result = parser.parse(
            "账户变动提醒\n账号：:*2519\n账户名称：交通银行账户\n交易时间：10月23日18:00\n交易类型：代发工资\n交易金额：25719.49元\n账户余额：3元",
            LocalDate.of(2026, 11, 1)
        )
        assertEquals(EntryType.INCOME, result?.type)
        assertEquals(2_571_949L, result?.amountFen)
        assertEquals(LocalDate.of(2026, 10, 23), result?.date)
    }

    @Test fun parsesPaymentAndHandlesRepeatedSeparatorsAndCurrencyPrefix() {
        val result = parser.parse(
            "交易成功提醒\n交易时间：:11月01日10:26\n交易类型：:网上快捷支付（尾号2091）\n交易金额：:人民币 3.00\n账户余额：677.25元",
            LocalDate.of(2026, 11, 1)
        )
        assertEquals(EntryType.EXPENSE, result?.type)
        assertEquals(300L, result?.amountFen)
        assertEquals(LocalDate.of(2026, 11, 1), result?.date)
    }

    @Test fun assignsPreviousYearToFutureMonthAndFindsLeapYearForFebruary29() {
        assertEquals(LocalDate.of(2025, 12, 31), parser.parse(
            "交易成功提醒\n交易时间：12月31日23:59\n交易类型：网上快捷支付\n交易金额：1元",
            LocalDate.of(2026, 1, 2)
        )?.date)
        assertEquals(LocalDate.of(2024, 2, 29), parser.parse(
            "交易成功提醒\n交易时间：2月29日23:59\n交易类型：网上快捷支付\n交易金额：1元",
            LocalDate.of(2025, 3, 1)
        )?.date)
    }

    @Test fun rejectsUnknownTemplatesInvalidAmountsDatesAndFutureDates() {
        val day = LocalDate.of(2026, 11, 1)
        listOf(
            "其它提醒\n交易时间：10月23日\n交易类型：网上快捷支付\n交易金额：3元",
            "交易成功提醒\n交易时间：11月01日\n交易类型：网上快捷支付\n交易金额：1.999元",
            "交易成功提醒\n交易时间：2月30日\n交易类型：网上快捷支付\n交易金额：3元"
        ).forEach { assertNull(parser.parse(it, day)) }
        assertEquals(67_725L, parser.parse(
            "交易成功提醒\n交易时间：11月01日\n交易类型：网上快捷支付\n交易金额：677.25元\n账户余额：3元", day
        )?.amountFen)
    }

    @Test fun rejectsOverflowAndAmbiguousRequiredFields() {
        val day = LocalDate.of(2026, 11, 1)
        assertNull(parser.parse(
            "交易成功提醒\n交易时间：11月01日\n交易类型：网上快捷支付\n交易金额：999999999999999999999元", day
        ))
        assertNull(parser.parse(
            "交易成功提醒\n交易时间：11月01日\n交易时间：11月02日\n交易类型：网上快捷支付\n交易金额：3元", day
        ))
        assertNull(parser.parse(
            "交易成功提醒\n交易时间：11月01日\n交易类型：网上快捷支付\n交易金额：\n交易金额：3元", day
        ))
    }
}
