package app.caiji.android.domain

private val thresholdPattern = Regex("[0-9]+(?:\\.[0-9]{1,2})?")

/** Parses a yuan threshold into fen. Invalid or blank input returns null. */
fun parseExpenseNoteThreshold(input: String): Long? {
    if (!thresholdPattern.matches(input)) return null
    val parts = input.split('.')
    val yuan = parts[0].toLongOrNull() ?: return null
    val fen = when (parts.getOrNull(1)?.length ?: 0) {
        0 -> 0L
        1 -> parts[1].toLong() * 10
        else -> parts[1].toLong()
    }
    return runCatching { Math.addExact(Math.multiplyExact(yuan, 100L), fen) }.getOrNull()
}

fun requiresExpenseNote(type: EntryType, amountFen: Long, thresholdFen: Long?): Boolean =
    type == EntryType.EXPENSE && thresholdFen != null && amountFen >= thresholdFen

fun requireExpenseNote(type: EntryType, amountFen: Long, note: String?, thresholdFen: Long?) {
    require(!requiresExpenseNote(type, amountFen, thresholdFen) || !note.isNullOrBlank()) {
        "支出达到最低金额，请填写备注"
    }
}
