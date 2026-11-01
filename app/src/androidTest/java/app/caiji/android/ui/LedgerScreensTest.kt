package app.caiji.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
import java.util.concurrent.atomic.AtomicReference
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
        compose.onNodeWithTag("daily-summary-2026-11-01").assertTextContains("支出 ¥58.00", substring = true)
        compose.onNodeWithTag("daily-summary-2026-11-01").assertTextContains("收入 ¥8,500.00", substring = true)
        compose.onNodeWithText("−¥36.00").assertIsDisplayed()
        compose.onNodeWithText("+¥8,500.00").assertIsDisplayed()
    }

    @Test
    fun dailyDateAndTotalsRemainInSeparateHorizontalColumnsOnCompactScreens() {
        val date = LocalDate.of(2026, 11, 1)
        val entries = listOf(
            LedgerEntry("day-expense", EntryType.EXPENSE, 8000, expenseCategory.id, date),
            LedgerEntry("day-income", EntryType.INCOME, 10000, incomeCategory.id, date)
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                CaijiTheme {
                    Box(Modifier.width(320.dp).height(720.dp)) {
                        LedgerHome(
                            month = YearMonth.of(2026, 11), expenseFen = BigInteger.valueOf(8000),
                            incomeFen = BigInteger.valueOf(10000), entries = entries,
                            categories = listOf(expenseCategory, incomeCategory), onPrevious = {}, onNext = {},
                            onEdit = {}, onCreateEntry = {}
                        )
                    }
                }
            }
        }
        val dateNode = compose.onNodeWithTag("daily-date-2026-11-01")
        val totalsNode = compose.onNodeWithTag("daily-summary-2026-11-01")
        dateNode.assertIsDisplayed()
        totalsNode.assertIsDisplayed()
        assertTrue("Daily totals must remain to the right of the date",
            dateNode.fetchSemanticsNode().boundsInRoot.right <= totalsNode.fetchSemanticsNode().boundsInRoot.left)
    }

    @Test
    fun compactDailySummaryKeepsBothLongIncomeAndExpenseValues() {
        val lineScale = AtomicReference(1f)
        val date = LocalDate.of(2026, 11, 1)
        val entries = listOf(
            LedgerEntry("long-day-expense", EntryType.EXPENSE, Long.MAX_VALUE, expenseCategory.id, date),
            LedgerEntry("long-day-income", EntryType.INCOME, Long.MAX_VALUE, incomeCategory.id, date)
        )
        compose.setContent {
            val density = LocalDensity.current
            lineScale.set(density.density * density.fontScale)
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                CaijiTheme {
                    Box(Modifier.width(320.dp).height(720.dp)) {
                        LedgerHome(
                            month = YearMonth.of(2026, 11), expenseFen = BigInteger.valueOf(Long.MAX_VALUE),
                            incomeFen = BigInteger.valueOf(Long.MAX_VALUE), entries = entries,
                            categories = listOf(expenseCategory, incomeCategory), onPrevious = {}, onNext = {},
                            onEdit = {}, onCreateEntry = {}
                        )
                    }
                }
            }
        }
        val summary = compose.onNodeWithTag("daily-summary-2026-11-01")
        summary.assertIsDisplayed()
        summary.assertTextContains("支出 ¥92,233,720,368,547,758.07", substring = true)
        summary.assertTextContains("收入 ¥92,233,720,368,547,758.07", substring = true)
        assertTrue("Both long daily amounts need enough visible lines",
            summary.fetchSemanticsNode().boundsInRoot.height >= 4 * 16 * lineScale.get() * 0.9f)
    }

    @Test
    fun monthControlsUseTransparentFortyEightDpTargets() {
        val densityScale = java.util.concurrent.atomic.AtomicReference(1f)
        compose.setContent {
            densityScale.set(LocalDensity.current.density)
            CaijiTheme {
                LedgerHome(
                    month = YearMonth.of(2026, 11), expenseFen = BigInteger.ZERO, incomeFen = BigInteger.ZERO,
                    entries = emptyList(), categories = emptyList(), onPrevious = {}, onNext = {},
                    onEdit = {}, onCreateEntry = {}
                )
            }
        }
        val previous = compose.onNodeWithTag("month-previous")
        previous.assertIsDisplayed()
        val targetPixels = (48 * densityScale.get()).toInt()
        assertTrue("Previous month target must be at least 48dp", previous.fetchSemanticsNode().size.width >= targetPixels)
        assertTrue("Previous month target must be at least 48dp", previous.fetchSemanticsNode().size.height >= targetPixels)
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

        compose.onAllNodesWithText("收支明细").assertCountEquals(0)
        compose.onAllNodesWithText("按月查看").assertCountEquals(0)
        compose.onNodeWithText("2026年11月").assertIsDisplayed()
        compose.onNodeWithTag("ledger-open-bills").assertIsDisplayed()
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

    @Test fun leftSwipeRequiresConfirmationAndCancelLeavesTheEntryUntouched() {
        val entry = LedgerEntry("swipe-delete", EntryType.EXPENSE, 1200, expenseCategory.id, LocalDate.of(2026, 11, 1))
        val deleted = AtomicBoolean(false)
        compose.setContent {
            CaijiTheme {
                LedgerHome(
                    month = YearMonth.of(2026, 11), expenseFen = BigInteger.valueOf(1200), incomeFen = BigInteger.ZERO,
                    entries = listOf(entry), categories = listOf(expenseCategory), onPrevious = {}, onNext = {},
                    onEdit = {}, onCreateEntry = {}, onDelete = { _, done -> deleted.set(true); done(true) }
                )
            }
        }

        compose.onNodeWithTag("entry-row-swipe-delete").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("ledger-delete-swipe-delete").assertIsDisplayed().performClick()
        compose.onNodeWithText("删除这笔账单？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("entry-row-swipe-delete").assertIsDisplayed()
        assertEquals(false, deleted.get())
    }

    @Test fun confirmedSwipeDeletionCallsTheRepositoryCallbackOnce() {
        val entry = LedgerEntry("swipe-confirm", EntryType.EXPENSE, 1200, expenseCategory.id, LocalDate.of(2026, 11, 1))
        val deleteCount = java.util.concurrent.atomic.AtomicInteger()
        compose.setContent {
            CaijiTheme {
                LedgerHome(
                    month = YearMonth.of(2026, 11), expenseFen = BigInteger.valueOf(1200), incomeFen = BigInteger.ZERO,
                    entries = listOf(entry), categories = listOf(expenseCategory), onPrevious = {}, onNext = {},
                    onEdit = {}, onCreateEntry = {}, onDelete = { _, done -> deleteCount.incrementAndGet(); done(true) }
                )
            }
        }
        compose.onNodeWithTag("entry-row-swipe-confirm").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("ledger-delete-swipe-confirm").performClick()
        compose.onNodeWithTag("ledger-confirm-delete").performClick()
        assertEquals(1, deleteCount.get())
    }
}
