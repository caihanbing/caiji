package app.caiji.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.backup.BackupSummary
import app.caiji.android.ui.theme.CaijiTheme
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDialogsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exportPasswordRequiresEightCharactersAndMatchingConfirmation() {
        var confirmed: Pair<String, String?>? = null
        compose.setContent {
            CaijiTheme {
                BackupPasswordDialog("加密完整备份", "选择保存位置", true, {}, { password, confirmation ->
                    confirmed = password to confirmation
                })
            }
        }

        compose.onNodeWithTag("backup-password-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("backup-password").performTextInput("1234567")
        compose.onNodeWithTag("backup-password-confirmation").performTextInput("1234567")
        compose.onNodeWithTag("backup-password-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("backup-password").performTextInput("8")
        compose.onNodeWithTag("backup-password-confirmation").performTextInput("8")
        compose.onNodeWithTag("backup-password-confirm").assertIsEnabled().performClick()
        assertEquals("12345678" to "12345678", confirmed)
    }

    @Test fun restorePreviewShowsSummaryAndCancellationDoesNotConfirm() {
        var confirmed = false
        var cancelled = false
        val summary = BackupSummary(16, 2, BigInteger.valueOf(5800), BigInteger.valueOf(850000), "digest")
        compose.setContent {
            CaijiTheme {
                RestoreSummaryDialog(summary, { confirmed = true }, { cancelled = true })
            }
        }

        compose.onNodeWithText("备份包含 2 笔账单、16 个分类").assertIsDisplayed()
        compose.onNodeWithText("收入 ¥8,500.00 · 支出 ¥58.00").assertIsDisplayed()
        compose.onNodeWithTag("restore-summary-cancel").performClick()
        assertTrue(cancelled)
        assertEquals(false, confirmed)
    }

    @Test fun processingDialogHasAccessibleStatusLabel() {
        compose.setContent {
            CaijiTheme {
                BackupProcessingDialog("正在恢复账单…")
            }
        }
        compose.onNodeWithTag("backup-processing").assertIsDisplayed()
        compose.onNodeWithText("正在恢复账单…").assertIsDisplayed()
    }

    @Test fun feedbackBannerAnnouncesErrorState() {
        compose.setContent {
            CaijiTheme {
                BackupFeedbackBanner("密码错误，现有账单未更改", FeedbackTone.ERROR, {})
            }
        }
        compose.onNodeWithTag("backup-feedback").assertIsDisplayed()
        compose.onNodeWithText("出错  ·  密码错误，现有账单未更改").assertIsDisplayed()
    }
}
