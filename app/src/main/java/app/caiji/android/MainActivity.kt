package app.caiji.android

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.caiji.android.backup.BackupCodec
import app.caiji.android.backup.BackupException
import app.caiji.android.backup.BackupSummary
import app.caiji.android.BuildConfig
import app.caiji.android.data.LedgerEntry
import app.caiji.android.domain.Category
import app.caiji.android.domain.CategoryCatalog
import app.caiji.android.domain.EntryType
import app.caiji.android.domain.Money
import app.caiji.android.ui.LedgerViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CaijiYellow = Color(0xFFFFD943)
private val CaijiInk = Color(0xFF282726)
private val CaijiGrey = Color(0xFFF4F4F3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CaijiTheme { CaijiRoot() } }
    }
}

@Composable
private fun CaijiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = CaijiYellow, onPrimary = CaijiInk,
        secondary = CaijiInk, onSecondary = Color.White, surface = Color.White,
        background = Color(0xFFFFFEFC), onBackground = CaijiInk, onSurface = CaijiInk,
        error = Color(0xFFD73737)), content = content)
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

    if (restorePasswordVisible) PasswordDialog("输入备份密码", "解密预览", false,
        onDismiss = { restorePasswordVisible = false; clearRestorePreview() }, onConfirm = { password, _ ->
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

    if (restoreFlow == RestoreFlow.BACKUP_PASSWORD) PasswordDialog("保存当前账单备份", "选择保存位置", true,
        onDismiss = { restoreFlow = RestoreFlow.PREVIEW }, onConfirm = { password, _ ->
            restoreBackupPassword = password
            restoreFlow = RestoreFlow.SELECTING_BACKUP
            preRestorePicker.launch("财记-恢复前备份-${LocalDate.now()}.cjbackup")
        })

    if (restoreFlow == RestoreFlow.PREVIEW) restoreCandidate?.let { backup ->
        AlertDialog(onDismissRequest = ::clearRestorePreview, title = { Text("确认恢复") },
            text = { Text("备份包含 ${backup.entryCount} 笔账单、${backup.categoryCount} 个分类\n收入 ${yuan(backup.incomeFen)} 元 · 支出 ${yuan(backup.expenseFen)} 元。确认后应用会先判断是否需要另存当前数据。") },
            confirmButton = { TextButton(onClick = {
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
            }) { Text("确认恢复") } },
            dismissButton = { TextButton(onClick = ::clearRestorePreview) { Text("取消") } })
    }

    if (restoreFlow in setOf(RestoreFlow.PREPARING, RestoreFlow.PREPARING_BACKUP, RestoreFlow.WRITING_BACKUP, RestoreFlow.APPLYING)) {
        val message = when (restoreFlow) {
            RestoreFlow.PREPARING -> "正在检查账本，请稍候…"
            RestoreFlow.PREPARING_BACKUP -> "正在准备当前账单备份…"
            RestoreFlow.WRITING_BACKUP -> "正在保存当前账单备份…"
            else -> "正在恢复账单…"
        }
        AlertDialog(onDismissRequest = {}, title = { Text("恢复账单") }, text = { Text(message) }, confirmButton = {})
    }

    Scaffold(bottomBar = {
        if (page == Page.LEDGER || page == Page.SETTINGS) NavigationBar(containerColor = Color.White) {
            NavigationBarItem(selected = page == Page.LEDGER, onClick = { page = Page.LEDGER },
                icon = { Text("▤") }, label = { Text("明细") }, modifier = Modifier.testTag("nav-ledger"))
            NavigationBarItem(selected = false, onClick = {
                editEntry = null; editEntryId = null; initialEditorType = EntryType.EXPENSE; page = Page.EDITOR
            }, icon = { Text("＋", style = MaterialTheme.typography.headlineMedium) }, label = { Text("记账") },
                modifier = Modifier.testTag("nav-entry"))
            NavigationBarItem(selected = page == Page.SETTINGS, onClick = { page = Page.SETTINGS },
                icon = { Text("☷") }, label = { Text("设置") }, modifier = Modifier.testTag("nav-settings"))
        }
    }, containerColor = Color(0xFFFFFEFC)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val message = pickerError ?: modelError
            if (message != null) Surface(color = Color(0xFFFFF0CB), modifier = Modifier.fillMaxWidth()
                .clickable { pickerError = null; model.clearError() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(message, modifier = Modifier.padding(4.dp), color = CaijiInk)
            }
            when (page) {
                Page.LEDGER -> LedgerHome(month, totals.expenseFen, totals.incomeFen, entries, categories,
                    onPrevious = { model.changeMonth(month.minusMonths(1)) },
                    onNext = { model.changeMonth(month.plusMonths(1)) },
                    onEdit = { editEntry = it; editEntryId = it.id; page = Page.EDITOR })
                Page.EDITOR -> if (editEntryId != null && editEntry == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在加载账单…") }
                } else EntryEditor(categories, editEntry, initialEditorType,
                    onCancel = { page = Page.LEDGER; editEntry = null; editEntryId = null },
                    onSave = { type, fen, categoryId, date, note, onComplete ->
                        val entryBeingEdited = editEntry
                        val saved = { page = Page.LEDGER; editEntryId = null; onComplete(true) }
                        val failed = { onComplete(false) }
                        if (entryBeingEdited == null) model.addEntry(type, fen, categoryId, date, note, saved, failed)
                        else model.updateEntry(entryBeingEdited.copy(type = type, amountFen = fen, categoryId = categoryId,
                            occurredOn = date, note = note), saved, failed)
                    }, onDelete = { entry -> model.deleteEntry(entry.id) { page = Page.LEDGER; editEntryId = null } })
                Page.SETTINGS -> SettingsPage({ page = Page.CATEGORIES }, { password ->
                    exportPassword = password
                    exportPicker.launch("财记-完整备份-${LocalDate.now()}.cjbackup")
                }, { importPicker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) })
                Page.CATEGORIES -> CategoryManager(categories, { page = Page.SETTINGS },
                    { type, id, name, icon, done -> model.saveCategory(type, id, name, icon,
                        onSaved = { done(true) }, onFailed = { done(false) }) },
                    model::setCategoryActive, model::reorderCategory)
            }
        }
    }
    BackHandler(enabled = page == Page.CATEGORIES) { page = Page.SETTINGS }
}

