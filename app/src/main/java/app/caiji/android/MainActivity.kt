package app.caiji.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.caiji.android.backup.BackupCodec
import app.caiji.android.backup.BackupException
import app.caiji.android.backup.BackupSummary
import app.caiji.android.data.LedgerEntry
import app.caiji.android.domain.EntryType
import app.caiji.android.ui.BackupFeedbackBanner
import app.caiji.android.ui.BackupPasswordDialog
import app.caiji.android.ui.BackupProcessingDialog
import app.caiji.android.ui.CaijiIcon
import app.caiji.android.ui.CaijiIconName
import app.caiji.android.ui.backupFeedbackTone
import app.caiji.android.ui.CategoryManagerScreen
import app.caiji.android.ui.EntryEditorScreen
import app.caiji.android.ui.LedgerHome
import app.caiji.android.ui.LedgerViewModel
import app.caiji.android.ui.RestoreSummaryDialog
import app.caiji.android.ui.SettingsScreen
import app.caiji.android.ui.theme.CaijiPalette
import app.caiji.android.ui.theme.CaijiTheme
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(app.caiji.android.R.color.caiji_page_background)
        window.navigationBarColor = getColor(app.caiji.android.R.color.caiji_page_background)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent { CaijiTheme { CaijiRoot() } }
    }
}

private enum class Page { LEDGER, EDITOR, SETTINGS, CATEGORIES }
private enum class RestoreFlow { IDLE, PREVIEW, PREPARING, BACKUP_PASSWORD, SELECTING_BACKUP, PREPARING_BACKUP, WRITING_BACKUP, APPLYING }

