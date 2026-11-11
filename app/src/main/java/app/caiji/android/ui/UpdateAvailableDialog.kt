package app.caiji.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.caiji.android.update.AppRelease
import app.caiji.android.ui.theme.CaijiPalette

@Composable
internal fun UpdateAvailableDialog(
    currentVersion: String,
    release: AppRelease,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本") },
        text = {
            Column {
                Text("当前版本 $currentVersion · 最新版本 ${release.tagName.removePrefix("v")}")
                Spacer(Modifier.size(8.dp))
                Text("更新内容")
                Text(
                    release.notes.ifBlank { "此版本包含问题修复和体验优化。" }.take(600),
                    color = CaijiPalette.SecondaryText
                )
            }
        },
        confirmButton = { TextButton(onClick = onDownload) { Text("下载更新") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后再说") } }
    )
}