@Composable
private fun LedgerHome(
    month: YearMonth, expenseFen: BigInteger, incomeFen: BigInteger, entries: List<LedgerEntry>, categories: List<Category>,
    onPrevious: () -> Unit, onNext: () -> Unit, onEdit: (LedgerEntry) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(CaijiYellow).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("财记", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPrevious) { Text("‹", style = MaterialTheme.typography.headlineLarge, color = CaijiInk) }
                    Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("home-month"))
                    TextButton(onClick = onNext) { Text("›", style = MaterialTheme.typography.headlineLarge, color = CaijiInk) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("本月收入", color = CaijiInk.copy(alpha = .65f)); Text(yuan(incomeFen), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("monthly-income")) }
                Column(horizontalAlignment = Alignment.End) { Text("本月支出", color = CaijiInk.copy(alpha = .65f)); Text(yuan(expenseFen), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("monthly-expense")) }
            }
        }
        if (entries.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("这个月还没有账单\n点下方「记账」记录第一笔", textAlign = TextAlign.Center, color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
        } else LazyColumn(Modifier.fillMaxSize()) {
            entries.groupBy { it.occurredOn }.forEach { (day, dayEntries) ->
                item(key = "day-$day") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(day.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)), color = Color.Gray)
                        val out = dayEntries.filter { it.type == EntryType.EXPENSE }
                            .fold(BigInteger.ZERO) { sum, entry -> sum + BigInteger.valueOf(entry.amountFen) }
                        val into = dayEntries.filter { it.type == EntryType.INCOME }
                            .fold(BigInteger.ZERO) { sum, entry -> sum + BigInteger.valueOf(entry.amountFen) }
                        val hasExpense = out.signum() > 0
                        val hasIncome = into.signum() > 0
                        Text("${if (hasExpense) "支出 ${yuan(out)}" else ""}${if (hasExpense && hasIncome) " · " else ""}${if (hasIncome) "收入 ${yuan(into)}" else ""}", color = Color.Gray)
                    }
                }
                items(dayEntries, key = { it.id }) { entry ->
                    val category = categories.firstOrNull { it.id == entry.categoryId }
                    EntryRow(entry, category?.name ?: "已停用分类", category?.iconKey ?: "other") { onEdit(entry) }
                }
                item(key = "divider-$day") { HorizontalDivider(color = CaijiGrey, thickness = 1.dp) }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LedgerEntry, categoryName: String, iconKey: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("entry-row-${entry.id}")
        .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = CircleShape, color = CaijiGrey, modifier = Modifier.size(44.dp)) { Box(contentAlignment = Alignment.Center) { Text(categoryEmoji(iconKey)) } }
        Column(Modifier.weight(1f)) {
            Text(categoryName, style = MaterialTheme.typography.bodyLarge)
            if (!entry.note.isNullOrBlank()) Text(entry.note, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        Text("${if (entry.type == EntryType.EXPENSE) "−" else "+"}${yuan(entry.amountFen)}", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EntryEditor(
    categories: List<Category>, existing: LedgerEntry?, initialType: EntryType,
    onCancel: () -> Unit, onSave: (EntryType, Long, String, LocalDate, String?, (Boolean) -> Unit) -> Unit,
    onDelete: (LedgerEntry) -> Unit
) {
    var type by rememberSaveable(existing?.id) { mutableStateOf(existing?.type ?: initialType) }
    var expression by rememberSaveable(existing?.id) { mutableStateOf(existing?.let { yuan(it.amountFen) } ?: "") }
    var selectedCategory by rememberSaveable(existing?.id) { mutableStateOf(existing?.categoryId) }
    var date by rememberSaveable(existing?.id) { mutableStateOf(existing?.occurredOn ?: LocalDate.now()) }
    var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var changed by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var saving by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var discardPrompt by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var deletePrompt by rememberSaveable(existing?.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val activeCategories = categories.filter { it.type == type && (it.active || it.id == selectedCategory) }
    val calculated = remember(expression) { runCatching { Money.evaluate(expression) }.getOrNull() }
    fun cancel() { if (changed) discardPrompt = true else onCancel() }

    BackHandler(enabled = true) { cancel() }
    if (discardPrompt) AlertDialog(onDismissRequest = { discardPrompt = false }, title = { Text("放弃本次记账？") },
        text = { Text("尚未保存的内容将丢失。") }, confirmButton = { TextButton(onClick = onCancel) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { discardPrompt = false }) { Text("继续编辑") } })
    if (deletePrompt && existing != null) AlertDialog(onDismissRequest = { deletePrompt = false }, title = { Text("删除这笔账单？") },
        text = { Text("删除后会立即从月度汇总中移除。") },
        confirmButton = { TextButton(onClick = { onDelete(existing); deletePrompt = false }, modifier = Modifier.testTag("confirm-delete-entry")) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deletePrompt = false }) { Text("取消") } })

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(CaijiYellow).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::cancel) { Text("取消", color = CaijiInk) }
            Spacer(Modifier.weight(1f))
            listOf(EntryType.EXPENSE to "支出", EntryType.INCOME to "收入").forEach { (candidate, label) ->
                TextButton(onClick = { if (type != candidate) { type = candidate; selectedCategory = null; changed = true } },
                    modifier = Modifier.testTag("entry-type-${candidate.name.lowercase()}")) {
                    Text(if (candidate == type) "$label　━━" else label, color = CaijiInk, style = MaterialTheme.typography.titleLarge)
                }
            }
            if (existing != null) TextButton(onClick = { deletePrompt = true }) { Text("删除", color = Color(0xFFAE2626)) }
        }
        if (activeCategories.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("没有可用分类，请先到设置添加或启用分类", textAlign = TextAlign.Center, color = Color.Gray)
        } else LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            items(activeCategories, key = { it.id }) { category ->
                Column(Modifier.fillMaxWidth().clickable { selectedCategory = category.id; changed = true }
                    .padding(vertical = 9.dp).testTag("entry-category-${category.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(shape = CircleShape, color = if (selectedCategory == category.id) CaijiYellow else CaijiGrey, modifier = Modifier.size(58.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(categoryEmoji(category.iconKey), style = MaterialTheme.typography.headlineSmall) }
                    }
                    Text(category.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Column(Modifier.background(Color.White)) {
            Text(calculated?.let(::yuan) ?: expression.ifEmpty { "0.00" },
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("amount-display"),
                textAlign = TextAlign.End, style = MaterialTheme.typography.displaySmall)
            if (expression.isNotEmpty() && calculated == null) Text("金额格式无效或结果不能为零及负数",
                Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            if (selectedCategory == null) Text("请选择分类", Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                textAlign = TextAlign.End, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { DatePickerDialog(context, { _, year, month, day ->
                    date = LocalDate.of(year, month + 1, day); changed = true
                }, date.year, date.monthValue - 1, date.dayOfMonth).apply { datePicker.maxDate = System.currentTimeMillis() }.show() },
                    modifier = Modifier.testTag("entry-date")) {
                    Text("日期　${date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))}", color = CaijiInk)
                }
                Text("人民币 CNY", color = Color.Gray, modifier = Modifier.padding(top = 12.dp, end = 8.dp))
            }
            OutlinedTextField(note, { note = it.take(20_000); changed = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).testTag("entry-note"),
                label = { Text("备注（选填）") }, singleLine = true)
            listOf(listOf("7", "8", "9", "今天"), listOf("4", "5", "6", "+"),
                listOf("1", "2", "3", "−"), listOf(".", "0", "⌫", "完成")).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth().height(54.dp)) {
                    row.forEach { key ->
                        val complete = key == "完成"
                        Surface(color = if (complete) CaijiYellow else Color.White,
                            modifier = Modifier.weight(1f).fillMaxSize().clickable(enabled = !saving) {
                                when {
                                    key == "今天" -> { date = LocalDate.now(); changed = true }
                                    complete -> {
                                        val amount = calculated
                                        val categoryId = selectedCategory
                                        if (!saving && amount != null && categoryId != null && !date.isAfter(LocalDate.now())) {
                                            saving = true
                                            onSave(type, amount, categoryId, date, note.takeIf(String::isNotBlank)) { saved ->
                                                saving = false
                                                if (saved) changed = false
                                            }
                                        }
                                    }
                                    key == "⌫" -> { expression = expression.dropLast(1); changed = true }
                                    key == "+" || key == "−" -> if (expression.isNotEmpty() && expression.last().isDigit()) {
                                        expression += if (key == "−") "-" else "+"; changed = true
                                    }
                                    key == "." -> {
                                        val token = expression.substringAfterLast('+').substringAfterLast('-')
                                        if (!token.contains('.')) { expression += if (token.isEmpty()) "0." else "."; changed = true }
                                    }
                                    expression.length < 24 -> { expression += key; changed = true }
                                }
                            }.testTag("entry-key-${if (complete) "complete" else key}")) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(if (complete && saving) "保存中" else key, style = if (rowIndex == 3 && complete) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                                    fontWeight = if (complete) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(onCategories: () -> Unit, onExport: (String) -> Unit, onImport: () -> Unit) {
    var exportPrompt by remember { mutableStateOf(false) }
    if (exportPrompt) PasswordDialog("加密完整备份", "选择保存位置", true,
        onDismiss = { exportPrompt = false }, onConfirm = { password, _ -> exportPrompt = false; onExport(password) })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("本机离线账本 · 数据只保存在这台设备", color = Color.Gray)
        SettingsRow("分类管理", "新增、编辑、排序和停用分类", onCategories, Modifier.testTag("settings-categories"))
        Spacer(Modifier.height(10.dp))
        Text("备份与恢复", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Button(onClick = { exportPrompt = true }, modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CaijiYellow, contentColor = CaijiInk)) { Text("加密备份", modifier = Modifier.testTag("settings-export")) }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("从备份恢复", modifier = Modifier.testTag("settings-import")) }
        Text("备份包含全部账单和分类。请设置至少 8 个字符的密码并妥善保存；忘记密码后无法打开备份。恢复会替换当前数据，操作前先另存账单。",
            color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = CaijiGrey)
        Text("关于财记", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("财记 ${BuildConfig.VERSION_NAME} · Android 8.0 及以上", color = Color.Gray)
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().then(modifier), colors = CardDefaults.cardColors(containerColor = CaijiGrey)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = Color.Gray)
        }
    }
}

@Composable
private fun CategoryManager(
    categories: List<Category>, onBack: () -> Unit, onSave: (EntryType, String?, String, String, (Boolean) -> Unit) -> Unit,
    onActive: (String, Boolean) -> Unit, onMove: (String, Int) -> Unit
) {
    var selectedType by remember { mutableStateOf(EntryType.EXPENSE) }
    var edit by remember { mutableStateOf<Category?>(null) }
    var add by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(CaijiYellow).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回", color = CaijiInk) }
            Text("分类管理", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            TextButton(onClick = { add = true; edit = null }) { Text("＋ 添加", color = CaijiInk) }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EntryType.values().forEach { type -> FilterButton(if (type == EntryType.EXPENSE) "支出" else "收入", selectedType == type, Modifier.weight(1f)) { selectedType = type } }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(categories.filter { it.type == selectedType }, key = { it.id }) { category ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = CaijiGrey, shape = CircleShape, modifier = Modifier.size(44.dp).clickable { edit = category }) {
                        Box(contentAlignment = Alignment.Center) { Text(categoryEmoji(category.iconKey)) }
                    }
                    Text(category.name, modifier = Modifier.weight(1f).clickable { edit = category }, color = if (category.active) CaijiInk else Color.Gray)
                    TextButton(onClick = { onMove(category.id, category.sortOrder - 1) }, enabled = category.sortOrder > 0) { Text("↑") }
                    TextButton(onClick = { onMove(category.id, category.sortOrder + 1) }) { Text("↓") }
                    Switch(checked = category.active, onCheckedChange = { onActive(category.id, it) })
                }
                HorizontalDivider(color = CaijiGrey)
            }
        }
    }
    if (add || edit != null) CategoryDialog(edit, selectedType, onDismiss = { add = false; edit = null },
        onSave = { type, id, name, icon, done ->
            onSave(type, id, name, icon) { saved ->
                done(saved)
                if (saved) { add = false; edit = null }
            }
        })
}

