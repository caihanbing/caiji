package app.caiji.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        compose.onNodeWithText("完成").assertIsDisplayed()
    }

    @Test fun categoryManagementFromEntryPreservesDraftAndUsesCurrentType() {
        val categoryName = "记账页分类-${System.currentTimeMillis()}"
        val note = "保留草稿-${System.currentTimeMillis()}"
        compose.onNodeWithTag("nav-entry").performClick()
        compose.onNodeWithTag("entry-type-income").performClick()
        listOf("1", "2", "3").forEach { compose.onNodeWithTag("entry-key-$it").performClick() }
        val dateBefore = compose.onNodeWithTag("entry-date").fetchSemanticsNode().config[SemanticsProperties.Text]
        compose.onNodeWithTag("entry-note").performTextInput(note)

        compose.onNodeWithTag("entry-manage-categories").performClick()
        compose.onNodeWithText("分类管理").assertIsDisplayed()
        compose.onNodeWithText("工资").assertIsDisplayed()
        compose.onAllNodesWithText("餐饮").assertCountEquals(0)
        compose.onNodeWithText("＋ 添加").performClick()
        compose.onNodeWithText("分类名称").performTextInput(categoryName)
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("‹ 返回").performClick()

        compose.onNodeWithTag("entry-type-income").assertIsSelected()
        compose.onNodeWithTag("amount-display").assertTextEquals("123.00")
        compose.onNodeWithTag("entry-note").assertTextEquals(note)
        assertEquals(dateBefore, compose.onNodeWithTag("entry-date").fetchSemanticsNode().config[SemanticsProperties.Text])
        compose.onNodeWithText(categoryName).assertIsDisplayed()
        compose.onNodeWithText("请选择分类").assertIsDisplayed()
        compose.onNodeWithText(categoryName).performClick()
        compose.onAllNodesWithText("请选择分类").assertCountEquals(0)

        compose.onNodeWithTag("entry-manage-categories").performClick()
        compose.onNodeWithText("‹ 返回").performClick()
        compose.onAllNodesWithText("请选择分类").assertCountEquals(0)
        compose.onNodeWithTag("entry-manage-categories").performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(categoryName).assertIsDisplayed()
        compose.onNodeWithTag("entry-manage-categories").performClick()
        compose.onNodeWithTag("category-active-name-$categoryName").performClick()
        compose.onNodeWithText("‹ 返回").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("该分类已停用，请重新选择启用分类").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("该分类已停用，请重新选择启用分类").assertIsDisplayed()
        compose.onNodeWithTag("amount-display").assertTextEquals("123.00")
        compose.onNodeWithTag("entry-note").assertTextEquals(note)
    }

    @Test fun anExistingEntryCanSwitchFromExpenseToIncomeAndBeDeleted() {
        val note = "ui-transfer-${System.currentTimeMillis()}"
        compose.onNodeWithTag("nav-entry").performClick()
        compose.onNodeWithTag("entry-category-seed-expense-0").performClick()
        compose.onNodeWithTag("entry-note").performTextInput(note)
        listOf("1", ".", "2", "3").forEach { compose.onNodeWithTag("entry-key-$it").performClick() }
        compose.onNodeWithTag("entry-key-complete").performClick()
        compose.onNodeWithText(note).assertIsDisplayed()
        compose.onNodeWithText(note).performClick()
        compose.onNodeWithTag("entry-type-income").performClick()
        compose.onNodeWithTag("entry-category-seed-income-0").performClick()
        compose.onNodeWithTag("entry-key-complete").performClick()
        compose.onNodeWithText("+1.23").assertIsDisplayed()

        compose.onNodeWithText(note).performClick()
        compose.onNodeWithText("删除").performClick()
        compose.onNodeWithTag("confirm-delete-entry").performClick()
        compose.onNodeWithTag("nav-ledger").assertIsDisplayed()
    }

    @Test fun rejectedCategorySaveKeepsTheDialogOpenForCorrection() {
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-categories").performClick()
        compose.onNodeWithText("＋ 添加").performClick()
        compose.onNodeWithText("分类名称").performTextInput("餐饮")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("同类型分类名称不能重复").assertIsDisplayed()
        compose.onNodeWithText("分类名称").assertIsDisplayed()
    }
}
