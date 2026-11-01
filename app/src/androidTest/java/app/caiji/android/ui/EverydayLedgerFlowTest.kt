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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EverydayLedgerFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launchesTheMonthlyLedgerAndExposesEntryAndSettingsNavigation() {
        compose.onNodeWithText("本月支出").assertIsDisplayed()
        compose.onNodeWithTag("nav-entry").assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("分类管理").assertIsDisplayed()
        compose.onNodeWithText("加密备份").assertIsDisplayed()
    }

    @Test fun openingTheEntryTabShowsIncomeExpenseAndCalculatorControls() {
        compose.onNodeWithTag("nav-entry").performClick()
        compose.onNodeWithTag("entry-type-expense").assertIsSelected()
        compose.onNodeWithTag("entry-type-income").assertIsNotSelected()
        compose.onNodeWithText("请选择分类").assertIsDisplayed()
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
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-category-grid"))
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasTestTag("entry-category-seed-income-0"))
        compose.onNodeWithTag("entry-category-seed-income-0").performClick()
        listOf("1", "2", "3").forEach { compose.onNodeWithTag("entry-key-$it").performClick() }
        val dateBefore = compose.onNodeWithTag("entry-date").fetchSemanticsNode().config[SemanticsProperties.Text]
        compose.onNodeWithTag("entry-note").performTextInput(note)

        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-manage-categories"))
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
        assertEquals(dateBefore, compose.onNodeWithTag("entry-date").fetchSemanticsNode().config[SemanticsProperties.Text])
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-category-grid"))
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasText(categoryName))
        compose.onNodeWithText(categoryName).assertIsDisplayed()
        compose.onNodeWithText(categoryName).performClick()
        compose.onAllNodesWithText("请选择分类").assertCountEquals(0)

        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-manage-categories"))
        compose.onNodeWithTag("entry-manage-categories").performClick()
        compose.onNodeWithTag("category-back").performClick()
        compose.onAllNodesWithText("请选择分类").assertCountEquals(0)
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-manage-categories"))
        compose.onNodeWithTag("entry-manage-categories").performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(categoryName).assertIsDisplayed()
        compose.onNodeWithTag("entry-manage-categories").performClick()
        compose.onNodeWithTag("category-active-name-$categoryName").performClick()
        compose.onNodeWithTag("category-back").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("该分类已停用，请重新选择启用分类").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasText("该分类已停用，请重新选择启用分类"))
        compose.onNodeWithText("该分类已停用，请重新选择启用分类").assertIsDisplayed()
        compose.onNodeWithTag("amount-display").assertTextEquals("123.00")
        compose.onNodeWithTag("entry-note").assertTextContains(note)
    }

    @Test fun anExistingEntryCanSwitchFromExpenseToIncomeAndBeDeleted() {
        val note = "ui-transfer-${System.currentTimeMillis()}"
        compose.onNodeWithTag("nav-entry").performClick()
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-category-grid"))
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasTestTag("entry-category-seed-expense-0"))
        compose.onNodeWithTag("entry-category-seed-expense-0").performClick()
        listOf("1", ".", "2", "3").forEach { compose.onNodeWithTag("entry-key-$it").performClick() }
        compose.onNodeWithTag("amount-display").assertTextEquals("1.23")
        compose.onNodeWithTag("entry-note").performTextInput(note)
        compose.onNodeWithTag("entry-note").performImeAction()
        compose.onNodeWithTag("amount-display").assertTextEquals("1.23")
        compose.onNodeWithTag("entry-save").performClick()
        compose.onNodeWithText(note).assertIsDisplayed()
        compose.onNodeWithText(note).performClick()
        compose.onNodeWithTag("amount-display").assertTextEquals("1.23")
        compose.onNodeWithTag("entry-type-income").performClick()
        compose.onNodeWithTag("amount-display").assertTextEquals("1.23")
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-category-grid"))
        compose.onNodeWithTag("entry-category-grid").performScrollToNode(hasTestTag("entry-category-seed-income-0"))
        compose.onNodeWithTag("entry-category-seed-income-0").performClick()
        compose.onNodeWithTag("entry-save").performClick()
        compose.onNodeWithText(note).assertTextContains("+¥1.23")

        compose.onNodeWithText(note).performClick()
        compose.onNodeWithContentDescription("删除账单").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-note"))
        compose.onNodeWithTag("entry-note").assertTextContains(note)
        compose.onNodeWithContentDescription("删除账单").performClick()
        compose.onNodeWithTag("confirm-delete-entry").performClick()
        compose.onNodeWithTag("nav-ledger").assertIsDisplayed()
    }

    @Test fun editingAFilteredReportEntryRefreshesTheReportAndKeepsItsCategoryFilter() {
        val note = "期间报告编辑-${System.currentTimeMillis()}"
        compose.onNodeWithTag("nav-entry").performClick()
        compose.onNodeWithTag("entry-editor-scroll").performScrollToNode(hasTestTag("entry-category-grid"))
        compose.onNodeWithTag("entry-category-seed-expense-0").performClick()
        listOf("1", "7").forEach { compose.onNodeWithTag("entry-key-$it").performClick() }
        compose.onNodeWithTag("entry-note").performTextInput(note)
        compose.onNodeWithTag("entry-note").performImeAction()
        compose.onNodeWithTag("entry-save").performClick()
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
        compose.onNodeWithTag("entry-save").performClick()

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
