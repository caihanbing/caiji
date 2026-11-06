package app.caiji.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.caiji.android.update.UpdateState
import app.caiji.android.update.GitHubRelease
import app.caiji.android.update.ReleaseVersion
import app.caiji.android.ui.theme.CaijiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsExposeSalaryManagementBelowLedgerSection() {
        var selected = ""
        compose.setContent {
            CaijiTheme {
                SettingsScreen({}, {}, {}, onSalaryImport = { selected = "导入 Excel" },
                    onSalaryExport = { selected = "导出 Excel" }, onSalaryTypes = { selected = "收入项管理" })
            }
        }
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-salary"))
        compose.onNodeWithTag("settings-salary").assertIsDisplayed()
        compose.onNodeWithText("账单").assertIsDisplayed()
        compose.onNodeWithText("工资").assertIsDisplayed()
        compose.onNodeWithText("导入 Excel").performClick()
        assertEquals("导入 Excel", selected)
        compose.onNodeWithText("导出 Excel").performClick()
        assertEquals("导出 Excel", selected)
        compose.onNodeWithText("收入项管理").performClick()
        assertEquals("收入项管理", selected)
    }

    @Test fun settingsCopyExplainsLocalDataBackupAndManualFeedback() {
        compose.setContent { CaijiTheme { SettingsScreen({}, {}, {}) } }
        compose.onNodeWithText("管理账单、工资与应用偏好").assertIsDisplayed()
        compose.onNodeWithText("数据保存在本机").assertIsDisplayed()
        compose.onNodeWithText("账单、工资与问题记录保存在此设备").assertIsDisplayed()
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-backup-section"))
        compose.onNodeWithText("账单备份与恢复").assertIsDisplayed()
        compose.onNodeWithTag("settings-root").performScrollToNode(hasText("加密备份仅包含全部账单和分类", substring = true))
        compose.onNodeWithText("加密备份仅包含全部账单和分类", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("settings-root").performScrollToNode(hasText("恢复将替换当前账单和分类", substring = true))
        compose.onNodeWithText("恢复将替换当前账单和分类", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-feedback"))
        compose.onNodeWithText("记录问题和截图，按需分享").assertIsDisplayed()
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-check-updates"))
        compose.onNodeWithText("检查 GitHub 上的新版本").assertIsDisplayed()
    }

    @Test fun settingsExposeManualCheckAndDisableItWhileChecking() {
        var checks = 0
        compose.setContent {
            CaijiTheme {
                SettingsScreen({}, {}, {}, updateState = UpdateState.Checking, onCheckUpdates = { checks++ })
            }
        }
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-check-updates"))
        compose.onNodeWithTag("settings-check-updates").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("正在检查…").assertIsDisplayed()
        assertEquals(0, checks)
    }

    @Test fun checkingEntryCallsManualCheck() {
        var checks = 0
        compose.setContent {
            CaijiTheme {
                SettingsScreen({}, {}, {}, onCheckUpdates = { checks++ })
            }
        }
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-check-updates"))
        compose.onNodeWithTag("settings-check-updates").performClick()
        assertEquals(1, checks)
    }

    @Test fun manualCheckFailureAndLatestResultsAreShownInSettings() {
        val updateState = mutableStateOf<UpdateState>(UpdateState.Failed)
        compose.setContent {
            CaijiTheme {
                SettingsScreen({}, {}, {}, updateState = updateState.value)
            }
        }
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-check-updates"))
        compose.onNodeWithText("检查失败，请重试").assertIsDisplayed()
        compose.runOnIdle { updateState.value = UpdateState.UpToDate }
        compose.onNodeWithTag("settings-root").performScrollToNode(hasTestTag("settings-check-updates"))
        compose.onNodeWithText("已是最新版本").assertIsDisplayed()
    }

    @Test fun availableUpdateShowsReleaseNotesAndDownloadAction() {
        var downloads = 0
        var dismissals = 0
        val tag = "v0.5.0"
        compose.setContent {
            CaijiTheme {
                UpdateAvailableDialog(
                    currentVersion = "0.4.2",
                    release = GitHubRelease(tag, ReleaseVersion.parse(tag)!!, "修复问题", "https://github.com/caihanbing/caiji/releases/tag/v0.5.0", null),
                    onDismiss = { dismissals++ }, onDownload = { downloads++ }
                )
            }
        }
        compose.onNodeWithText("当前版本 0.4.2 · 最新版本 0.5.0").assertIsDisplayed()
        compose.onNodeWithText("修复问题").assertIsDisplayed()
        compose.onNodeWithText("下载更新").performClick()
        assertEquals(1, downloads)
        assertEquals(0, dismissals)
    }
}
