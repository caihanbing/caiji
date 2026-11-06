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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.MainActivity
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

        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("累计纯收入").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("累计纯收入").assertIsDisplayed()
        compose.onNodeWithText("还没有工资记录").assertIsDisplayed()
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
        compose.onNodeWithTag("salary-field-毛收入").performTextClearance()
        compose.onNodeWithTag("salary-field-毛收入").performTextInput("123.45")

        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("salary-field-毛收入").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("salary-field-毛收入").assertTextContains("123.45", substring = true)
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
        compose.onNodeWithTag("amount-display").assertTextEquals("123.00")
        compose.onAllNodesWithTag("entry-note").assertCountEquals(0)
        compose.onNodeWithTag("entry-category-seed-income-0").performClick()
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
        compose.onNodeWithText(note).assertIsDisplayed()
        compose.onNodeWithText(note).performClick()
        compose.onNodeWithTag("amount-display").assertTextEquals("1.23")
        compose.onNodeWithTag("entry-type-income").performClick()
        compose.onNodeWithTag("amount-display").assertTextEquals("1.23")
        compose.onNodeWithTag("entry-category-grid").assertIsDisplayed()
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasTestTag("entry-category-seed-income-0"))
        compose.onNodeWithTag("entry-category-seed-income-0").performClick()
        compose.onNodeWithTag("entry-key-save").performClick()
        compose.onNodeWithText(note).assertTextContains("+¥1.23")

        compose.onNodeWithText(note).performClick()
        compose.onNodeWithContentDescription("删除账单").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("amount-display").performClick()
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

        val month = java.time.YearMonth.now()
        compose.onNodeWithTag("ledger-open-bills").performClick()
        compose.onNodeWithTag("month-row-${month.monthValue}").performClick()
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
        compose.onNodeWithTag("amount-display").performClick()
        compose.onNodeWithTag("entry-key-⌫").performClick()
        compose.onNodeWithTag("entry-key-9").performClick()
        compose.onNodeWithTag("entry-key-save").performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("−¥19.00").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(note, substring = true).assertTextContains("−¥19.00")
        compose.onNodeWithText("分类明细").assertIsDisplayed()
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