@Composable
private fun CaijiRoot(model: LedgerViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val month by model.month.collectAsState()
    val entries by model.entries.collectAsState()
    val categories by model.categories.collectAsState()
    val totals by model.totals.collectAsState()
    val modelError by model.error.collectAsState()
    var page by rememberSaveable { mutableStateOf(Page.LEDGER) }
    var editEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var editEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    LaunchedEffect(editEntryId) {
        editEntry = editEntryId?.let { model.entryById(it) }
    }
    var initialEditorType by remember { mutableStateOf(EntryType.EXPENSE) }
    var exportPassword by remember { mutableStateOf<String?>(null) }
    var restoreCandidate by remember { mutableStateOf<BackupSummary?>(null) }
    var restoreFlow by remember { mutableStateOf(RestoreFlow.IDLE) }
    var restoreBackupPassword by remember { mutableStateOf<String?>(null) }
    var restoreSourcePassword by remember { mutableStateOf<String?>(null) }
    var restorePasswordVisible by remember { mutableStateOf(false) }
    var selectedRestoreUri by remember { mutableStateOf(Uri.EMPTY) }
    var pickerError by remember { mutableStateOf<String?>(null) }

    suspend fun applyRestoreSource(preview: BackupSummary, password: String) {
        val source = selectedRestoreUri
        model.replaceAllForRestore { sink ->
            val actual = context.contentResolver.openInputStream(source)?.use { BackupCodec().restore(it, password, sink) }
                ?: throw BackupException("无法读取备份文件")
            if (actual != preview) throw BackupException("备份文件在预览后发生变化，请重新选择")
        }
    }

    fun clearRestorePreview() {
        val uri = selectedRestoreUri
        selectedRestoreUri = Uri.EMPTY
        restoreCandidate = null
        restoreSourcePassword = null
        restoreFlow = RestoreFlow.IDLE
        if (uri != Uri.EMPTY) runCatching {
            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val password = exportPassword
        exportPassword = null
        if (uri == null || password == null) {
            pickerError = "已取消备份保存，原数据未更改"
        } else scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use {
                        model.exportBackup(password, it)
                    } ?: error("无法写入所选文件")
                }
            }.onSuccess { pickerError = "加密备份已保存" }
                .onFailure { pickerError = "保存失败：${it.message ?: "请重试"}" }
        }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) pickerError = "已取消选择备份文件"
        else {
            if (selectedRestoreUri != Uri.EMPTY) runCatching {
                context.contentResolver.releasePersistableUriPermission(selectedRestoreUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            restoreCandidate = null
            restoreFlow = RestoreFlow.IDLE
            selectedRestoreUri = uri
            restoreSourcePassword = null
            restorePasswordVisible = true
        }
    }

    val preRestorePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val password = restoreBackupPassword
        restoreBackupPassword = null
        val backup = restoreCandidate
        if (uri == null) {
            restoreFlow = RestoreFlow.PREVIEW
            restoreBackupPassword = null
            pickerError = "已取消保存恢复前备份，原数据未更改"
        } else if (password == null || backup == null) {
            restoreFlow = if (backup == null) RestoreFlow.IDLE else RestoreFlow.PREVIEW
            pickerError = "恢复流程已结束，原数据未更改"
        } else {
            restoreFlow = RestoreFlow.PREPARING_BACKUP
            scope.launch {
                var sessionStarted = false
                try {
                    model.beginRestoreSession()
                    sessionStarted = true
                    restoreFlow = RestoreFlow.WRITING_BACKUP
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "w")?.use {
                            model.exportBackup(password, it)
                        }
                            ?: error("无法写入所选文件")
                    }
                    restoreFlow = RestoreFlow.APPLYING
                    val sourcePassword = restoreSourcePassword ?: throw BackupException("恢复密码已失效，请重新选择备份")
                    applyRestoreSource(backup, sourcePassword)
                    clearRestorePreview()
                    pickerError = "恢复前备份已保存，账单已恢复"
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    pickerError = error.message ?: "恢复失败，现有账单未更改"
                    restoreFlow = RestoreFlow.PREVIEW
                } finally {
                    if (sessionStarted) withContext(NonCancellable) { model.endRestoreSession() }
                }
            }
        }
    }

    if (restorePasswordVisible) BackupPasswordDialog("输入备份密码", "解密预览", false,
        onDismiss = { restorePasswordVisible = false; clearRestorePreview(); pickerError = "已取消恢复，原数据未更改" }, onConfirm = { password, _ ->
            restorePasswordVisible = false
            val uri = selectedRestoreUri
            scope.launch {
                try {
                    val summary = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { BackupCodec().preview(it, password) }
                            ?: throw BackupException("无法读取备份文件")
                    }
                    restoreSourcePassword = password
                    restoreCandidate = summary
                    restoreFlow = RestoreFlow.PREVIEW
                    pickerError = null
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    pickerError = error.message ?: "备份无法读取"
                    clearRestorePreview()
                }
            }
        })

    if (restoreFlow == RestoreFlow.BACKUP_PASSWORD) BackupPasswordDialog("保存当前账单备份", "选择保存位置", true,
        onDismiss = { restoreFlow = RestoreFlow.PREVIEW }, onConfirm = { password, _ ->
            restoreBackupPassword = password
            restoreFlow = RestoreFlow.SELECTING_BACKUP
            preRestorePicker.launch("财记-恢复前备份-${LocalDate.now()}.cjbackup")
        })

    if (restoreFlow == RestoreFlow.PREVIEW) restoreCandidate?.let { backup ->
        RestoreSummaryDialog(summary = backup, onCancel = {
            clearRestorePreview()
            pickerError = "已取消恢复，原数据未更改"
        }, onConfirm = {
                restoreFlow = RestoreFlow.PREPARING
                scope.launch {
                    var sessionStarted = false
                    try {
                        model.beginRestoreSession()
                        sessionStarted = true
                        val hasExistingData = withContext(Dispatchers.IO) { model.hasExistingData() }
                        if (hasExistingData) {
                            withContext(NonCancellable) { model.endRestoreSession() }
                            sessionStarted = false
                            restoreFlow = RestoreFlow.BACKUP_PASSWORD
                        } else {
                            restoreFlow = RestoreFlow.APPLYING
                            val sourcePassword = restoreSourcePassword ?: throw BackupException("恢复密码已失效，请重新选择备份")
                            applyRestoreSource(backup, sourcePassword)
                            clearRestorePreview()
                            pickerError = "账单已恢复"
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        pickerError = error.message ?: "恢复失败，现有账单未更改"
                        restoreFlow = RestoreFlow.PREVIEW
                    } finally {
                        if (sessionStarted) withContext(NonCancellable) { model.endRestoreSession() }
                    }
                }
            })
    }

    if (restoreFlow in setOf(RestoreFlow.PREPARING, RestoreFlow.PREPARING_BACKUP, RestoreFlow.WRITING_BACKUP, RestoreFlow.APPLYING)) {
        val message = when (restoreFlow) {
            RestoreFlow.PREPARING -> "正在检查账本，请稍候…"
            RestoreFlow.PREPARING_BACKUP -> "正在准备当前账单备份…"
            RestoreFlow.WRITING_BACKUP -> "正在保存当前账单备份…"
            else -> "正在恢复账单…"
        }
        BackupProcessingDialog(message)
    }

    Scaffold(bottomBar = {
        if (page == Page.LEDGER || page == Page.SETTINGS) NavigationBar(containerColor = CaijiPalette.Surface) {
            val navigationColors = NavigationBarItemDefaults.colors(
                selectedIconColor = CaijiPalette.Primary,
                selectedTextColor = CaijiPalette.Ink,
                unselectedIconColor = CaijiPalette.SecondaryText,
                unselectedTextColor = CaijiPalette.SecondaryText,
                indicatorColor = Color.Transparent
            )
            NavigationBarItem(selected = page == Page.LEDGER, onClick = { page = Page.LEDGER },
                icon = { CaijiIcon(CaijiIconName.LEDGER, contentDescription = null) }, label = { Text("明细") },
                colors = navigationColors, modifier = Modifier.testTag("nav-ledger"))
            NavigationBarItem(selected = false, onClick = {
                editEntry = null; editEntryId = null; initialEditorType = EntryType.EXPENSE; page = Page.EDITOR
            }, icon = {
                Surface(shape = RoundedCornerShape(12.dp), color = CaijiPalette.Primary, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        CaijiIcon(CaijiIconName.ADD, contentDescription = null, modifier = Modifier.size(23.dp))
                    }
                }
            }, label = { Text("记账") },
                colors = navigationColors, modifier = Modifier.testTag("nav-entry"))
            NavigationBarItem(selected = page == Page.SETTINGS, onClick = { page = Page.SETTINGS },
                icon = { CaijiIcon(CaijiIconName.SETTINGS, contentDescription = null) }, label = { Text("设置") },
                colors = navigationColors, modifier = Modifier.testTag("nav-settings"))
        }
    }, containerColor = CaijiPalette.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val message = pickerError ?: modelError
            if (message != null) BackupFeedbackBanner(
                message = message,
                tone = backupFeedbackTone(message, isModelError = modelError != null && modelError == message),
                onDismiss = { pickerError = null; model.clearError() }
            )
            when (page) {
                Page.LEDGER -> LedgerHome(month, totals.expenseFen, totals.incomeFen, entries, categories,
                    onPrevious = { model.changeMonth(month.minusMonths(1)) },
                    onNext = { model.changeMonth(month.plusMonths(1)) },
                    onEdit = { editEntry = it; editEntryId = it.id; page = Page.EDITOR },
                    onCreateEntry = {
                        editEntry = null
                        editEntryId = null
                        initialEditorType = EntryType.EXPENSE
                        page = Page.EDITOR
                    })
                Page.EDITOR -> if (editEntryId != null && editEntry == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在加载账单…") }
                } else EntryEditorScreen(categories, editEntry, initialEditorType,
                    onCancel = { page = Page.LEDGER; editEntry = null; editEntryId = null },
                    onSave = { type, fen, categoryId, date, note, onComplete ->
                        val entryBeingEdited = editEntry
                        val saved = { page = Page.LEDGER; editEntryId = null; onComplete(true) }
                        val failed = { onComplete(false) }
                        if (entryBeingEdited == null) model.addEntry(type, fen, categoryId, date, note, saved, failed)
                        else model.updateEntry(entryBeingEdited.copy(type = type, amountFen = fen, categoryId = categoryId,
                            occurredOn = date, note = note), saved, failed)
                    }, onDelete = { entry -> model.deleteEntry(entry.id) { page = Page.LEDGER; editEntryId = null } },
                    onSaveCategory = { type, id, name, icon, done -> model.saveCategory(type, id, name, icon,
                        onSaved = { done(true) }, onFailed = { done(false) }) },
                    onSetCategoryActive = model::setCategoryActive,
                    onReorderCategory = model::reorderCategory,
                    categoryErrorMessage = modelError)
                Page.SETTINGS -> SettingsScreen({ page = Page.CATEGORIES }, { password ->
                    exportPassword = password
                    exportPicker.launch("财记-完整备份-${LocalDate.now()}.cjbackup")
                }, { importPicker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) })
                Page.CATEGORIES -> CategoryManagerScreen(categories, { page = Page.SETTINGS },
                    { type, id, name, icon, done -> model.saveCategory(type, id, name, icon,
                        onSaved = { done(true) }, onFailed = { done(false) }) },
                    model::setCategoryActive, model::reorderCategory, initialType = EntryType.EXPENSE,
                    errorMessage = modelError)
            }
        }
    }
    BackHandler(enabled = page == Page.CATEGORIES) { page = Page.SETTINGS }
}