@Composable
private fun CategoryDialog(existing: Category?, defaultType: EntryType, onDismiss: () -> Unit,
    onSave: (EntryType, String?, String, String, (Boolean) -> Unit) -> Unit) {
    var type by remember(existing?.id) { mutableStateOf(existing?.type ?: defaultType) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var icon by remember(existing?.id) { mutableStateOf(existing?.iconKey ?: "other") }
    var saving by remember(existing?.id) { mutableStateOf(false) }
    val icons = listOf("restaurant", "shopping_bag", "train", "home", "daily", "movie", "medical", "school", "phone", "favorite", "salary", "gift", "work", "savings", "other")
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(if (existing == null) "添加分类" else "编辑分类") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (existing == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EntryType.values().forEach { FilterButton(if (it == EntryType.EXPENSE) "支出" else "收入", type == it) { type = it } }
            } else Text(if (type == EntryType.EXPENSE) "支出分类" else "收入分类", color = Color.Gray)
            OutlinedTextField(name, { name = it }, label = { Text("分类名称") }, singleLine = true)
            Text("选择图标", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.height(150.dp)) {
                items(icons) { key -> Surface(shape = CircleShape, color = if (icon == key) CaijiYellow else CaijiGrey,
                    modifier = Modifier.padding(3.dp).size(42.dp).clickable { icon = key }) {
                    Box(contentAlignment = Alignment.Center) { Text(categoryEmoji(key)) }
                } }
            }
        } }, confirmButton = { TextButton(onClick = {
            if (!saving) {
                saving = true
                onSave(type, existing?.id, name.trim(), icon) { _ -> saving = false }
            }
        }, enabled = name.trim().isNotEmpty() && !saving) { Text(if (saving) "保存中" else "保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } })
}

@Composable
private fun FilterButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = CaijiInk, contentColor = CaijiYellow)) { Text(text) }
    else OutlinedButton(onClick = onClick, modifier = modifier, border = BorderStroke(1.dp, CaijiInk)) { Text(text, color = CaijiInk) }
}

