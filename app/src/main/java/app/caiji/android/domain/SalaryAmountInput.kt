package app.caiji.android.domain

import java.math.BigDecimal
import java.math.BigInteger

/** Salary-specific amount parsing; unlike ledger amounts, blank and zero values are valid. */
object SalaryAmountInput {
    private val literal = Regex("-?[0-9]+(?:\\.[0-9]{1,2})?")

    fun evaluate(expression: String, allowNegative: Boolean = false): Long {
        if (expression.isBlank()) return 0L
        val operatorIndex = expression.indexOfFirstIndexed { index, character ->
            index > 0 && (character == '+' || character == '-')
        }
        val result = if (operatorIndex < 0) {
            parseLiteral(expression, allowNegative)
        } else {
            require(expression.indexOfFirstIndexed { index, character ->
                index > operatorIndex && (character == '+' || character == '-')
            } < 0) { "每次只能进行一次加减运算" }
            val left = parseLiteral(expression.substring(0, operatorIndex), allowNegative)
            val right = parseLiteral(expression.substring(operatorIndex + 1), false)
            val first = BigInteger.valueOf(left)
            val second = BigInteger.valueOf(right)
            (if (expression[operatorIndex] == '+') first + second else first - second).toLongChecked()
        }
        require(allowNegative || result >= 0) { "金额不能为负数" }
        return result
    }

    private fun parseLiteral(value: String, allowNegative: Boolean): Long {
        require(literal.matches(value)) { "请输入有效金额，最多保留两位小数" }
        require(allowNegative || !value.startsWith('-')) { "金额不能为负数" }
        return BigDecimal(value).movePointRight(2).toBigIntegerExact().toLongChecked()
    }

    private fun BigInteger.toLongChecked(): Long {
        if (this < BigInteger.valueOf(Long.MIN_VALUE) || this > BigInteger.valueOf(Long.MAX_VALUE)) {
            throw ArithmeticException("金额超出可用范围")
        }
        return toLong()
    }

    private inline fun String.indexOfFirstIndexed(predicate: (Int, Char) -> Boolean): Int {
        for (index in indices) if (predicate(index, this[index])) return index
        return -1
    }
}
