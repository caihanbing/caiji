package app.caiji.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.caiji.android.backup.BackupSummary
import app.caiji.android.ui.theme.CaijiPalette

@Composable
internal fun BackupPasswordDialog(
    title: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val meetsRule = password.codePointCount(0, password.length) >= 8
    val matches = !requireConfirmation || password == confirmation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("至少 8 个字符的密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("backup-password")
                )
                if (requireConfirmation) OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = { Text("再次输入密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("backup-password-confirmation")
                )
                Text(
                    "请妥善保存密码；忘记密码后无法打开备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = CaijiPalette.SecondaryText
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password, confirmation.takeIf { requireConfirmation }) },
                enabled = meetsRule && matches,
                colors = ButtonDefaults.buttonColors(containerColor = CaijiPalette.Primary, contentColor = CaijiPalette.OnPrimary),
                modifier = Modifier.testTag("backup-password-confirm")
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
internal fun RestoreSummaryDialog(summary: BackupSummary, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("确认恢复", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("备份包含 ${summary.entryCount} 笔账单、${summary.categoryCount} 个分类")
                Surface(color = CaijiPalette.PrimarySoft, shape = RoundedCornerShape(13.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("收入 ${currency(summary.incomeFen)} · 支出 ${currency(summary.expenseFen)}", fontWeight = FontWeight.Medium)
                        Text("金额采用备份中的账单数据汇总。", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    "确认后会替换当前账单。若本机已有数据，应用会先要求另存一份加密备份。",
                    color = CaijiPalette.SecondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = CaijiPalette.Primary, contentColor = CaijiPalette.OnPrimary),
                modifier = Modifier.testTag("restore-summary-confirm")
            ) { Text("确认恢复") }
        },
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.testTag("restore-summary-cancel")) { Text("取消") } }
    )
}

@Composable
internal fun BackupProcessingDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("恢复账单", fontWeight = FontWeight.SemiBold) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("backup-processing"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(color = CaijiPalette.Primary)
                Text(message, color = CaijiPalette.Ink)
            }
        },
        confirmButton = {}
    )
}

internal enum class FeedbackTone { SUCCESS, NOTICE, ERROR }

internal fun backupFeedbackTone(message: String, isModelError: Boolean = false): FeedbackTone = when {
    isModelError -> FeedbackTone.ERROR
    message.contains("已取消") || message.contains("未更改") -> FeedbackTone.NOTICE
    message == "加密备份已保存" || message == "工资 Excel 已保存" || message == "账单已恢复" || message == "恢复前备份已保存，账单已恢复" -> FeedbackTone.SUCCESS
    else -> FeedbackTone.ERROR
}

@Composable
internal fun BackupFeedbackBanner(message: String, tone: FeedbackTone, onDismiss: () -> Unit) {
    val (container, foreground, prefix) = when (tone) {
        FeedbackTone.SUCCESS -> Triple(CaijiPalette.IncomeSoft, CaijiPalette.Income, "完成")
        FeedbackTone.NOTICE -> Triple(CaijiPalette.PrimarySoft, CaijiPalette.Ink, "提示")
        FeedbackTone.ERROR -> Triple(CaijiPalette.DangerSoft, CaijiPalette.Danger, "出错")
    }
    Surface(
        color = container,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("backup-feedback")
    ) {
        Row(Modifier.padding(start = 13.dp, end = 4.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$prefix  ·  $message", modifier = Modifier.weight(1f), color = foreground, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text("关闭", color = foreground) }
        }
    }
}

private fun currency(fen: java.math.BigInteger): String = "¥${formatYuan(fen)}"
