package app.caiji.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.caiji.android.ui.theme.CaijiTheme
import androidx.compose.ui.graphics.Color
import java.math.BigInteger
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PrivacySummaryCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun summaryMasksSelectedAmountsAndTogglesBackToFullValues() {
        lateinit var hidden: androidx.compose.runtime.MutableState<Boolean>
        compose.setContent {
            CaijiTheme {
                hidden = rememberHiddenState()
                FinancialSummaryCard(
                    primaryLabel = "结余", primaryFen = BigInteger.valueOf(1_234_567),
                    leftLabel = "收入", leftFen = BigInteger.valueOf(123_000), leftColor = Color.Green,
                    rightLabel = "支出", rightFen = BigInteger.valueOf(999_999),
                    primaryTag = "report-balance", leftTag = "report-income",
                    maskPrimaryAmount = hidden.value, maskLeftAmount = hidden.value,
                    onToggleAmounts = { hidden.value = !hidden.value }
                )
            }
        }
        compose.onNodeWithTag("report-balance").assertTextEquals("¥**,345.67")
        compose.onNodeWithTag("report-income").assertTextEquals("¥*,230.00")
        compose.onNodeWithTag("summary-privacy-toggle").assertIsDisplayed()
        val amountBounds = compose.onNodeWithTag("report-balance").fetchSemanticsNode().boundsInRoot
        val toggleBounds = compose.onNodeWithTag("summary-privacy-toggle").fetchSemanticsNode().boundsInRoot
        assertTrue("Privacy toggle should sit to the right of the primary amount", toggleBounds.left >= amountBounds.right)
        assertTrue("Privacy toggle should align vertically with the primary amount",
            kotlin.math.abs(toggleBounds.center.y - amountBounds.center.y) <= toggleBounds.height / 2f)
        compose.onNodeWithContentDescription("显示金额").performClick()
        compose.onNodeWithTag("report-balance").assertTextEquals("¥12,345.67")
        compose.onNodeWithTag("report-income").assertTextEquals("¥1,230.00")
    }

    @androidx.compose.runtime.Composable
    private fun rememberHiddenState() = androidx.compose.runtime.remember { mutableStateOf(true) }
}
