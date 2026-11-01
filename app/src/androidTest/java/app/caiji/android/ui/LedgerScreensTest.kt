package app.caiji.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.data.LedgerEntry
import app.caiji.android.domain.Category
import app.caiji.android.domain.EntryType
import app.caiji.android.ui.theme.CaijiTheme
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val expenseCategory = Category(
        id = "fixture-expense",
        type = EntryType.EXPENSE,
        name = "餐饮",
        iconKey = "restaurant"
    )
    private val incomeCategory = Category(
        id = "fixture-income",
        type = EntryType.INCOME,
        name = "工资",
        iconKey = "salary"
    )

    @Test
    fun ledgerShowsMonthlyBalanceAndSignedDailyTotals() {
        val date = LocalDate.of(2026, 11, 1)
        val entries = listOf(
            LedgerEntry("salary", EntryType.INCOME, 850000, incomeCategory.id, date, "十一月薪资", 3, 3),
            LedgerEntry("lunch", EntryType.EXPENSE, 3600, expenseCategory.id, date, "和同事", 2, 2),
            LedgerEntry("coffee", EntryType.EXPENSE, 2200, expenseCategory.id, date, null, 1, 1)
        )

        compose.setContent {
            CaijiTheme {
                LedgerHome(
                    month = YearMonth.of(2026, 11),
                    expenseFen = BigInteger.valueOf(5800),
                    incomeFen = BigInteger.valueOf(850000),
                    entries = entries,
                    categories = listOf(expenseCategory, incomeCategory),
                    onPrevious = {},
                    onNext = {},
                    onEdit = {},
                    onCreateEntry = {}
                )
            }
        }

        compose.onNodeWithText("本月结余").assertIsDisplayed()
        compose.onNodeWithText("¥8,442.00").assertIsDisplayed()
        compose.onNodeWithText("本月收入").assertIsDisplayed()
        compose.onNodeWithText("¥8,500.00").assertIsDisplayed()
        compose.onNodeWithText("本月支出").assertIsDisplayed()
        compose.onNodeWithText("¥58.00").assertIsDisplayed()
        compose.onNodeWithText("支出 ¥58.00 · 收入 ¥8,500.00").assertIsDisplayed()
        compose.onNodeWithText("−¥36.00").assertIsDisplayed()
        compose.onNodeWithText("+¥8,500.00").assertIsDisplayed()
    }

    @Test
    fun emptyMonthOffersStartRecording() {
        val createRequested = AtomicBoolean(false)

        compose.setContent {
            CaijiTheme {
                LedgerHome(
                    month = YearMonth.of(2026, 11),
                    expenseFen = BigInteger.ZERO,
                    incomeFen = BigInteger.ZERO,
                    entries = emptyList(),
                    categories = emptyList(),
                    onPrevious = {},
                    onNext = {},
                    onEdit = {},
                    onCreateEntry = { createRequested.set(true) }
                )
            }
        }

        compose.onNodeWithText("本月还没有账单").assertIsDisplayed()
        compose.onNodeWithText("记下第一笔收支，月度明细会显示在这里").assertIsDisplayed()
        compose.onNodeWithTag("empty-start-entry").performClick()
        assertTrue("Empty-state action should open the existing entry editor", createRequested.get())
    }

    @Test
    fun compactLargeFontKeepsLedgerHeadingOnOneLineAndPrimaryActionVisible() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = 1.3f)) {
                CaijiTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        LedgerHome(
                            month = YearMonth.of(2026, 11),
                            expenseFen = BigInteger.ZERO,
                            incomeFen = BigInteger.ZERO,
                            entries = emptyList(),
                            categories = emptyList(),
                            onPrevious = {},
                            onNext = {},
                            onEdit = {},
                            onCreateEntry = {}
                        )
                    }
                }
            }
        }

        val headingHeight = compose.onNodeWithText("收支明细").fetchSemanticsNode().size.height
        assertTrue("Compact header should keep the ledger title on one line", headingHeight < with(compose.density) { 52.dp.toPx() })
        compose.onNodeWithText("2026年11月").assertIsDisplayed()
        compose.onNodeWithTag("empty-start-entry").assertIsDisplayed()
    }

    @Test
    fun longAmountsAndCategoryNamesStaySeparatedAtCompactWidthAndLargeFont() {
        val longCategoryName = "日常支出特别长的分类名称用于检查省略显示"
        val category = expenseCategory.copy(name = longCategoryName)
        val entry = LedgerEntry(
            id = "very-large-ledger-row",
            type = EntryType.EXPENSE,
            amountFen = Long.MAX_VALUE,
            categoryId = category.id,
            occurredOn = LocalDate.of(2026, 11, 1)
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                CaijiTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        LedgerHome(
                            month = YearMonth.of(2026, 11),
                            expenseFen = BigInteger.valueOf(Long.MAX_VALUE),
                            incomeFen = BigInteger.ZERO,
                            entries = listOf(entry),
                            categories = listOf(category),
                            onPrevious = {},
                            onNext = {},
                            onEdit = {},
                            onCreateEntry = {}
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("monthly-balance").assertIsDisplayed()
        compose.onNodeWithTag("entry-row-${entry.id}").assertIsDisplayed()
        val name = compose.onNodeWithTag("ledger-category-name-${entry.id}", useUnmergedTree = true)
        val amount = compose.onNodeWithTag("ledger-amount-${entry.id}", useUnmergedTree = true)
        name.assertIsDisplayed()
        amount.assertIsDisplayed()
        assertTrue("Category text and amount must not overlap", name.fetchSemanticsNode().boundsInRoot.right <= amount.fetchSemanticsNode().boundsInRoot.left)
        compose.onNodeWithText("11月1日 · 星期日").assertIsDisplayed()
    }
}
