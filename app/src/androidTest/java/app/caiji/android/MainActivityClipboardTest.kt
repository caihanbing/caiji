package app.caiji.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.ui.theme.CaijiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityClipboardTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun foregroundClipboardNotificationOpensPrefilledEntryOnlyOnce() {
        val clip = """交易成功提醒
            交易时间：11月01日10:26
            交易类型：网上快捷支付（尾号2091）
            交易金额：人民币 3.00
            账户余额：677.25元""".trimIndent()
        compose.activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences("clipboard_transaction_imports", Context.MODE_PRIVATE).edit().clear().commit()
            (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("bank transaction", clip))
            activity.readClipboardForForeground()
        }
        compose.waitForIdle()

        compose.onNodeWithTag("entry-type-expense").assertIsDisplayed()
        compose.onNodeWithTag("amount-display").assertTextContains("3.00")
        compose.onNodeWithTag("entry-key-date").assertIsDisplayed()
        compose.onNodeWithTag("entry-key-save").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.readClipboardForForeground() }
        compose.waitForIdle()
        compose.onNodeWithText("本月结余").assertIsDisplayed()
        compose.onNodeWithTag("amount-display").assertDoesNotExist()
    }
}
