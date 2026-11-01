package app.caiji.android.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class CaijiThemeTest {
    @Test
    fun primaryActionForegroundMeetsAmberContrast() {
        val contrast = contrastRatio(CaijiPalette.OnPrimary, CaijiPalette.Primary)
        assertTrue("Amber primary foreground must have 4.5:1 contrast, got $contrast", contrast >= 4.5)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val first = relativeLuminance(foreground)
        val second = relativeLuminance(background)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red) + 0.7152 * linearize(color.green) + 0.0722 * linearize(color.blue)

    private fun linearize(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
    }
}
