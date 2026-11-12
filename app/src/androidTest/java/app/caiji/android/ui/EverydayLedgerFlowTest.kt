package app.caiji.android.ui

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.MainActivity
import app.caiji.android.CaijiApplication
import app.caiji.android.data.RoomLedgerRepository
import java.math.BigInteger
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EverydayLedgerFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun returnToMainNavigationIfLaunchedOnAChildRoute() {
        repeat(8) {
            if (compose.onAllNodesWithTag("nav-entry").fetchSemanticsNodes().isEmpty()) {
                compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
            }
        }
    }

    @Test fun settingsAndBillSummaryNavigationUseConsistentLabels() {
        repeat(5) {
            if (compose.onAllNodesWithTag("nav-settings").fetchSemanticsNodes().isEmpty()) {
                compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            }
        }
        compose.onNodeWithTag("nav-settings").assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("分类管理").assertIsDisplayed()
        compose.onNodeWithTag("settings-root").performScrollToNode(hasText("加密备份"))
        compose.onNodeWithText("加密备份").assertIsDisplayed()
        compose.onNodeWithTag("nav-ledger").performClick()
        compose.onNodeWithTag("nav-ledger").assertTextContains("账单")
        compose.onNodeWithTag("ledger-open-bills").assertTextContains("汇总").performClick()
        compose.onNodeWithTag("bills-back").assertIsDisplayed()
        compose.onNodeWithText("汇总").assertIsDisplayed()
    }

    @Test fun salarySummaryPageSurvivesActivityRecreation() {
        repeat(5) {
            if (compose.onAllNodesWithTag("nav-salary").fetchSemanticsNodes().isEmpty()) {
                compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
            }
        }
        compose.onNodeWithTag("nav-salary").performClick()
        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithText("累计纯收入").assertIsDisplayed()
        compose.onNodeWithText("年份").assertIsDisplayed()

        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("累计纯收入").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("累计纯收入").assertIsDisplayed()
        compose.onNodeWithTag("salary-year-row-${java.time.LocalDate.now().year}").assertTextContains("¥0.00")
    }

    @Test fun salaryHomeYearSurvivesMainNavigationAndActivityRecreation() {
        repeat(5) {
            if (compose.onAllNodesWithTag("nav-salary").fetchSemanticsNodes().isEmpty()) {
                compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
            }
        }
        val priorYear = java.time.LocalDate.now().year - 1
        compose.onNodeWithTag("nav-salary").performClick()
        compose.onNodeWithTag("salary-year-label").performClick()
        compose.onNodeWithTag("salary-year-option-$priorYear").performClick()
        compose.onNodeWithTag("salary-year-label").assertTextEquals("${priorYear}年")

        compose.onNodeWithTag("nav-ledger").performClick()
        compose.onNodeWithTag("nav-chart").performClick()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("nav-salary").performClick()
        compose.onNodeWithTag("salary-year-label").assertTextEquals("${priorYear}年")

        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("salary-year-label").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("salary-year-label").assertTextEquals("${priorYear}年")

        val reportYear = java.time.LocalDate.now().year
        compose.onNodeWithTag("salary-open-summary").performClick()
        compose.onNodeWithTag("salary-year-row-$reportYear").performClick()
        compose.onNodeWithTag("page-title").assertTextEquals("${reportYear}年报告")
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-header-back").performClick()
        compose.onNodeWithTag("salary-year-label").assertTextEquals("${priorYear}年")
    }

    @Test fun salaryAndBillAnnualSummaryBackControlsShareTheSameBounds() {
        repeat(5) {
            if (compose.onAllNodesWithTag("nav-ledger").fetchSemanticsNodes().isEmpty()) {
                compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
            }
        }
        compose.onNodeWithTag("nav-ledger").performClick()
        compose.onNodeWithTag("ledger-open-bills").performClick()
        val billBack = compose.onNodeWithTag("bills-back").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("bills-back").performClick()

        compose.onNodeWithTag("nav-salary").performClick()
        compose.onNodeWithTag("salary-open-summary").performClick()
        val salaryBack = compose.onNodeWithTag("salary-header-back").fetchSemanticsNode().boundsInRoot
        assertTrue("Salary and bill summary back buttons should match horizontally", kotlin.math.abs(billBack.left - salaryBack.left) <= 1f)
        assertTrue("Salary and bill summary back buttons should match vertically", kotlin.math.abs(billBack.top - salaryBack.top) <= 1f)
        assertTrue("Salary and bill summary back targets should have the same size",
            kotlin.math.abs(billBack.width - salaryBack.width) <= 1f && kotlin.math.abs(billBack.height - salaryBack.height) <= 1f)
    }

    @Test fun salaryEditorDraftSurvivesActivityRecreation() {
        repeat(5) {
            if (compose.onAllNodesWithTag("nav-salary").fetchSemanticsNodes().isEmpty()) {
                compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
            }
        }
        compose.onNodeWithTag("nav-salary").performClick()
        compose.onNodeWithText("＋  新增收入").performClick()
        compose.onNodeWithTag("salary-income-noncash-tab").performClick()
        compose.onNodeWithTag("salary-income-type-builtin-4").performClick()
        listOf("1", "2", "3", ".", "4", "5").forEach {
            compose.onNodeWithTag("salary-key-$it").performClick()
        }

        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("salary-field-收入").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("salary-value-收入", useUnmergedTree = true).assertTextContains("123.45", substring = true)
        compose.onNodeWithTag("salary-income-noncash-tab").assertIsSelected()
        compose.onAllNodesWithTag("salary-field-工会费").assertCountEquals(0)
    }

    @Test fun settingsIncomeTypeEditorDialogRestoresItsDraftAfterActivityRecreation() {
        repeat(5) {
            if (compose.onAllNodesWithTag("nav-settings").fetchSemanticsNodes().isEmpty()) {
                compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
            }
        }
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-salary-types"))
        compose.onNodeWithTag("settings-salary-types").performClick()
        compose.onNodeWithTag("salary-income-types-add").performClick()
        compose.onNodeWithTag("salary-income-type-name").performTextInput("重建后保留的收入项")
        compose.onNodeWithTag("salary-income-type-icon-grid").performScrollToNode(
            hasTestTag("category-icon-allowance"))
        compose.onNodeWithTag("category-icon-allowance").performClick()

        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("salary-income-type-name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("添加收入项").assertIsDisplayed()
        compose.onNodeWithTag("salary-income-type-name").assertTextContains("重建后保留的收入项")
        compose.onNodeWithTag("salary-income-type-icon-grid").performScrollToNode(
            hasTestTag("category-icon-allowance"))
        compose.onNodeWithTag("category-icon-allowance").assertIsSelected()
    }

    @Test fun openingTheEntryTabShowsIncomeExpenseAndCalculatorControls() {
        compose.onNodeWithTag("nav-entry").assertTextContains("记一笔").performClick()
        compose.onNodeWithTag("page-title").assertTextContains("记一笔")
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("page-title").fetchSemanticsNode().boundsInRoot
        assertTrue("Entry editor title should be centered", kotlin.math.abs(root.center.x - title.center.x) <= 1f)
        compose.onNodeWithTag("entry-type-expense").assertIsSelected()
        compose.onNodeWithTag("entry-type-income").assertIsNotSelected()
        compose.onAllNodesWithText("请选择分类").assertCountEquals(0)
        compose.onAllNodesWithTag("entry-key-1").assertCountEquals(0)
    }

    @Suppress("DEPRECATION")
    @Test fun activityResizesContentForSystemIme() {
        val adjustMode = compose.activity.window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, adjustMode)
    }

    @Test fun categoryManagementFromEntryPreservesDraftAndUsesCurrentType() {
        val categoryName = "记账页分类-${System.currentTimeMillis()}"
        val note = "保留草稿-${System.currentTimeMillis()}"
        compose.onNodeWithTag("nav-entry").performClick()
        compose.onNodeWithTag("entry-type-income").performClick()
        compose.onNodeWithTag("entry-category-grid").assertExists()
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasTestTag("entry-category-seed-income-0"))
        compose.onNodeWithTag("entry-category-seed-income-0").performClick()
        listOf("1", "2", "3").forEach { compose.onNodeWithTag("entry-key-$it").performClick() }
        val dateBefore = compose.onNodeWithTag("entry-key-date").fetchSemanticsNode().config[SemanticsProperties.Text]
        compose.onNodeWithTag("entry-note").performTextInput(note)

        compose.onNodeWithTag("entry-manage-categories").assertIsDisplayed()
        compose.onNodeWithTag("entry-manage-categories").performClick()
        compose.onNodeWithText("分类管理").assertIsDisplayed()
        compose.onNodeWithText("工资").assertIsDisplayed()
        compose.onAllNodesWithText("餐饮").assertCountEquals(0)
        compose.onNodeWithTag("category-add").performClick()
        compose.onNodeWithText("分类名称").performTextInput(categoryName)
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithTag("category-back").performClick()

        compose.onNodeWithTag("entry-type-income").assertIsSelected()
        compose.onNodeWithTag("entry-note").performImeAction()
        compose.onNodeWithTag("amount-display").assertTextEquals("123.00")
        compose.onNodeWithTag("entry-note").assertTextContains(note)
        assertEquals(dateBefore, compose.onNodeWithTag("entry-key-date").fetchSemanticsNode().config[SemanticsProperties.Text])
        compose.onNodeWithTag("entry-category-grid").assertIsDisplayed()
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasText(categoryName))
        compose.onNodeWithText(categoryName).assertIsDisplayed()
        compose.onNodeWithText(categoryName).performClick()
        compose.onAllNodesWithText("请选择分类").assertCountEquals(0)

        compose.onNodeWithTag("entry-manage-categories").assertIsDisplayed()
        compose.onNodeWithTag("entry-manage-categories").performClick()
        compose.onNodeWithTag("category-back").performClick()
        compose.onAllNodesWithText("请选择分类").assertCountEquals(0)
        compose.onNodeWithTag("entry-manage-categories").assertIsDisplayed()
        compose.onNodeWithTag("entry-manage-categories").performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(categoryName).assertIsDisplayed()
        compose.onNodeWithTag("entry-manage-categories").performClick()
        compose.onNodeWithTag("category-active-name-$categoryName").performClick()
        compose.onNodeWithTag("category-back").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("该分类已停用，请重新选择启用分类").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("该分类已停用，请重新选择启用分类").assertIsDisplayed()
        compose.onNodeWithText("该分类已停用，请重新选择启用分类").assertIsDisplayed()
        compose.onAllNodesWithTag("amount-display").assertCountEquals(0)
        compose.onAllNodesWithTag("entry-note").assertCountEquals(0)
        compose.onNodeWithTag("entry-category-seed-income-0").performClick()
        compose.onNodeWithTag("amount-display").assertTextEquals("123.00")
        compose.onNodeWithTag("entry-note").assertTextContains(note)
    }

    @Test fun anExistingEntryCanSwitchFromExpenseToIncomeAndBeDeleted() {
        val note = "ui-transfer-${System.currentTimeMillis()}"
        compose.onNodeWithTag("nav-entry").performClick()
        compose.onNodeWithTag("entry-category-grid").assertIsDisplayed()
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasTestTag("entry-category-seed-expense-0"))
        compose.onNodeWithTag("entry-category-seed-expense-0").performClick()
        listOf("1", ".", "2", "3").forEach { compose.onNodeWithTag("entry-key-$it").performClick() }
        compose.onNodeWithTag("amount-display").assertTextEquals("1.23")
        compose.onNodeWithTag("entry-note").performTextInput(note)
        compose.onNodeWithTag("entry-note").performImeAction()
        compose.onNodeWithTag("amount-display").assertTextEquals("1.23")
        compose.onNodeWithTag("entry-key-save").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(note).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(note).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(note).performClick()
        compose.onNodeWithTag("amount-display").assertTextEquals("1.23")
        compose.onNodeWithTag("entry-type-income").performClick()
        compose.onAllNodesWithTag("amount-display").assertCountEquals(0)
        compose.onNodeWithTag("entry-category-grid").assertIsDisplayed()
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasTestTag("entry-category-seed-income-0"))
        compose.onNodeWithTag("entry-category-seed-income-0").performClick()
        compose.onNodeWithTag("entry-key-save").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("+¥1.23").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(note).assertTextContains("+¥1.23")

        compose.onNodeWithText(note).performClick()
        compose.onNodeWithContentDescription("删除账单").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("entry-note").assertTextContains(note)
        compose.onNodeWithContentDescription("删除账单").performClick()
        compose.onNodeWithTag("confirm-delete-entry").performClick()
        compose.onNodeWithTag("nav-ledger").assertIsDisplayed()
    }

    @Test fun editingAFilteredReportEntryRefreshesTheReportAndKeepsItsCategoryFilter() {
        val note = "期间报告编辑-${System.currentTimeMillis()}"
        compose.onNodeWithTag("nav-entry").performClick()
        compose.onNodeWithTag("entry-category-grid").assertIsDisplayed()
        compose.onNodeWithTag("entry-category-seed-expense-0").performClick()
        listOf("1", "7").forEach { compose.onNodeWithTag("entry-key-$it").performClick() }
        compose.onNodeWithTag("entry-note").performTextInput(note)
        compose.onNodeWithTag("entry-note").performImeAction()
        compose.onNodeWithTag("entry-key-save").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(note, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav-ledger").performClick()

        val month = java.time.YearMonth.now()
        compose.onNodeWithTag("ledger-open-bills").performClick()
        compose.onNodeWithTag("bill-tab-month").performClick()
        compose.onNodeWithTag("month-row-${month.monthValue}", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("report-category-seed-expense-0").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("分类明细").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(note, substring = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("该分类暂无账单").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("report-retry").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("report-loading").fetchSemanticsNodes().isNotEmpty()
        }
        val empty = compose.onAllNodesWithText("该分类暂无账单").fetchSemanticsNodes().isNotEmpty()
        val failed = compose.onAllNodesWithTag("report-retry").fetchSemanticsNodes().isNotEmpty()
        val loading = compose.onAllNodesWithTag("report-loading").fetchSemanticsNodes().isNotEmpty()
        assertEquals("filtered period should show the newly added ledger entry (empty=$empty error=$failed loading=$loading)", false, empty || failed || loading)
        compose.onNodeWithText(note, substring = true).performClick()
        compose.onNodeWithTag("entry-key-⌫").performClick()
        compose.onNodeWithTag("entry-key-9").performClick()
        compose.onNodeWithTag("entry-key-save").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("−¥19.00").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(note, substring = true).assertTextContains("−¥19.00")
        compose.onNodeWithText("分类明细").assertIsDisplayed()
    }

    @Test fun deletingFromCategoryDetailsKeepsTheFilterAndRefreshesTheReportTotals() {
        lateinit var repository: RoomLedgerRepository
        compose.activityRule.scenario.onActivity { activity ->
            repository = (activity.application as CaijiApplication).ledgerRepository
        }
        val note = "分类明细删除-${System.currentTimeMillis()}"
        compose.onNodeWithTag("nav-entry").performClick()
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasTestTag("entry-category-seed-expense-0"))
        compose.onNodeWithTag("entry-category-seed-expense-0").performClick()
        compose.onNodeWithTag("entry-key-3").performClick()
        compose.onNodeWithTag("entry-note").performTextInput(note)
        compose.onNodeWithTag("entry-note").performImeAction()
        compose.onNodeWithTag("entry-key-save").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(note, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        val entry = runBlocking { repository.allEntries().single { it.note == note } }
        val month = YearMonth.from(entry.occurredOn)
        val balanceBefore = runBlocking { repository.monthTotals(month) }
            .let { it.incomeFen.subtract(it.expenseFen) }
        fun yearBalance(): BigInteger = runBlocking {
            repository.allEntries().filter { it.occurredOn.year == month.year }.fold(BigInteger.ZERO) { total, row ->
                total + if (row.type == app.caiji.android.domain.EntryType.INCOME) BigInteger.valueOf(row.amountFen)
                else BigInteger.valueOf(row.amountFen).negate()
            }
        }
        val yearBalanceBefore = yearBalance()

        compose.onNodeWithTag("nav-ledger").performClick()
        compose.onNodeWithTag("ledger-open-bills").performClick()
        compose.onNodeWithTag("bill-tab-month").performClick()
        compose.onNodeWithTag("month-row-${month.monthValue}").performClick()
        compose.onNodeWithTag("report-category-seed-expense-0").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("report-entry-${entry.id}").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("report-entry-${entry.id}").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("ledger-delete-${entry.id}").performClick()
        compose.onNodeWithTag("report-confirm-delete").performClick()
        compose.waitUntil(5_000) { runBlocking { repository.allEntries().none { it.id == entry.id } } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("report-entry-${entry.id}").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("分类明细").assertIsDisplayed()
        compose.onNodeWithTag("report-category-detail-title").assertTextEquals("餐饮")
        val balanceAfter = runBlocking { repository.monthTotals(month) }
            .let { it.incomeFen.subtract(it.expenseFen) }
        val yearBalanceAfter = yearBalance()
        assertEquals(balanceBefore + BigInteger.valueOf(300L), balanceAfter)
        assertEquals(yearBalanceBefore + BigInteger.valueOf(300L), yearBalanceAfter)
        compose.onNodeWithTag("report-balance").assertTextContains(formatYuan(balanceAfter.abs()), substring = true)
        compose.onNodeWithTag("period-report-back").performClick()
        val remainingCategoryFen = runBlocking { repository.allEntries() }
            .filter { it.categoryId == entry.categoryId && it.type == app.caiji.android.domain.EntryType.EXPENSE && YearMonth.from(it.occurredOn) == month }
            .fold(BigInteger.ZERO) { total, row -> total + BigInteger.valueOf(row.amountFen) }
        compose.waitUntil(5_000) {
            val categoryRankings = compose.onAllNodesWithTag("report-category-seed-expense-0").fetchSemanticsNodes()
            if (remainingCategoryFen.signum() > 0) {
                categoryRankings.isNotEmpty() && runCatching {
                    compose.onNodeWithTag("report-category-seed-expense-0")
                        .assertTextContains("−¥${formatYuan(remainingCategoryFen)}")
                }.isSuccess
            } else compose.onAllNodesWithText("此期间暂无支出").fetchSemanticsNodes().isNotEmpty()
        }
        if (remainingCategoryFen.signum() == 0) compose.onAllNodesWithTag("report-category-seed-expense-0").assertCountEquals(0)
        compose.onNodeWithTag("period-report-back").performClick()
        compose.onNodeWithText("汇总").assertIsDisplayed()
        compose.onNodeWithTag("bill-tab-month").performClick()
        val expectedMonthBalance = (if (balanceAfter.signum() < 0) "−" else "") + "¥" + formatYuan(balanceAfter.abs())
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("month-row-${month.monthValue}", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("month-row-${month.monthValue}").assertTextContains(expectedMonthBalance)
        compose.onNodeWithTag("bill-tab-year").performClick()
        val expectedYearBalance = (if (yearBalanceAfter.signum() < 0) "−" else "") + "¥" + formatYuan(yearBalanceAfter.abs())
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("year-row-${month.year}", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("year-row-${month.year}").assertTextContains(expectedYearBalance)
    }

    @Test fun rejectedCategorySaveKeepsTheDialogOpenForCorrection() {
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-categories").performClick()
        compose.onNodeWithTag("category-add").performClick()
        compose.onNodeWithText("分类名称").performTextInput("餐饮")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("同类型分类名称不能重复").assertIsDisplayed()
        compose.onNodeWithText("分类名称").assertIsDisplayed()
    }
}