@Composable
private fun PasswordDialog(title: String, confirmLabel: String, requireConfirmation: Boolean,
    onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val meetsRule = password.codePointCount(0, password.length) >= 8
    val matches = !requireConfirmation || password == confirmation
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(password, { password = it }, label = { Text("至少 8 个字符的密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            if (requireConfirmation) OutlinedTextField(confirmation, { confirmation = it }, label = { Text("再次输入密码") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Text("请妥善保存密码；忘记密码后无法打开备份。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } }, confirmButton = { TextButton(onClick = { onConfirm(password, confirmation.takeIf { requireConfirmation }) }, enabled = meetsRule && matches) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun yuan(fen: Long): String = yuan(BigInteger.valueOf(fen))

private fun yuan(fen: BigInteger): String = BigDecimal(fen, 2).stripTrailingZeros().toPlainString()

private fun categoryEmoji(key: String): String = when (key) {
    "restaurant" -> "🍜"; "shopping_bag" -> "🛍️"; "train" -> "🚆"; "home" -> "🏠"; "daily" -> "🧹"
    "movie" -> "🎬"; "medical" -> "✚"; "school" -> "📚"; "phone" -> "📱"; "favorite" -> "💝"
    "salary" -> "💼"; "gift" -> "🎁"; "work" -> "🧰"; "savings" -> "💰"; else -> "✨"
}
