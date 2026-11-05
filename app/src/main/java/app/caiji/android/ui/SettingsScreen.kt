package app.caiji.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.caiji.android.BuildConfig
import app.caiji.android.ui.theme.CaijiPalette
import app.caiji.android.ui.theme.CaijiThemeId
import app.caiji.android.update.UpdateState

@Composable
internal fun SettingsScreen(
    onCategories: () -> Unit,
    onExport: (String) -> Unit,
    onImport: () -> Unit,
    onTheme: () -> Unit = {},
    onFeedback: () -> Unit = {},
    updateState: UpdateState = UpdateState.Idle,
    onCheckUpdates: () -> Unit = {},
    onLocalDialogVisibilityChange: (Boolean) -> Unit = {}
) {
    var exportPrompt by remember { mutableStateOf(false) }
    if (exportPrompt) BackupPasswordDialog(
        title = "加密完整备份",
        confirmLabel = "选择保存位置",
        requireConfirmation = true,
        onDismiss = { exportPrompt = false; onLocalDialogVisibilityChange(false) },
        onConfirm = { password, _ -> exportPrompt = false; onLocalDialogVisibilityChange(false); onExport(password) }
    )

    Column(
        Modifier.fillMaxSize().testTag("settings-root").verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = CaijiPalette.Ink)
            Text("管理账本与使用偏好", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
        Surface(color = CaijiPalette.Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = CaijiPalette.IncomeSoft, shape = RoundedCornerShape(12.dp)) {
                    Text("本机", modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp), color = CaijiPalette.Income, style = MaterialTheme.typography.labelLarge)
                }
                Column {
                    Text("本机离线账本", fontWeight = FontWeight.SemiBold, color = CaijiPalette.Ink)
                    Text("账单只保存在这台设备", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("账本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card(
                onClick = onCategories,
                modifier = Modifier.fillMaxWidth().testTag("settings-categories"),
                colors = CardDefaults.cardColors(containerColor = CaijiPalette.Surface)
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = CaijiPalette.PrimarySoft, shape = RoundedCornerShape(11.dp)) {
                        CaijiIcon(CaijiIconName.CATEGORY, "分类管理", Modifier.padding(9.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("分类管理", fontWeight = FontWeight.Medium, color = CaijiPalette.Ink)
                        Text("新增、编辑、排序和停用分类", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("使用偏好", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card(
                onClick = onTheme,
                modifier = Modifier.fillMaxWidth().testTag("settings-theme"),
                colors = CardDefaults.cardColors(containerColor = CaijiPalette.Surface)
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = CaijiPalette.PrimarySoft, shape = RoundedCornerShape(11.dp)) {
                        ThemePaletteGlyph(Modifier.padding(9.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("换肤", fontWeight = FontWeight.Medium, color = CaijiPalette.Ink)
                        Text(CaijiPalette.selected.label, color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.headlineMedium)
                }
            }
            Card(
                onClick = onFeedback,
                modifier = Modifier.fillMaxWidth().testTag("settings-feedback"),
                colors = CardDefaults.cardColors(containerColor = CaijiPalette.Surface)
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = CaijiPalette.PrimarySoft, shape = RoundedCornerShape(11.dp)) {
                        Text("✎", Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = CaijiPalette.Primary)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("问题反馈", fontWeight = FontWeight.Medium, color = CaijiPalette.Ink)
                        Text("记录体验中的问题", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("备份与恢复", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = { exportPrompt = true; onLocalDialogVisibilityChange(true) },
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("settings-export"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CaijiPalette.Primary, contentColor = CaijiPalette.OnPrimary)
            ) { Text("加密备份", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("settings-import"),
                shape = RoundedCornerShape(14.dp)
            ) { Text("从备份恢复", color = CaijiPalette.Ink) }
            Text(
                "备份包含全部账单和分类。请设置至少 8 个字符的密码并妥善保存；忘记密码后无法打开备份。恢复会替换当前数据，开始前可先另存当前账本。",
                color = CaijiPalette.SecondaryText,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = CaijiPalette.Divider)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("关于财记", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("财记 ${BuildConfig.VERSION_NAME} · Android 8.0 及以上", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        Card(
            onClick = onCheckUpdates,
            enabled = updateState != UpdateState.Checking,
            modifier = Modifier.fillMaxWidth().testTag("settings-check-updates"),
            colors = CardDefaults.cardColors(containerColor = CaijiPalette.Surface)
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = CaijiPalette.PrimarySoft, shape = RoundedCornerShape(11.dp)) {
                    Text("↻", modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = CaijiPalette.Primary)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("检查更新", fontWeight = FontWeight.Medium, color = CaijiPalette.Ink)
                    val status = when (updateState) {
                        UpdateState.Checking -> "正在检查…"
                        UpdateState.UpToDate -> "已是最新版本"
                        UpdateState.Failed -> "检查失败，请重试"
                        else -> "检查 GitHub 上的最新版本"
                    }
                    Text(status, color = if (updateState == UpdateState.Failed) CaijiPalette.Danger else CaijiPalette.SecondaryText,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (updateState == UpdateState.Checking) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("›", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

@Composable
private fun ThemePaletteGlyph(modifier: Modifier = Modifier) {
    val dots = CaijiPalette.preview(CaijiThemeId.AMBER)
    Canvas(modifier.size(24.dp)) {
        val radius = size.minDimension * .16f
        val centers = listOf(
            Offset(size.width * .35f, size.height * .35f),
            Offset(size.width * .68f, size.height * .4f),
            Offset(size.width * .38f, size.height * .68f),
            Offset(size.width * .68f, size.height * .7f)
        )
        centers.forEachIndexed { index, center -> drawCircle(dots[index], radius, center) }
        drawCircle(CaijiPalette.Primary, size.minDimension * .46f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx()))
    }
}
