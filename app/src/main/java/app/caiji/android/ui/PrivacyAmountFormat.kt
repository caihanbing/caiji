package app.caiji.android.ui

/** Replaces integer digits above the final three, while retaining separators and cents. */
internal fun maskThousands(value: String): String {
    val decimalIndex = value.indexOf('.')
    val integerEnd = if (decimalIndex < 0) value.length else decimalIndex
    val digits = value.take(integerEnd).count(Char::isDigit)
    if (digits <= 3) return value
    val toMask = digits - 3
    var seen = 0
    return buildString(value.length) {
        value.forEachIndexed { index, char ->
            if (index < integerEnd && char.isDigit()) {
                seen++
                append(if (seen <= toMask) '*' else char)
            } else append(char)
        }
    }
}
