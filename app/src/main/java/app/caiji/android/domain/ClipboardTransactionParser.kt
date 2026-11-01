package app.caiji.android.domain

import java.time.DateTimeException
import java.time.LocalDate

data class ClipboardTransaction(
    val type: EntryType,
    val amountFen: Long,
    val date: LocalDate
)

/** Parses only the two bank notification templates supported by the app. */
object ClipboardTransactionParser {
    private val datePattern = Regex("^(\\d{1,2})月(\\d{1,2})日(?:\\s*\\d{1,2}:\\d{2})?$")
    private val amountPattern = Regex("^(?:人民币\\s*)?([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?:\\s*元)?$")

    fun parse(text: String, today: LocalDate): ClipboardTransaction? {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val type = when {
            lines.firstOrNull()?.trimEnd(':', '：') == "账户变动提醒" &&
                uniqueValue(lines, "交易类型")?.startsWith("代发工资") == true -> EntryType.INCOME
            lines.firstOrNull()?.trimEnd(':', '：') == "交易成功提醒" &&
                uniqueValue(lines, "交易类型")?.startsWith("网上快捷支付") == true -> EntryType.EXPENSE
            else -> return null
        }
        val amountText = uniqueValue(lines, "交易金额") ?: return null
        val amountMatch = amountPattern.matchEntire(amountText) ?: return null
        val amountFen = runCatching { Money.parseYuanToFen(amountMatch.groupValues[1].replace(",", "")) }.getOrNull()
            ?: return null
        val dateText = uniqueValue(lines, "交易时间") ?: return null
        val dateMatch = datePattern.matchEntire(dateText) ?: return null
        val month = dateMatch.groupValues[1].toIntOrNull() ?: return null
        val day = dateMatch.groupValues[2].toIntOrNull() ?: return null
        val date = (today.year downTo 1900).firstNotNullOfOrNull { year ->
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.takeUnless { it.isAfter(today) }
        } ?: return null
        return ClipboardTransaction(type, amountFen, date)
    }

    private fun uniqueValue(lines: List<String>, key: String): String? {
        val matches = lines.mapNotNull { line ->
            Regex("^${Regex.escape(key)}\\s*[:：]+\\s*(.*?)\\s*$").matchEntire(line)
        }
        if (matches.size != 1) return null
        return matches.single().groupValues[1].trim().takeIf(String::isNotEmpty)
    }
}
