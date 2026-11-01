package app.caiji.android.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.caiji.android.data.LedgerEntry
import app.caiji.android.domain.Category
import app.caiji.android.domain.EntryType
import app.caiji.android.domain.Money
import app.caiji.android.ui.theme.CaijiPalette
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun EntryEditorScreen(
    categories: List<Category>,
    existing: LedgerEntry?,
    initialType: EntryType,
    onCancel: () -> Unit,
    onSave: (EntryType, Long, String, LocalDate, String?, (Boolean) -> Unit) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
    onSaveCategory: (EntryType, String?, String, String, (Boolean) -> Unit) -> Unit,
    onSetCategoryActive: (String, Boolean) -> Unit,
    onReorderCategory: (String, Int, (Boolean) -> Unit) -> Unit,
    imeVisible: Boolean? = null,
    categoryErrorMessage: String? = null
) {
    var type by rememberSaveable(existing?.id) { mutableStateOf(existing?.type ?: initialType) }
    var expression by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.let { BigDecimal.valueOf(it.amountFen, 2).stripTrailingZeros().toPlainString() } ?: "")
    }
    var selectedCategory by rememberSaveable(existing?.id) { mutableStateOf(existing?.categoryId) }
    var date by rememberSaveable(existing?.id) { mutableStateOf(existing?.occurredOn ?: LocalDate.now()) }
    var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var noteFocused by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var keypadExpanded by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var changed by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var saving by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var discardPrompt by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var deletePrompt by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var managingCategories by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var mustReselectActiveCategory by rememberSaveable(existing?.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val activeCategories = categories.filter { it.type == type && (it.active || it.id == selectedCategory) }
    val calculated = runCatching { Money.evaluate(expression) }.getOrNull()
    val canSave = !saving && calculated != null && selectedCategory != null && !date.isAfter(LocalDate.now())
    val showSoftwareKeyboard = imeVisible ?: noteFocused
    val showAmountKeypad = keypadExpanded && !showSoftwareKeyboard
    val focusManager = LocalFocusManager.current

    fun cancel() {
        if (changed) discardPrompt = true else onCancel()
    }

    fun saveEntry() {
        val amount = runCatching { Money.evaluate(expression) }.getOrNull() ?: return
        val categoryId = selectedCategory ?: return
        if (!canSave) return
        saving = true
        onSave(type, amount, categoryId, date, note.takeIf(String::isNotBlank)) { saved ->
            saving = false
            if (saved) changed = false
        }
    }

    LaunchedEffect(categories, selectedCategory, existing?.id) {
        val selected = categories.firstOrNull { it.id == selectedCategory }
        if (existing == null && selectedCategory != null && selected != null && !selected.active) {
            selectedCategory = null
            mustReselectActiveCategory = true
            changed = true
        }
    }

    BackHandler(enabled = true) {
        when {
            managingCategories -> managingCategories = false
            keypadExpanded && !noteFocused -> keypadExpanded = false
            noteFocused -> focusManager.clearFocus()
            else -> cancel()
        }
    }

    if (!managingCategories && discardPrompt) {
        AlertDialog(
            onDismissRequest = { discardPrompt = false },
            title = { Text("放弃本次记账？") },
            text = { Text("尚未保存的内容将丢失。") },
            confirmButton = {
                TextButton(onClick = onCancel) { Text("放弃", color = CaijiPalette.Danger) }
            },
            dismissButton = { TextButton(onClick = { discardPrompt = false }) { Text("继续编辑") }
            }
        )
    }
    if (!managingCategories && deletePrompt && existing != null) {
        AlertDialog(
            onDismissRequest = { deletePrompt = false },
            title = { Text("删除这笔账单？") },
            text = { Text("删除后会立即从月度汇总中移除。") },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(existing); deletePrompt = false },
                    modifier = Modifier.testTag("confirm-delete-entry")
                ) { Text("删除", color = CaijiPalette.Danger) }
            },
            dismissButton = { TextButton(onClick = { deletePrompt = false }) { Text("取消") } }
        )
    }

    if (managingCategories) {
        CategoryManagerScreen(
            categories = categories,
            onBack = { managingCategories = false },
            onSave = onSaveCategory,
            onActive = onSetCategoryActive,
            onMove = onReorderCategory,
            initialType = type,
            errorMessage = categoryErrorMessage
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .background(CaijiPalette.Background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = ::cancel) {
                    CaijiIcon(CaijiIconName.BACK, contentDescription = "取消编辑")
                }
                Text(
                    if (existing == null) "记一笔" else "编辑账单",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (existing != null) {
                    TextButton(onClick = { deletePrompt = true }) {
                        CaijiIcon(CaijiIconName.DELETE, contentDescription = "删除账单", modifier = Modifier.size(20.dp))
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }

            EntryTypeToggle(type) { candidate ->
                if (type != candidate) {
                    type = candidate
                    selectedCategory = null
                    keypadExpanded = false
                    mustReselectActiveCategory = false
                    changed = true
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("entry-editor-scroll")
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("金额", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("¥", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.titleLarge)
                    Text(
                        calculated?.let(::formatYuan) ?: expression.ifEmpty { "0.00" },
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                focusManager.clearFocus()
                                if (selectedCategory != null) keypadExpanded = true
                            }
                            .testTag("amount-display"),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (expression.isNotEmpty() && calculated == null) {
                    Text(
                        "金额格式无效或结果不能为零及负数",
                        color = CaijiPalette.Danger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选择分类", color = CaijiPalette.Ink, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { focusManager.clearFocus(); managingCategories = true },
                    enabled = !saving,
                    modifier = Modifier.testTag("entry-manage-categories")
                ) {
                    CaijiIcon(CaijiIconName.CATEGORY, contentDescription = "管理分类", modifier = Modifier.size(18.dp))
                    Text("管理分类", modifier = Modifier.padding(start = 5.dp))
                }
            }

            if (activeCategories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无可用分类，请先新增或启用分类",
                        color = CaijiPalette.SecondaryText,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .padding(horizontal = 12.dp)
                        .testTag("entry-category-grid"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(activeCategories, key = { it.id }) { category ->
                        val selected = selectedCategory == category.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !saving) {
                                    selectedCategory = category.id
                                    mustReselectActiveCategory = false
                                    keypadExpanded = true
                                    changed = true
                                }
                                .padding(vertical = 4.dp)
                                .testTag("entry-category-${category.id}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(13.dp),
                                color = if (selected) CaijiPalette.PrimarySoft else CaijiPalette.SurfaceAlt,
                                modifier = Modifier.size(43.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(categoryEmoji(category.iconKey), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            Text(
                                category.name,
                                color = if (selected) CaijiPalette.Ink else CaijiPalette.SecondaryText,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (selectedCategory == null) {
                Text(
                    if (mustReselectActiveCategory) "该分类已停用，请重新选择启用分类" else "请选择分类",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 3.dp),
                    textAlign = TextAlign.End,
                    color = if (mustReselectActiveCategory) CaijiPalette.Danger else CaijiPalette.SecondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider(color = CaijiPalette.Divider)
            TextButton(
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> date = LocalDate.of(year, month + 1, day); changed = true },
                        date.year,
                        date.monthValue - 1,
                        date.dayOfMonth
                    ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                },
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("entry-date")
                    .padding(horizontal = 16.dp)
            ) {
                CaijiIcon(CaijiIconName.DATE, contentDescription = "日期")
                Text(
                    "日期 · ${date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))}",
                    modifier = Modifier.padding(start = 8.dp),
                    color = CaijiPalette.Ink
                )
                Spacer(Modifier.weight(1f))
                CaijiIcon(
                    CaijiIconName.BACK,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer(rotationZ = 180f)
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(20_000); changed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .onFocusChanged { noteFocused = it.isFocused }
                    .testTag("entry-note"),
                label = { Text("备注（选填）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
            }
            if (showAmountKeypad) {
                val keys = listOf(
                    listOf("7", "8", "9", "今天"),
                    listOf("4", "5", "6", "+"),
                    listOf("1", "2", "3", "−"),
                    listOf(".", "0", "⌫", "收起")
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    keys.forEachIndexed { rowIndex, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(43.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            row.forEach { key ->
                                val isDismiss = key == "收起"
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (isDismiss) CaijiPalette.PrimarySoft else CaijiPalette.Surface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .clickable(enabled = !saving) {
                                            when {
                                                key == "今天" -> { date = LocalDate.now(); changed = true }
                                                isDismiss -> keypadExpanded = false
                                                key == "⌫" -> { expression = expression.dropLast(1); changed = true }
                                                key == "+" || key == "−" -> if (expression.isNotEmpty() && expression.last().isDigit()) {
                                                    expression += if (key == "−") "-" else "+"
                                                    changed = true
                                                }
                                                key == "." -> {
                                                    val token = expression.substringAfterLast('+').substringAfterLast('-')
                                                    if (!token.contains('.')) {
                                                        expression += if (token.isEmpty()) "0." else "."
                                                        changed = true
                                                    }
                                                }
                                                expression.length < 24 -> { expression += key; changed = true }
                                            }
                                        }
                                        .testTag("entry-key-${if (isDismiss) "dismiss" else key}")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            key,
                                            color = CaijiPalette.Ink,
                                            style = if (rowIndex == 3 && isDismiss) MaterialTheme.typography.labelLarge
                                            else MaterialTheme.typography.titleMedium,
                                            fontWeight = if (isDismiss) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = ::saveEntry,
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp)
                    .testTag("entry-save"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CaijiPalette.Primary,
                    contentColor = CaijiPalette.OnPrimary,
                    disabledContainerColor = CaijiPalette.SurfaceAlt,
                    disabledContentColor = CaijiPalette.SecondaryText
                )
            ) {
                Text(if (saving) "保存中" else "保存账单", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EntryTypeToggle(type: EntryType, onTypeSelected: (EntryType) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(CaijiPalette.SurfaceAlt, RoundedCornerShape(11.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(EntryType.EXPENSE to "支出", EntryType.INCOME to "收入").forEach { (candidate, label) ->
            val selected = candidate == type
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selected) CaijiPalette.PrimarySoft else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onTypeSelected(candidate) }
                    )
                    .testTag("entry-type-${candidate.name.lowercase()}")
                    .height(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        color = if (selected) CaijiPalette.Ink else CaijiPalette.SecondaryText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
