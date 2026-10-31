package app.caiji.android.domain

/** Money utilities intentionally use integer fen to avoid floating point rounding. */
object Money {
    private val amountPattern = Regex("[0-9]+(?:\\.[0-9]{1,2})?")

    fun parseYuanToFen(input: String): Long {
        require(amountPattern.matches(input)) { "请输入有效金额，最多保留两位小数" }
        val decimalPoint = input.indexOf('.')
        val yuanText = if (decimalPoint >= 0) input.substring(0, decimalPoint) else input
        val fractionText = if (decimalPoint >= 0) input.substring(decimalPoint + 1) else ""
        val yuan = yuanText.toLongOrNull() ?: throw ArithmeticException("金额超出可用范围")
        val fen = when (fractionText.length) {
            0 -> 0L
            1 -> fractionText.toLong() * 10L
            else -> fractionText.toLong()
        }
        val result = Math.addExact(Math.multiplyExact(yuan, 100L), fen)
        require(result > 0L) { "金额必须大于零" }
        return result
    }

    /** Evaluates the single addition or subtraction operation accepted by the keypad. */
    fun evaluate(expression: String): Long {
        val operatorIndex = expression.indexOfFirstIndexed { index, char -> index > 0 && (char == '+' || char == '-') }
        if (operatorIndex < 0) return parseYuanToFen(expression)
        require(expression.indexOfFirstIndexed { index, char -> index > operatorIndex && (char == '+' || char == '-') } < 0) {
            "每次只能进行一次加减运算"
        }
        val left = parseYuanToFenAllowZero(expression.substring(0, operatorIndex))
        val right = parseYuanToFenAllowZero(expression.substring(operatorIndex + 1))
        val result = if (expression[operatorIndex] == '+') Math.addExact(left, right) else Math.subtractExact(left, right)
        require(result > 0L) { "结果必须大于零" }
        return result
    }

    private fun parseYuanToFenAllowZero(input: String): Long =
        if (input == "0" || input == "0.0" || input == "0.00") 0L else parseYuanToFen(input)

    private inline fun String.indexOfFirstIndexed(predicate: (Int, Char) -> Boolean): Int {
        for (index in indices) if (predicate(index, this[index])) return index
        return -1
    }
}
