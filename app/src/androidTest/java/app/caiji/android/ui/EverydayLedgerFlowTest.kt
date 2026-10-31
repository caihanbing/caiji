package app.caiji.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.MainActivity
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
        compose.onNodeWithText("支出").assertIsDisplayed()
        compose.onNodeWithText("收入").assertIsDisplayed()
        compose.onNodeWithText("完成").assertIsDisplayed()
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
