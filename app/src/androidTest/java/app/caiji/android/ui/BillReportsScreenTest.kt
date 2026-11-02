package app.caiji.android.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.data.LedgerPeriodTotals
import app.caiji.android.data.MonthLedgerTotal
import app.caiji.android.data.CategoryPeriodTotal
import app.caiji.android.data.PeriodLedgerReport
import app.caiji.android.data.LedgerEntry
import app.caiji.android.domain.Category
import app.caiji.android.domain.EntryType
import app.caiji.android.ui.theme.CaijiTheme
import java.math.BigInteger
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BillReportsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun monthlyAndYearlyBillsCanSwitchByHorizontalSwipe() {
        compose.setContent {
            CaijiTheme {
                var mode by remember { mutableStateOf(BillListMode.MONTH) }
                BillReportsScreen(
                    mode = mode, year = 2026, monthReports = emptyList(), yearReports = emptyList(),
                    loading = false, error = null, onBack = {}, onPreviousYear = {}, onNextYear = {},
                    onRetryList = {}, onSelectMode = { mode = it }, onOpenMonth = {}, onOpenYear = {}
                )
            }
        }
        compose.onNodeWithTag("bill-mode-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("全部账单总结余").assertIsDisplayed()
    }

    @Test fun expenseAndIncomeRankingsCanSwitchByHorizontalSwipe() {
        compose.setContent {
            CaijiTheme {
                var type by remember { mutableStateOf(EntryType.EXPENSE) }
                BillPeriodReportScreen(
                    year = 2026, monthValue = 0,
                    report = PeriodLedgerReport(java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2027, 1, 1),
                        totals(100, 0), emptyList(), emptyList()),
                    loading = false, error = null, selectedType = type, selectedCategoryId = null,
                    onBack = {}, onRetry = {}, onSelectType = { type = it }, onSelectCategory = {}, onEditEntry = {}
                )
            }
        }
        compose.onNodeWithTag("report-ranking-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("此期间暂无收入").assertIsDisplayed()
    }

    @Test fun monthAndYearListsUseReferenceTitlesAndOpenTheSelectedPeriod() {
        val opened = AtomicReference<String>()
        compose.setContent {
            CaijiTheme {
                var mode by remember { mutableStateOf(BillListMode.MONTH) }
                BillReportsScreen(
                    mode = mode,
                    year = 2026,
                    monthReports = listOf(MonthLedgerTotal(YearMonth.of(2026, 10), totals(100, 500))),
                    yearReports = listOf(2026 to totals(100, 500), 2025 to totals(300, 700)),
                    loading = false,
                    error = null,
                    onBack = {}, onPreviousYear = {}, onNextYear = {}, onRetryList = {}, onSelectMode = { mode = it },
                    onOpenMonth = { opened.set("2026-${it.monthValue}") },
                    onOpenYear = { opened.set("year-$it") }
                )
            }
        }

        compose.onNodeWithText("月账单").assertIsDisplayed()
        compose.onNodeWithTag("bill-selected-year").assertIsDisplayed()
        compose.onNodeWithText("10月").assertIsDisplayed().performClick()
        assertEquals("2026-10", opened.get())
        compose.onNodeWithTag("bill-tab-year").performClick()
        compose.onAllNodesWithTag("bill-selected-year").assertCountEquals(0)
        compose.onNodeWithText("全部账单总结余").assertIsDisplayed()
        compose.onNodeWithText("2025年").assertIsDisplayed().performClick()
        assertEquals("year-2025", opened.get())
    }

    @Test
    fun compactBillTableScrollsAsOneAndKeepsRowsClickable() {
        val opened = AtomicReference<String>()
        compose.setContent {
            CaijiTheme {
                Box(Modifier.width(320.dp).height(700.dp)) {
                    BillReportsScreen(
                        mode = BillListMode.MONTH, year = 2026,
                        monthReports = listOf(
                            MonthLedgerTotal(YearMonth.of(2026, 10), totals(100, 500)),
                            MonthLedgerTotal(YearMonth.of(2026, 9), totals(200, 800))
                        ), yearReports = listOf(2026 to totals(300, 1300)),
                        loading = false, error = null, onSelectMode = {}, onBack = {}, onPreviousYear = {},
                        onNextYear = {}, onRetryList = {},
                        onOpenMonth = { opened.set("month-${it.monthValue}") }, onOpenYear = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("bill-table-header-balance").assertIsDisplayed()
        compose.onNodeWithTag("bill-table-scroll").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("bill-table-header-income").assertIsDisplayed()
        compose.onNodeWithTag("month-row-10").assertIsDisplayed().performClick()
        assertEquals("month-10", opened.get())
    }

    @Test fun monthlyBalanceTextEndsAtTheEighteenDpContentEdge() {
        assertBalanceTextRightEdge(BillListMode.MONTH, "¥390,795.53")
    }

    @Test fun yearlyBalanceTextEndsAtTheEighteenDpContentEdge() {
        assertBalanceTextRightEdge(
            BillListMode.YEAR, "¥391,323.53", expenseFen = 15_772, incomeFen = 39_148_125
        )
    }

    @Test fun zeroBalanceTextEndsAtTheEighteenDpContentEdge() {
        assertBalanceTextRightEdge(BillListMode.MONTH, "¥0.00", expenseFen = 0, incomeFen = 0)
    }

    @Test fun negativeBalanceTextEndsAtTheEighteenDpContentEdge() {
        assertBalanceTextRightEdge(BillListMode.YEAR, "−¥528.00", expenseFen = 66_600, incomeFen = 13_800)
    }

    private fun assertBalanceTextRightEdge(
        mode: BillListMode,
        value: String,
        expenseFen: Long = 1_972,
        incomeFen: Long = 39_081_525
    ) {
        val density = AtomicReference(1f)
        compose.setContent {
            val currentDensity = LocalDensity.current
            density.set(currentDensity.density)
            CaijiTheme {
                Box(Modifier.width(393.dp).height(1000.dp).testTag("bill-alignment-viewport")) {
                    val totals = LedgerPeriodTotals(BigInteger.valueOf(expenseFen), BigInteger.valueOf(incomeFen))
                    BillReportsScreen(
                        mode = mode, year = 2026,
                        monthReports = listOf(MonthLedgerTotal(YearMonth.of(2026, 12), totals)),
                        yearReports = listOf(2026 to totals), loading = false, error = null,
                        onSelectMode = {}, onBack = {}, onPreviousYear = {}, onNextYear = {}, onRetryList = {},
                        onOpenMonth = {}, onOpenYear = {}
                    )
                }
            }
        }
        val viewport = compose.onNodeWithTag("bill-alignment-viewport").fetchSemanticsNode().boundsInRoot
        val rowTag = if (mode == BillListMode.MONTH) "month-row-12" else "year-row-2026"
        compose.onNodeWithTag(rowTag).assertIsDisplayed()
        val balanceNodes = compose.onAllNodes(
            hasText(value).and(hasParent(hasTestTag(rowTag))), useUnmergedTree = true
        ).fetchSemanticsNodes()
        assertTrue("Expected the balance amount in the selected row", balanceNodes.isNotEmpty())
        val balance = balanceNodes.last().boundsInRoot
        val rendered = compose.onRoot().captureToImage().toPixelMap()
        val scanTop = balance.top.toInt().coerceAtLeast(0)
        val scanBottom = balance.bottom.toInt().coerceAtMost(rendered.height - 1)
        var rightmostInk = -1
        for (y in scanTop..scanBottom) {
            for (x in balance.left.toInt().coerceAtLeast(0)..balance.right.toInt().coerceAtMost(rendered.width - 1)) {
                val pixel = rendered[x, y]
                if (pixel.red < .28f && pixel.green < .28f && pixel.blue < .28f) rightmostInk = maxOf(rightmostInk, x)
            }
        }
        assertTrue("Balance text should have rendered pixels", rightmostInk >= 0)
        assertEquals(viewport.right - 18f * density.get(), rightmostInk.toFloat(), 3f * density.get())
    }

    @Test
    fun reportSummaryUsesSharedCardAndPeriodBalanceTitle() {
        compose.setContent {
            CaijiTheme {
                BillPeriodReportScreen(
                    year = 2026, monthValue = 11,
                    report = PeriodLedgerReport(
                        java.time.LocalDate.of(2026, 11, 1), java.time.LocalDate.of(2026, 12, 1),
                        totals(300, 1000), emptyList(), emptyList()
                    ),
                    loading = false, error = null, selectedType = EntryType.EXPENSE, selectedCategoryId = null,
                    onBack = {}, onRetry = {}, onSelectType = {}, onSelectCategory = {}, onEditEntry = {}
                )
            }
        }
        compose.onNodeWithTag("period-summary-card").assertIsDisplayed()
        compose.onNodeWithText("2026年11月结余").assertIsDisplayed()
    }

    @Test fun compactBillListOmitsIrrelevantEmptyDetail() {
        compose.setContent {
            CaijiTheme {
                BillReportsScreen(
                    mode = BillListMode.MONTH,
                    year = 2026, monthReports = emptyList(), yearReports = emptyList(), loading = false, error = null,
                    onBack = {}, onPreviousYear = {}, onNextYear = {}, onRetryList = {}, onSelectMode = {},
                    onOpenMonth = {}, onOpenYear = {}
                )
            }
        }
        compose.onAllNodesWithText("暂无账单数据").assertCountEquals(0)
        compose.onNodeWithText("2026年").assertIsDisplayed()
    }

    @Test fun billYearCanBrowseEmptyYearsBackTo1900AndStopsAtCurrentYear() {
        val previous = AtomicReference(false)
        val next = AtomicReference(false)
        compose.setContent {
            CaijiTheme {
                var year by remember { mutableStateOf(1900) }
                BillReportsScreen(
                    mode = BillListMode.MONTH, year = year, monthReports = emptyList(), yearReports = emptyList(),
                    loading = false, error = null, onBack = {}, onPreviousYear = { previous.set(true) },
                    onNextYear = { next.set(true); year++ }, onRetryList = {}, onSelectMode = {},
                    onOpenMonth = {}, onOpenYear = {}
                )
            }
        }
        compose.onNodeWithTag("bill-previous-year").assertIsNotEnabled()
        compose.onNodeWithTag("bill-next-year").assertIsEnabled().performClick()
        assertEquals(true, next.get())
        compose.onNodeWithTag("bill-selected-year").assertTextContains("1901年")
        compose.onNodeWithTag("bill-previous-year").assertIsEnabled()
        assertEquals(false, previous.get())
    }

    @Test fun failedMonthListRetryRequestsTheCurrentYearAgain() {
        val retryYear = AtomicReference<Int>()
        compose.setContent {
            CaijiTheme {
                BillReportsScreen(
                    mode = BillListMode.MONTH, year = 2024, monthReports = emptyList(), yearReports = emptyList(),
                    loading = false, error = "读取月账单失败", onSelectMode = {},
                    onBack = {}, onPreviousYear = {}, onNextYear = {}, onRetryList = { retryYear.set(2024) },
                    onOpenMonth = {}, onOpenYear = {}
                )
            }
        }
        compose.onNodeWithTag("report-retry").performClick()
        assertEquals(2024, retryYear.get())
    }

    @Test fun categoryRankOpensFilteredEntriesAndShowsFailuresWithRetry() {
        val category = Category("food", EntryType.EXPENSE, "餐饮", "restaurant")
        val entry = LedgerEntry("entry-1", EntryType.EXPENSE, 1500, category.id, java.time.LocalDate.of(2026, 10, 2), "午饭")
        val openedCategory = AtomicReference<String>()
        val retried = AtomicReference(false)
        compose.setContent {
            CaijiTheme {
                BillPeriodReportScreen(
                    year = 2026, monthValue = 10,
                    report = PeriodLedgerReport(
                        java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 11, 1),
                        totals(1500, 0), listOf(CategoryPeriodTotal(category, BigInteger.valueOf(1500))), listOf(entry)
                    ),
                    loading = false, error = "查询失败", selectedType = EntryType.EXPENSE, selectedCategoryId = null,
                    onBack = {}, onRetry = { retried.set(true) }, onSelectType = {},
                    onSelectCategory = { openedCategory.set(it ?: "") }, onEditEntry = {}
                )
            }
        }

        compose.onNodeWithText("查询失败").assertIsDisplayed()
        compose.onNodeWithTag("report-retry").performClick()
        assertEquals(true, retried.get())
        compose.onNodeWithTag("report-category-food").performClick()
        assertEquals("food", openedCategory.get())
    }

    @Test fun compactLargeFontAnnualTableKeepsYearRowsAndNavigationVisible() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                CaijiTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        BillReportsScreen(
                            mode = BillListMode.YEAR, year = 2026, monthReports = emptyList(),
                            yearReports = listOf(2026 to LedgerPeriodTotals(BigInteger("12345678901234567890"), BigInteger("12345678901234567891"))),
                            loading = false, error = null, onSelectMode = {},
                            onBack = {}, onPreviousYear = {}, onNextYear = {}, onRetryList = {}, onOpenMonth = {}, onOpenYear = {}
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("bill-tab-year").assertIsDisplayed()
        compose.onNodeWithTag("year-row-2026").assertIsDisplayed()
        compose.onAllNodesWithText("历史总结余").assertCountEquals(0)
        compose.onNodeWithText("全部账单总结余").assertIsDisplayed()
        compose.onAllNodesWithText("123,456,789,012,345,678.91", substring = true).assertCountEquals(2)
    }

    private fun totals(expense: Long, income: Long) = LedgerPeriodTotals(BigInteger.valueOf(expense), BigInteger.valueOf(income))
}
