package app.caiji.android

import android.content.Intent
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import app.caiji.android.data.SalaryEntry
import app.caiji.android.data.FeedbackItemRow
import app.caiji.android.data.FeedbackItemWithScreenshots
import app.caiji.android.domain.EntryType
import app.caiji.android.domain.ClipboardTransaction
import app.caiji.android.domain.ClipboardTransactionParser
import app.caiji.android.ui.BackupFeedbackBanner
import app.caiji.android.ui.BackupPasswordDialog
import app.caiji.android.ui.BackupProcessingDialog
import app.caiji.android.ui.CaijiIcon
import app.caiji.android.ui.CaijiIconName
import app.caiji.android.ui.backupFeedbackTone
import app.caiji.android.ui.CategoryManagerScreen
import app.caiji.android.ui.BillListMode
import app.caiji.android.ui.BillPeriodReportScreen
import app.caiji.android.ui.BillReportsScreen
import app.caiji.android.ui.EntryEditorScreen
import app.caiji.android.ui.LedgerHome
import app.caiji.android.ui.LedgerViewModel
import app.caiji.android.ui.RestoreSummaryDialog
import app.caiji.android.ui.SettingsScreen
import app.caiji.android.ui.UpdateAvailableDialog
import app.caiji.android.ui.FeedbackEditorScreen
import app.caiji.android.ui.FeedbackListScreen
import app.caiji.android.ui.ThemePickerScreen
import app.caiji.android.ui.shareFeedback
import app.caiji.android.ui.SalaryDraft
import app.caiji.android.ui.SalaryManagementRoute
import app.caiji.android.ui.theme.CaijiThemeId
import app.caiji.android.ui.theme.CaijiPalette
import app.caiji.android.ui.theme.CaijiTheme
import app.caiji.android.update.UpdateState
import java.time.LocalDate
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    internal val clipboardReads = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    private var shouldReadClipboard = false

    override fun onStart() {
        super.onStart()
        shouldReadClipboard = true
        window.decorView.post {
            if (hasWindowFocus() && shouldReadClipboard) {
                shouldReadClipboard = false
                readClipboardForForeground()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && shouldReadClipboard) {
            shouldReadClipboard = false
            readClipboardForForeground()
        }
    }

    internal fun readClipboardForForeground() {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = runCatching { manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() }
            .getOrNull()
        if (!clipText.isNullOrBlank()) clipboardReads.tryEmit(clipText)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeKey = getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("theme", CaijiThemeId.AMBER.key)
        CaijiPalette.select(CaijiThemeId.entries.firstOrNull { it.key == themeKey } ?: CaijiThemeId.AMBER)
        window.statusBarColor = CaijiPalette.Background.toArgb()
        window.navigationBarColor = CaijiPalette.Background.toArgb()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !CaijiPalette.isDark
            isAppearanceLightNavigationBars = !CaijiPalette.isDark
        }
        setContent { CaijiTheme { CaijiRoot() } }
    }
}

private enum class Page { LEDGER, BILLS, PERIOD_REPORT, EDITOR, CHART, SALARY, SETTINGS, CATEGORIES, THEMES, FEEDBACK_LIST, FEEDBACK_EDITOR }
private enum class RestoreFlow { IDLE, PREVIEW, PREPARING, BACKUP_PASSWORD, SELECTING_BACKUP, PREPARING_BACKUP, WRITING_BACKUP, APPLYING }

