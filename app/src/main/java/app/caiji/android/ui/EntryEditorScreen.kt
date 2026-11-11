package app.caiji.android.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.caiji.android.data.LedgerEntry
import app.caiji.android.R
import app.caiji.android.domain.Category
import app.caiji.android.domain.ClipboardTransaction
import app.caiji.android.domain.EntryType
import app.caiji.android.domain.Money
import app.caiji.android.ui.theme.CaijiPalette
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberUpdatedState

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
internal fun EntryEditorScreen(
    categories: List<Category>,
    existing: LedgerEntry?,
    initialType: EntryType,
    clipboardPrefill: ClipboardTransaction? = null,
    clipboardPrefillId: String? = null,
    onCancel: () -> Unit,
    onSave: (EntryType, Long, String, LocalDate, String?, (Boolean) -> Unit) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
    onSaveCategory: (EntryType, String?, String, String, (Boolean) -> Unit) -> Unit,
    onSetCategoryActive: (String, Boolean, (Boolean) -> Unit) -> Unit,
    onReorderCategory: (String, Int, (Boolean) -> Unit) -> Unit,
    imeVisible: Boolean? = null,
    categoryErrorMessage: String? = null,
    datePickerLauncher: ((LocalDate, (LocalDate?) -> Unit) -> Unit)? = null
) {
    var type by rememberSaveable(existing?.id, clipboardPrefillId) { mutableStateOf(existing?.type ?: clipboardPrefill?.type ?: initialType) }
    var expression by rememberSaveable(existing?.id, clipboardPrefillId) {
        mutableStateOf(existing?.let { BigDecimal.valueOf(it.amountFen, 2).stripTrailingZeros().toPlainString() }
            ?: clipboardPrefill?.let { BigDecimal.valueOf(it.amountFen, 2).stripTrailingZeros().toPlainString() }.orEmpty())
    }
    var selectedCategory by rememberSaveable(existing?.id) { mutableStateOf(existing?.categoryId) }
    var date by rememberSaveable(existing?.id, clipboardPrefillId) { mutableStateOf(existing?.occurredOn ?: clipboardPrefill?.date ?: LocalDate.now()) }
    var note by rememberSaveable(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var noteFocused by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var inputPanelExpanded by rememberSaveable(existing?.id, clipboardPrefillId) { mutableStateOf(clipboardPrefill != null) }
    var changed by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var saving by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var discardPrompt by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var deletePrompt by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var managingCategories by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var mustReselectActiveCategory by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var categoryDragActive by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = if (type == EntryType.EXPENSE) 0 else 1, pageCount = { 2 })
    val pagerScope = rememberCoroutineScope()
    val calculated = runCatching { Money.evaluate(expression) }.getOrNull()
    val canSave = !saving && calculated != null && selectedCategory != null && !date.isAfter(LocalDate.now())
    val showSoftwareKeyboard = imeVisible ?: noteFocused
    val showAmountKeypad = inputPanelExpanded && !showSoftwareKeyboard
    val focusManager = LocalFocusManager.current

    LaunchedEffect(pagerState.settledPage) {
        val settledType = if (pagerState.settledPage == 0) EntryType.EXPENSE else EntryType.INCOME
        if (type != settledType) {
            type = settledType
            selectedCategory = null
            inputPanelExpanded = false
            mustReselectActiveCategory = false
            changed = true
        }
    }

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

    fun showDatePicker() {
        val completeSelection: (LocalDate?) -> Unit = { selected ->
            if (selected != null) {
                date = selected
                changed = true
            }
        }
        if (datePickerLauncher != null) {
            datePickerLauncher.invoke(date, completeSelection)
        } else {
            DatePickerDialog(
                context,
                when (CaijiPalette.selected) {
                    app.caiji.android.ui.theme.CaijiThemeId.AMBER -> R.style.Theme_Caiji_DatePicker_Amber
                    app.caiji.android.ui.theme.CaijiThemeId.BAMBOO -> R.style.Theme_Caiji_DatePicker_Bamboo
                    app.caiji.android.ui.theme.CaijiThemeId.SEA -> R.style.Theme_Caiji_DatePicker_Sea
                    app.caiji.android.ui.theme.CaijiThemeId.MIST -> R.style.Theme_Caiji_DatePicker_Mist
                    app.caiji.android.ui.theme.CaijiThemeId.GRAPHITE -> R.style.Theme_Caiji_DatePicker_Graphite
                },
                { _, year, month, day -> completeSelection(LocalDate.of(year, month + 1, day)) },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth
            ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        }
    }

    LaunchedEffect(categories, selectedCategory, existing?.id) {
        val selected = categories.firstOrNull { it.id == selectedCategory }
        if (existing == null && selectedCategory != null && selected != null && !selected.active) {
            selectedCategory = null
            inputPanelExpanded = false
            mustReselectActiveCategory = true
            changed = true
        }
    }

    BackHandler(enabled = true) {
        when {
            managingCategories -> managingCategories = false
            showSoftwareKeyboard || noteFocused -> focusManager.clearFocus()
            inputPanelExpanded -> { inputPanelExpanded = false; focusManager.clearFocus() }
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
            CaijiPageTopBar(
                title = if (existing == null) "记一笔" else "编辑账单",
                onBack = ::cancel,
                backTag = "entry-editor-back",
                actionTag = "entry-editor-delete-action",
                action = if (existing != null) {
                    {
                        TextButton(onClick = { deletePrompt = true }) {
                        CaijiIcon(CaijiIconName.DELETE, contentDescription = "删除账单", modifier = Modifier.size(20.dp))
                        }
                    }
                } else null
            )

            PagerSegmentedTabs(
                state = pagerState,
                labels = listOf("支出", "收入"),
                tags = listOf("entry-type-expense", "entry-type-income"),
                containerTag = "entry-type-tabs",
                enabled = !saving && !categoryDragActive,
                onSelectPage = { page ->
                    if (page != pagerState.currentPage) pagerScope.launch { pagerState.animateScrollToPage(page) }
                }
            )

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !saving && !managingCategories && !showSoftwareKeyboard && !categoryDragActive,
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("entry-content-pager")
            ) { pageIndex ->
                EntryEditorPageContent(
                    pageType = if (pageIndex == 0) EntryType.EXPENSE else EntryType.INCOME,
                    currentType = type,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    amount = calculated?.let(::formatYuan) ?: expression.ifEmpty { "0.00" },
                    invalidAmount = expression.isNotEmpty() && calculated == null,
                    saving = saving,
                    mustReselectActiveCategory = mustReselectActiveCategory,
                    onAmountClick = { focusManager.clearFocus(); inputPanelExpanded = true },
                    onManageCategories = { focusManager.clearFocus(); managingCategories = true },
                    onSelectCategory = { category ->
                        selectedCategory = category.id
                        mustReselectActiveCategory = false
                        inputPanelExpanded = true
                        changed = true
                    },
                    onDragActive = { categoryDragActive = it },
                    onMove = onReorderCategory
                )
            }

            if (inputPanelExpanded) {
                Column(Modifier.fillMaxWidth().testTag("entry-input-dock")) {
                    HorizontalDivider(color = CaijiPalette.Divider)
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(20_000); changed = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .onFocusChanged { noteFocused = it.isFocused }
                            .testTag("entry-note"),
                        label = { Text("备注（选填）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                    if (showAmountKeypad) AmountKeypad(
                        date = date, saving = saving, canSave = canSave, tagPrefix = "entry",
                        onDate = { showDatePicker() }, onSave = { saveEntry() },
                        onKey = { key ->
                            when (key) {
                                "⌫" -> { expression = expression.dropLast(1); changed = true }
                                "+", "−" -> if (expression.isNotEmpty() && expression.last().isDigit()) {
                                    expression += if (key == "−") "-" else "+"
                                    changed = true
                                }
                                "." -> {
                                    val token = expression.substringAfterLast('+').substringAfterLast('-')
                                    if (!token.contains('.')) {
                                        expression += if (token.isEmpty()) "0." else "."
                                        changed = true
                                    }
                                }
                                else -> if (expression.length < 24) { expression += key; changed = true }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryEditorPageContent(
    pageType: EntryType,
    currentType: EntryType,
    categories: List<Category>,
    selectedCategory: String?,
    amount: String,
    invalidAmount: Boolean,
    saving: Boolean,
    mustReselectActiveCategory: Boolean,
    onAmountClick: () -> Unit,
    onManageCategories: () -> Unit,
    onSelectCategory: (Category) -> Unit,
    onDragActive: (Boolean) -> Unit,
    onMove: (String, Int, (Boolean) -> Unit) -> Unit
) {
    val pageCategories = categories.filter { it.type == pageType && (it.active || it.id == selectedCategory) }
    Column(Modifier.fillMaxSize().testTag("entry-editor-scroll")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("金额", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("¥", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.titleLarge)
                Text(
                    amount,
                    Modifier.weight(1f).clickable(onClick = onAmountClick).testTag("amount-display"),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (invalidAmount) {
                Text("金额格式无效或结果不能为零及负数", color = CaijiPalette.Danger,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("选择分类", color = CaijiPalette.Ink, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onManageCategories, enabled = !saving, modifier = Modifier.testTag("entry-manage-categories")) {
                CaijiIcon(CaijiIconName.CATEGORY, contentDescription = "管理分类", modifier = Modifier.size(18.dp))
                Text("管理分类", modifier = Modifier.padding(start = 5.dp))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
            if (pageCategories.isEmpty()) {
                Text("暂无可用分类，请先新增或启用分类", Modifier.padding(horizontal = 20.dp),
                    color = CaijiPalette.SecondaryText, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium)
            } else CategoryDragGrid(
                visibleCategories = pageCategories,
                allTypeCategories = categories.filter { it.type == pageType }.sortedBy { it.sortOrder },
                selectedCategory = selectedCategory,
                enabled = !saving && pageType == currentType,
                onSelect = onSelectCategory,
                onDragActive = onDragActive,
                onMove = onMove
            )
        }
        if (selectedCategory == null && mustReselectActiveCategory && pageType == currentType) {
            Text("该分类已停用，请重新选择启用分类",
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp),
                textAlign = TextAlign.End, color = CaijiPalette.Danger,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryDragGrid(
    visibleCategories: List<Category>,
    allTypeCategories: List<Category>,
    selectedCategory: String?,
    enabled: Boolean,
    onSelect: (Category) -> Unit,
    onDragActive: (Boolean) -> Unit,
    onMove: (String, Int, (Boolean) -> Unit) -> Unit
) {
    var order by remember(visibleCategories) { mutableStateOf(visibleCategories) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var originalOrder by remember(visibleCategories) { mutableStateOf<List<Category>?>(null) }
    var dragSourceCategories by remember(visibleCategories) { mutableStateOf<List<Category>?>(null) }
    var savingOrder by remember { mutableStateOf(false) }
    val latestVisibleCategories by rememberUpdatedState(visibleCategories)
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).testTag("entry-category-grid")
            .pointerInput(allTypeCategories, enabled) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { start ->
                        val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                            start.x >= info.offset.x && start.x <= info.offset.x + info.size.width &&
                                start.y >= info.offset.y && start.y <= info.offset.y + info.size.height
                        }
                        val id = item?.key as? String
                        if (enabled && !savingOrder && id != null && order.any { it.id == id }) {
                            draggedId = id
                            dragStart = start
                            dragDelta = Offset.Zero
                            originalOrder = order
                            dragSourceCategories = visibleCategories
                            onDragActive(true)
                        }
                    },
                    onDrag = { change, delta ->
                        if (draggedId != null) {
                            change.consume()
                            dragDelta += delta
                            val pointer = dragStart + dragDelta
                            val nearest = gridState.layoutInfo.visibleItemsInfo.minByOrNull { info ->
                                val centerX = info.offset.x + info.size.width / 2f
                                val centerY = info.offset.y + info.size.height / 2f
                                (centerX - pointer.x) * (centerX - pointer.x) + (centerY - pointer.y) * (centerY - pointer.y)
                            }
                            val sourceIndex = order.indexOfFirst { it.id == draggedId }
                            val targetIndex = order.indexOfFirst { it.id == nearest?.key }
                            if (sourceIndex >= 0 && targetIndex >= 0 && targetIndex != sourceIndex) {
                                val moved = order.toMutableList()
                                moved.add(targetIndex, moved.removeAt(sourceIndex))
                                order = moved
                            }
                            val info = gridState.layoutInfo
                            val firstVisibleIndex = info.visibleItemsInfo.firstOrNull()?.index ?: 0
                            val lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            if (pointer.y < 28 && firstVisibleIndex > 0) {
                                scope.launch { gridState.animateScrollToItem((info.visibleItemsInfo.first().index - 1).coerceAtLeast(0)) }
                            } else if (pointer.y > info.viewportEndOffset - 28 && lastVisibleIndex + 1 < info.totalItemsCount) {
                                scope.launch { gridState.animateScrollToItem((lastVisibleIndex + 1).coerceAtMost(info.totalItemsCount - 1)) }
                            }
                        }
                    },
                    onDragEnd = {
                        val id = draggedId
                        val reordered = order
                        val rollbackOrder = originalOrder
                        val sourceCategories = dragSourceCategories
                        originalOrder = null
                        dragSourceCategories = null
                        if (id == null || rollbackOrder == null || reordered == rollbackOrder) {
                            draggedId = null
                            dragDelta = Offset.Zero
                            onDragActive(false)
                        } else {
                            val visibleIds = reordered.map { it.id }.toSet()
                            val visibleIterator = reordered.iterator()
                            val completeOrder = allTypeCategories.map { category -> if (category.id in visibleIds) visibleIterator.next() else category }
                            val target = completeOrder.indexOfFirst { it.id == id }
                            savingOrder = true
                            onMove(id, target) { saved ->
                                if (!saved && latestVisibleCategories == sourceCategories) order = rollbackOrder
                                savingOrder = false
                                draggedId = null
                                dragDelta = Offset.Zero
                                onDragActive(false)
                            }
                        }
                    },
                    onDragCancel = {
                        val rollbackOrder = originalOrder
                        if (rollbackOrder != null) {
                            if (latestVisibleCategories == dragSourceCategories) order = rollbackOrder
                            originalOrder = null
                            dragSourceCategories = null
                            draggedId = null
                            dragDelta = Offset.Zero
                            onDragActive(false)
                        }
                    }
                )
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items(order, key = { it.id }) { category ->
            val isDragged = category.id == draggedId
            val itemInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == category.id }
            val dragTranslationX = if (isDragged && itemInfo != null) dragStart.x + dragDelta.x - (itemInfo.offset.x + itemInfo.size.width / 2f) else 0f
            val dragTranslationY = if (isDragged && itemInfo != null) dragStart.y + dragDelta.y - (itemInfo.offset.y + itemInfo.size.height / 2f) else 0f
            Column(
                modifier = Modifier.fillMaxWidth()
                    .graphicsLayer {
                        if (isDragged) {
                            translationX = dragTranslationX
                            translationY = dragTranslationY
                            scaleX = 1.06f
                            scaleY = 1.06f
                            shadowElevation = 8.dp.toPx()
                        }
                    }
                    .clickable(enabled = enabled && draggedId == null && !savingOrder) { onSelect(category) }
                    .padding(vertical = 4.dp).testTag("entry-category-${category.id}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = if (category.id == selectedCategory || isDragged) CaijiPalette.PrimarySoft else CaijiPalette.SurfaceAlt,
                    modifier = Modifier.size(43.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(categoryEmoji(category.iconKey), style = MaterialTheme.typography.titleMedium) }
                }
                Text(category.name, color = if (category.id == selectedCategory) CaijiPalette.Ink else CaijiPalette.SecondaryText,
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
