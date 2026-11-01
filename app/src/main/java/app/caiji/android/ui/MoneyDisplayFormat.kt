package app.caiji.android.ui

import java.math.BigDecimal
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal fun formatYuan(fen: Long): String = formatYuan(BigInteger.valueOf(fen))

internal fun formatYuan(fen: BigInteger): String {
    val decimal = DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.CHINA))
    return decimal.format(BigDecimal(fen, 2))
}