@Composable
private fun CaijiRoot(model: LedgerViewModel = viewModel()) {
    val context = LocalContext.current
    val updateManager = (context.applicationContext as CaijiApplication).updateManager
    val updateState by updateManager.state.collectAsState()
    val feedbackRepository = remember { (context.applicationContext as CaijiApplication).feedbackRepository }
    val scope = rememberCoroutineScope()
    val month by model.month.collectAsState()
    val entries by model.entries.collectAsState()
    val categories by model.categories.collectAsState()
    val totals by model.totals.collectAsState()
    val modelError by model.error.collectAsState()
    val monthlyReports by model.monthlyReports.collectAsState()
    val yearlyReports by model.yearlyReports.collectAsState()
    val reportLoading by model.reportLoading.collectAsState()
    val reportError by model.reportError.collectAsState()
    val periodReport by model.periodReport.collectAsState()
    val periodRankingSnapshot by model.periodRankingSnapshot.collectAsState()
    val periodRankingLoading by model.periodRankingLoading.collectAsState()
    val periodRankingError by model.periodRankingError.collectAsState()
    var page by rememberSaveable { mutableStateOf(Page.LEDGER) }
    var editorReturnPage by rememberSaveable { mutableStateOf(Page.LEDGER.name) }
    var billListMode by rememberSaveable { mutableStateOf(BillListMode.MONTH.name) }
    var reportYear by rememberSaveable { mutableIntStateOf(month.year) }
    var reportMonthValue by rememberSaveable { mutableIntStateOf(month.monthValue) }
    var reportTypeName by rememberSaveable { mutableStateOf(EntryType.EXPENSE.name) }
    var reportCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var editEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var editEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var feedbackRecords by remember { mutableStateOf<List<FeedbackItemWithScreenshots>>(emptyList()) }
    var feedbackEditId by remember { mutableStateOf<String?>(null) }
    var feedbackDraftId by remember { mutableStateOf<String?>(null) }
    var feedbackDeleteCandidate by remember { mutableStateOf<FeedbackItemWithScreenshots?>(null) }
    var localBlockingDialogOpen by remember { mutableStateOf(false) }
    var selectedTheme by remember {
        val key = (context as? MainActivity)?.getSharedPreferences("appearance", Context.MODE_PRIVATE)
            ?.getString("theme", CaijiThemeId.AMBER.key)
        mutableStateOf(CaijiThemeId.entries.firstOrNull { it.key == key } ?: CaijiThemeId.AMBER)
    }
    var clipboardPrefill by remember { mutableStateOf<ClipboardTransaction?>(null) }
    var clipboardPrefillId by remember { mutableStateOf<String?>(null) }
    var pendingClipboard by remember { mutableStateOf<Pair<String, ClipboardTransaction>?>(null) }

    LaunchedEffect(editEntryId) {
        editEntry = editEntryId?.let { model.entryById(it) }
    }
    LaunchedEffect(feedbackRepository) {
        feedbackRepository.observeAll().collect { feedbackRecords = it }
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
    var restoreSessionActive by remember { mutableStateOf(false) }
    var salaryPreview by remember { mutableStateOf<app.caiji.android.data.SalaryImportPreview?>(null) }
    var salaryEditorDraft by remember { mutableStateOf<SalaryDraft?>(null) }
    var salaryEditorEntry by remember { mutableStateOf<SalaryEntry?>(null) }
    var salaryManagementRoute by rememberSaveable { mutableStateOf(SalaryManagementRoute.HOME) }
    var showSalaryBottomNavigation by remember { mutableStateOf(true) }
    var salaryExportPeriod by rememberSaveable { mutableStateOf<app.caiji.android.data.SalaryPeriod?>(null) }
    var salaryImportGeneration by rememberSaveable { mutableIntStateOf(0) }
    var salaryImportRequest by rememberSaveable { mutableIntStateOf(0) }
    fun navigateMain(target: Page) {
        if (target == Page.SALARY) { showSalaryBottomNavigation = true; salaryManagementRoute = SalaryManagementRoute.HOME }
        if (page == Page.SALARY && target != Page.SALARY) {
            salaryImportGeneration++
            salaryPreview = null
        }
        page = target
    }

    LaunchedEffect(context) {
        val activity = context as? MainActivity ?: return@LaunchedEffect
        activity.clipboardReads.collectLatest { text ->
            val transaction = ClipboardTransactionParser.parse(text, LocalDate.now()) ?: return@collectLatest
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val preferences = activity.getSharedPreferences("clipboard_transaction_imports", Context.MODE_PRIVATE)
            if (fingerprint in preferences.getStringSet("fingerprints", emptySet()).orEmpty()) return@collectLatest
            pendingClipboard = fingerprint to transaction
        }
    }
    LaunchedEffect(page, restoreFlow, restoreSessionActive, pendingClipboard, editEntryId) {
        val pending = pendingClipboard ?: return@LaunchedEffect
        if (page == Page.EDITOR || page == Page.SALARY || page == Page.FEEDBACK_EDITOR || restoreFlow != RestoreFlow.IDLE || restoreSessionActive || editEntryId != null) return@LaunchedEffect
        val activity = context as? MainActivity ?: return@LaunchedEffect
        val preferences = activity.getSharedPreferences("clipboard_transaction_imports", Context.MODE_PRIVATE)
        val seen = preferences.getStringSet("fingerprints", emptySet()).orEmpty().toMutableSet()
        if (pending.first !in seen) {
            seen.add(pending.first)
            preferences.edit().putStringSet("fingerprints", seen).apply()
            clipboardPrefill = pending.second
            clipboardPrefillId = UUID.randomUUID().toString()
            initialEditorType = pending.second.type
            editEntry = null
            editEntryId = null
            editorReturnPage = Page.LEDGER.name
            page = Page.EDITOR
        }
        pendingClipboard = null
    }

    LaunchedEffect(page, reportYear, reportMonthValue, reportCategoryId) {
        when (page) {
            Page.PERIOD_REPORT -> if (reportCategoryId == null) model.loadPeriodRankingSnapshot(reportYear, reportMonthValue)
                else model.loadPeriodReport(reportYear, reportMonthValue, EntryType.valueOf(reportTypeName), reportCategoryId)
            else -> Unit
        }
    }

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

    val salaryImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val request = salaryImportRequest
        if (request == salaryImportGeneration && page == Page.SALARY) {
            if (uri == null) {
                salaryPreview = null
                pickerError = "已取消选择工资 Excel 文件"
            } else scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        app.caiji.android.data.SalaryXlsxCodec { context.assets.open("salary-template.xlsx") }
                            .importPreview(it, (context.applicationContext as CaijiApplication).salaryRepository.incomeTypes(), LocalDate.now())
                    } ?: throw java.io.IOException("Selected document is unavailable")
                } }
                if (request == salaryImportGeneration && page == Page.SALARY) {
                    result.onSuccess { salaryPreview = it; pickerError = null }
                        .onFailure {
                            salaryPreview = app.caiji.android.data.SalaryImportPreview(
                                emptyList(),
                                listOf(app.caiji.android.data.SalaryImportError(app.caiji.android.data.SalaryImportErrorKind.FILE,
                                    "无法读取所选文件", "请确认文件可访问，或用 Excel/WPS 另存为 .xlsx 后重新选择。", "IMP-URI-001")),
                                0, emptyList()
                            )
                            pickerError = null
                        }
                }
            }
        }
    }
    val salaryExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri: Uri? ->
        val period = salaryExportPeriod; salaryExportPeriod = null
        if (uri == null || period == null) pickerError = "已取消保存工资 Excel"
        else scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val repo = (context.applicationContext as CaijiApplication).salaryRepository
                val types = repo.incomeTypes()
                val entries = app.caiji.android.data.SalaryReports.filter(repo.entries(), period)
                context.contentResolver.openOutputStream(uri, "w")?.use {
                    app.caiji.android.data.SalaryXlsxCodec { context.assets.open("salary-template.xlsx") }.export(it, entries, types)
                } ?: error("无法写入所选文件")
            } }.onSuccess {
                pickerError = "工资 Excel 已保存"
                if (page == Page.SALARY && salaryManagementRoute == SalaryManagementRoute.EXPORT) {
                    salaryManagementRoute = SalaryManagementRoute.HOME
                    page = Page.SETTINGS
                }
            }
                .onFailure { pickerError = "导出失败：" + (it.message ?: "请重试") }
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
                    restoreSessionActive = true
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
                    try {
                        if (sessionStarted) withContext(NonCancellable) { model.endRestoreSession() }
                    } finally {
                        restoreSessionActive = false
                    }
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
                        restoreSessionActive = true
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
                        try {
                            if (sessionStarted) withContext(NonCancellable) { model.endRestoreSession() }
                        } finally {
                            restoreSessionActive = false
                        }
                    restoreSessionActive = false
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

    feedbackDeleteCandidate?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { feedbackDeleteCandidate = null },
            title = { Text("删除这条问题记录？") },
            text = { Text("删除后无法恢复，关联截图也会从本机移除。") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                val candidate = feedbackDeleteCandidate ?: return@TextButton
                feedbackDeleteCandidate = null
                scope.launch(Dispatchers.IO) { feedbackRepository.delete(candidate.item.id, candidate.screenshots) }
            }) { Text("删除", color = CaijiPalette.Danger) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { feedbackDeleteCandidate = null }) { Text("取消") } }
        )
    }

    val canShowUpdateDialog = restoreFlow == RestoreFlow.IDLE &&
        restoreCandidate == null && exportPassword == null && feedbackDeleteCandidate == null && !localBlockingDialogOpen &&
        page !in setOf(Page.EDITOR, Page.CATEGORIES, Page.THEMES, Page.FEEDBACK_LIST, Page.FEEDBACK_EDITOR, Page.SALARY)
    if (canShowUpdateDialog) (updateState as? UpdateState.Available)?.let { available ->
        val release = available.release
        UpdateAvailableDialog(
            currentVersion = BuildConfig.VERSION_NAME,
            release = release,
            onDismiss = updateManager::dismissAvailable,
            onDownload = {
                val target = release.apkUrl ?: release.releaseUrl
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                }.onSuccess { updateManager.dismissAvailable() }
                    .onFailure { updateManager.downloadFailed() }
            }
        )
    }

    Scaffold(bottomBar = {
        if (page == Page.LEDGER || (page == Page.SALARY && showSalaryBottomNavigation) || page == Page.CHART || page == Page.SETTINGS) NavigationBar(containerColor = CaijiPalette.Surface) {
            val navigationColors = NavigationBarItemDefaults.colors(
                selectedIconColor = CaijiPalette.Primary,
                selectedTextColor = CaijiPalette.Ink,
                unselectedIconColor = CaijiPalette.SecondaryText,
                unselectedTextColor = CaijiPalette.SecondaryText,
                indicatorColor = Color.Transparent
            )
            NavigationBarItem(selected = page == Page.LEDGER, onClick = { navigateMain(Page.LEDGER) },
                icon = { CaijiIcon(CaijiIconName.LEDGER, contentDescription = null) }, label = { Text("明细") },
                colors = navigationColors, modifier = Modifier.testTag("nav-ledger"))
            NavigationBarItem(selected = page == Page.CHART, onClick = { navigateMain(Page.CHART) },
                icon = { Text("▥") }, label = { Text("图表") }, colors = navigationColors, modifier = Modifier.testTag("nav-chart"))
            NavigationBarItem(selected = false, onClick = {
                editEntry = null; editEntryId = null; initialEditorType = EntryType.EXPENSE
                clipboardPrefill = null; clipboardPrefillId = null
                editorReturnPage = Page.LEDGER.name; navigateMain(Page.EDITOR)
            }, icon = {
                Surface(shape = RoundedCornerShape(12.dp), color = CaijiPalette.Primary, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        CaijiIcon(CaijiIconName.ADD, contentDescription = null, modifier = Modifier.size(23.dp))
                    }
                }
            }, label = { Text("记账") },
                colors = navigationColors, modifier = Modifier.testTag("nav-entry"))
            NavigationBarItem(selected = page == Page.SALARY, onClick = { navigateMain(Page.SALARY) },
                icon = { Text("¥") }, label = { Text("工资") }, colors = navigationColors, modifier = Modifier.testTag("nav-salary"))
            NavigationBarItem(selected = page == Page.SETTINGS, onClick = { navigateMain(Page.SETTINGS) },
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
                    onEdit = { editEntry = it; editEntryId = it.id; editorReturnPage = Page.LEDGER.name; page = Page.EDITOR },
                    onCreateEntry = {
                        editEntry = null
                        editEntryId = null
                        clipboardPrefill = null
                        clipboardPrefillId = null
                        initialEditorType = EntryType.EXPENSE
                        editorReturnPage = Page.LEDGER.name; page = Page.EDITOR
                    }, onOpenBills = {
                        billListMode = BillListMode.MONTH.name
                        reportYear = month.year
                        reportMonthValue = month.monthValue
                        reportCategoryId = null
                        page = Page.BILLS
                    }, onDelete = { entry, complete ->
                        model.deleteEntry(entry.id, onDeleted = { complete(true) }, onFailed = { complete(false) })
                    }, onModalVisibilityChange = { localBlockingDialogOpen = it })
                Page.BILLS -> BillReportsScreen(
                    mode = BillListMode.valueOf(billListMode), year = reportYear,
                    monthReports = monthlyReports, yearReports = yearlyReports, loading = reportLoading, error = reportError,
                    onSelectMode = { billListMode = it.name }, onBack = { page = Page.LEDGER },
                    onPreviousYear = { reportYear -= 1 }, onNextYear = { reportYear += 1 },
                    onRetryList = { model.loadBillLists(reportYear) },
                    onLoadLists = { model.loadBillLists(it) },
                    onOpenMonth = { selected -> reportYear = selected.year; reportMonthValue = selected.monthValue; reportTypeName = EntryType.EXPENSE.name; reportCategoryId = null; page = Page.PERIOD_REPORT },
                    onOpenYear = { selected -> reportYear = selected; reportMonthValue = 0; reportTypeName = EntryType.EXPENSE.name; reportCategoryId = null; page = Page.PERIOD_REPORT }
                )
                Page.PERIOD_REPORT -> BillPeriodReportScreen(
                    year = reportYear, monthValue = reportMonthValue, report = periodReport,
                    rankingSnapshot = periodRankingSnapshot,
                    loading = if (reportCategoryId == null) periodRankingLoading else reportLoading,
                    error = if (reportCategoryId == null) periodRankingError else reportError,
                    selectedType = EntryType.valueOf(reportTypeName),
                    selectedCategoryId = reportCategoryId, onBack = { page = Page.BILLS }, onRetry = {
                        if (reportCategoryId == null) model.loadPeriodRankingSnapshot(reportYear, reportMonthValue, force = true)
                        else model.loadPeriodReport(reportYear, reportMonthValue, EntryType.valueOf(reportTypeName), reportCategoryId)
                    }, onSelectType = { reportTypeName = it.name; reportCategoryId = null },
                    onSelectCategory = { reportCategoryId = it }, onEditEntry = { entry ->
                        editEntry = entry; editEntryId = entry.id; editorReturnPage = Page.PERIOD_REPORT.name; page = Page.EDITOR
                    }
                )
                Page.EDITOR -> if (editEntryId != null && editEntry == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在加载账单…") }
                } else EntryEditorScreen(categories, editEntry, initialEditorType,
                    clipboardPrefill = clipboardPrefill,
                    clipboardPrefillId = clipboardPrefillId,
                    onCancel = { page = runCatching { Page.valueOf(editorReturnPage) }.getOrDefault(Page.LEDGER); editEntry = null; editEntryId = null; clipboardPrefill = null; clipboardPrefillId = null },
                    onSave = { type, fen, categoryId, date, note, onComplete ->
                        val entryBeingEdited = editEntry
                        val saved = {
                            page = runCatching { Page.valueOf(editorReturnPage) }.getOrDefault(Page.LEDGER)
                            editEntryId = null
                            clipboardPrefill = null
                            clipboardPrefillId = null
                            onComplete(true)
                        }
                        val failed = { onComplete(false) }
                        if (entryBeingEdited == null) model.addEntry(type, fen, categoryId, date, note, saved, failed)
                        else model.updateEntry(entryBeingEdited.copy(type = type, amountFen = fen, categoryId = categoryId,
                            occurredOn = date, note = note), saved, failed)
                    }, onDelete = { entry -> model.deleteEntry(entry.id, onDeleted = {
                        page = runCatching { Page.valueOf(editorReturnPage) }.getOrDefault(Page.LEDGER)
                        editEntryId = null
                    }) },
                    onSaveCategory = { type, id, name, icon, done -> model.saveCategory(type, id, name, icon,
                        onSaved = { done(true) }, onFailed = { done(false) }) },
                    onSetCategoryActive = model::setCategoryActive,
                    onReorderCategory = model::reorderCategory,
                    categoryErrorMessage = modelError)
                Page.CHART -> Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("图表", style = MaterialTheme.typography.headlineSmall)
                    Text("图表功能即将上线", color = CaijiPalette.SecondaryText)
                }
                Page.SALARY -> app.caiji.android.ui.SalaryRootScreen(
                    repo = (context.applicationContext as CaijiApplication).salaryRepository,
                    preview = salaryPreview,
                    initialDraft = salaryEditorDraft,
                    initialEntry = salaryEditorEntry,
                    onDraftSnapshotChange = { draft, entry -> salaryEditorDraft = draft; salaryEditorEntry = entry },
                    onAsyncError = { pickerError = it },
                    onBottomNavigationVisibility = { showSalaryBottomNavigation = it },
                    onImport = {
                        salaryImportGeneration++
                        salaryImportRequest = salaryImportGeneration
                        salaryPreview = null
                        pickerError = null
                        salaryImportPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    },
                    onClear = { salaryImportGeneration++; salaryPreview = null },
                    onExport = { period ->
                        salaryExportPeriod = period
                        salaryExportPicker.launch("工资明细.xlsx")
                    },
                    initialManagementRoute = salaryManagementRoute,
                    onManagementSuccess = { pickerError = it },
                    onManagementBack = {
                        salaryImportGeneration++
                        salaryPreview = null
                        salaryManagementRoute = SalaryManagementRoute.HOME
                        page = Page.SETTINGS
                    }
                )
                Page.SETTINGS -> SettingsScreen({ page = Page.CATEGORIES }, { password ->
                    exportPassword = password
                    exportPicker.launch("财记-完整备份-${LocalDate.now()}.cjbackup")
                }, { importPicker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) },
                    onTheme = { page = Page.THEMES }, onFeedback = { page = Page.FEEDBACK_LIST },
                    updateState = updateState,
                    onCheckUpdates = { updateManager.check(manual = true) },
                    onLocalDialogVisibilityChange = { localBlockingDialogOpen = it },
                    onSalaryImport = { showSalaryBottomNavigation = false; salaryManagementRoute = SalaryManagementRoute.IMPORT; salaryPreview = null; page = Page.SALARY },
                    onSalaryExport = { showSalaryBottomNavigation = false; salaryManagementRoute = SalaryManagementRoute.EXPORT; pickerError = null; page = Page.SALARY },
                    onSalaryTypes = { showSalaryBottomNavigation = false; salaryManagementRoute = SalaryManagementRoute.TYPES; page = Page.SALARY })
                Page.CATEGORIES -> CategoryManagerScreen(categories, { page = Page.SETTINGS },
                    { type, id, name, icon, done -> model.saveCategory(type, id, name, icon,
                        onSaved = { done(true) }, onFailed = { done(false) }) },
                    model::setCategoryActive, model::reorderCategory, initialType = EntryType.EXPENSE,
                    errorMessage = modelError)
                Page.THEMES -> ThemePickerScreen(selectedTheme, onSelect = { theme ->
                    selectedTheme = theme
                    CaijiPalette.select(theme)
                    (context as? MainActivity)?.getSharedPreferences("appearance", Context.MODE_PRIVATE)
                        ?.edit()?.putString("theme", theme.key)?.apply()
                }, onBack = { page = Page.SETTINGS })
                Page.FEEDBACK_LIST -> FeedbackListScreen(feedbackRecords, onNew = {
                    feedbackEditId = null
                    feedbackDraftId = feedbackRepository.newDraftId()
                    page = Page.FEEDBACK_EDITOR
                }, onEdit = { record ->
                    feedbackEditId = record.item.id
                    feedbackDraftId = feedbackRepository.newDraftId()
                    page = Page.FEEDBACK_EDITOR
                }, onShare = { record -> runCatching { shareFeedback(context, record, feedbackRepository) }
                    .onFailure { pickerError = "分享失败：${it.message ?: "请重试"}" } },
                    onDelete = { feedbackDeleteCandidate = it }, repository = feedbackRepository,
                    onBack = { page = Page.SETTINGS })
                Page.FEEDBACK_EDITOR -> {
                    val currentRecord = feedbackEditId?.let { id -> feedbackRecords.firstOrNull { it.item.id == id } }
                    if (feedbackEditId != null && currentRecord == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在加载问题…") }
                    } else FeedbackEditorScreen(
                        repository = feedbackRepository, existing = currentRecord,
                        draftId = feedbackDraftId ?: "new-feedback-draft",
                        onCancel = {
                            feedbackDraftId?.let { draft -> scope.launch(Dispatchers.IO) { feedbackRepository.discardDraft(draft) } }
                            feedbackDraftId = null
                            feedbackEditId = null
                            page = Page.FEEDBACK_LIST
                        },
                        onSave = { title, description, imageRefs, done ->
                            val existingRow = currentRecord?.item
                            val now = System.currentTimeMillis()
                            val row = FeedbackItemRow(existingRow?.id ?: UUID.randomUUID().toString(), title, description,
                                existingRow?.createdAtMillis ?: now, now)
                            scope.launch {
                                val draftId = feedbackDraftId ?: "new-feedback-draft"
                                runCatching { withContext(Dispatchers.IO) { feedbackRepository.save(row, imageRefs, draftId) } }
                                    .onSuccess { feedbackDraftId = null; feedbackEditId = null; page = Page.FEEDBACK_LIST; done(true, null) }
                                    .onFailure { done(false, it.message) }
                            }
                        }
                    )
                }
            }
        }
    }
    BackHandler(enabled = page == Page.CATEGORIES) { page = Page.SETTINGS }
    BackHandler(enabled = page == Page.BILLS) { page = Page.LEDGER }
    BackHandler(enabled = page == Page.PERIOD_REPORT) {
        if (reportCategoryId != null) reportCategoryId = null else page = Page.BILLS
    }
    BackHandler(enabled = page == Page.THEMES) { page = Page.SETTINGS }
    BackHandler(enabled = page == Page.FEEDBACK_LIST) { page = Page.SETTINGS }
}
