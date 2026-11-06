package app.caiji.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.caiji.android.data.LedgerEntry
import app.caiji.android.data.LedgerPeriodTotals
import app.caiji.android.domain.Category
import app.caiji.android.domain.EntryType
import app.caiji.android.ui.theme.CaijiPalette
import java.math.BigInteger
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun LedgerHome(
    month: YearMonth,
    expenseFen: BigInteger,
    incomeFen: BigInteger,
    entries: List<LedgerEntry>,
    categories: List<Category>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onEdit: (LedgerEntry) -> Unit,
    onCreateEntry: () -> Unit,
    onOpenBills: () -> Unit = {},
    onDelete: (LedgerEntry, (Boolean) -> Unit) -> Unit = { _, complete -> complete(false) },
    onModalVisibilityChange: (Boolean) -> Unit = {}
) {
    var openSwipeEntryId by remember { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<LedgerEntry?>(null) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(deleteCandidate) { onModalVisibilityChange(deleteCandidate != null) }
    DisposableEffect(onModalVisibilityChange) { onDispose { onModalVisibilityChange(false) } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CaijiPalette.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onOpenBills, modifier = Modifier.testTag("ledger-open-bills")) {
                CaijiIcon(CaijiIconName.DATE, "汇总", Modifier.size(18.dp))
                Text("汇总", Modifier.padding(start = 5.dp), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            MonthSelector(month, onPrevious, onNext)
        }
        PeriodSummaryCard(
            title = "本月结余",
            totals = LedgerPeriodTotals(expenseFen, incomeFen),
            incomeLabel = "本月收入",
            expenseLabel = "本月支出",
            modifier = Modifier.testTag("monthly-balance")
        )
        if (entries.isEmpty()) {
            EmptyLedgerState(onCreateEntry, Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)
            ) {
                entries.groupBy { it.occurredOn }.forEach { (day, dayEntries) ->
                    item(key = "day-$day") {
                        val expense = dailyTotal(dayEntries, EntryType.EXPENSE)
                        val income = dailyTotal(dayEntries, EntryType.INCOME)
                        val hasSummary = expense.signum() > 0 || income.signum() > 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 18.dp, top = 13.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                day.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)),
                                modifier = Modifier.weight(if (hasSummary) 0.9f else 1f).testTag("daily-date-$day"),
                                color = CaijiPalette.Ink,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (hasSummary) Box(
                                modifier = Modifier.weight(1.1f),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                        .testTag("daily-summary-$day").semantics(mergeDescendants = true) {},
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (income.signum() > 0) Text("收入 ¥${formatYuan(income)}", color = CaijiPalette.SecondaryText,
                                        style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                                    if (expense.signum() > 0) Text("支出 ¥${formatYuan(expense)}", color = CaijiPalette.SecondaryText,
                                        style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                    items(dayEntries, key = { it.id }) { entry ->
                        val category = categories.firstOrNull { it.id == entry.categoryId }
                        EntryRow(
                            entry = entry,
                            categoryName = category?.name ?: "已停用分类",
                            iconKey = category?.iconKey ?: "other",
                            onClick = { onEdit(entry) },
                            deleteRevealed = openSwipeEntryId == entry.id,
                            onSwipeChanged = { openSwipeEntryId = if (it) entry.id else null },
                            onRequestDelete = { deleteCandidate = entry; openSwipeEntryId = null }
                        )
                    }
                    item(key = "divider-$day") {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 3.dp),
                            color = CaijiPalette.Divider,
                            thickness = 1.dp
                        )
                    }
                }
            }
        }
    }
    deleteCandidate?.let { entry ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteCandidate = null },
            title = { Text("删除这笔账单？") },
            text = { Text("删除后会立即从月度汇总中移除。") },
            confirmButton = {
                TextButton(onClick = {
                    if (!deleting) {
                        deleting = true
                        onDelete(entry) { _ ->
                            deleting = false
                            deleteCandidate = null
                        }
                    }
                }, enabled = !deleting, modifier = Modifier.testTag("ledger-confirm-delete")) {
                    Text(if (deleting) "删除中" else "删除", color = CaijiPalette.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }, enabled = !deleting) { Text("取消") } }
        )
    }
}

@Composable
private fun MonthSelector(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        PeriodArrowButton("上个月", "month-previous", isNext = false, onClick = onPrevious)
        Text(
            "${month.year}年${month.monthValue}月",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp).testTag("home-month")
        )
        PeriodArrowButton("下个月", "month-next", isNext = true, onClick = onNext)
    }
}

@Composable
private fun EmptyLedgerState(onCreateEntry: () -> Unit, modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = CaijiPalette.PrimarySoft,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CaijiIcon(CaijiIconName.ADD, contentDescription = null, modifier = Modifier.size(25.dp))
                }
            }
            Text("本月还没有账单", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "记下第一笔收支，月度明细会显示在这里",
                color = CaijiPalette.SecondaryText,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onCreateEntry,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("empty-start-entry"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CaijiPalette.Primary,
                    contentColor = CaijiPalette.OnPrimary
                )
            ) {
                Text("记一笔", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: LedgerEntry,
    categoryName: String,
    iconKey: String,
    onClick: () -> Unit,
    deleteRevealed: Boolean,
    onSwipeChanged: (Boolean) -> Unit,
    onRequestDelete: () -> Unit
) {
    val actionWidth = 100.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val actionWidthPx = with(density) { actionWidth.toPx() }
    var offsetX by remember(entry.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(deleteRevealed) { offsetX = if (deleteRevealed) -actionWidthPx else 0f }
    Box(Modifier.fillMaxWidth().clipToBounds().testTag("entry-row-${entry.id}")) {
        Surface(
            color = CaijiPalette.DangerSoft,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.CenterEnd).size(width = actionWidth, height = 48.dp)
                .clickable(onClick = onRequestDelete).testTag("ledger-delete-${entry.id}")
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                CaijiIcon(CaijiIconName.DELETE, "删除账单", Modifier.size(18.dp))
                Text("删除", Modifier.padding(start = 4.dp), color = CaijiPalette.Danger, style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(entry.id, deleteRevealed) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(-actionWidthPx, 0f)
                        },
                        onDragEnd = {
                            val reveal = offsetX < -actionWidthPx * 0.4f
                            offsetX = if (reveal) -actionWidthPx else 0f
                            onSwipeChanged(reveal)
                        },
                        onDragCancel = {
                            offsetX = if (deleteRevealed) -actionWidthPx else 0f
                            onSwipeChanged(deleteRevealed)
                        }
                    )
                }
                .clickable(onClick = { if (deleteRevealed) onSwipeChanged(false) else onClick() })
                .background(CaijiPalette.Background)
                .testTag("entry-row-content-${entry.id}")
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = CaijiPalette.SurfaceAlt, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(categoryEmoji(iconKey), style = MaterialTheme.typography.titleMedium) }
            }
            Column(Modifier.weight(1f)) {
                Text(categoryName, modifier = Modifier.testTag("ledger-category-name-${entry.id}"), color = CaijiPalette.Ink,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!entry.note.isNullOrBlank()) Text(entry.note, color = CaijiPalette.SecondaryText,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${if (entry.type == EntryType.EXPENSE) "−" else "+"}¥${formatYuan(entry.amountFen)}",
                modifier = Modifier.widthIn(max = 140.dp).testTag("ledger-amount-${entry.id}"),
                color = if (entry.type == EntryType.INCOME) CaijiPalette.Income else CaijiPalette.Ink,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun dailyTotal(entries: List<LedgerEntry>, type: EntryType): BigInteger =
    entries.asSequence()
        .filter { it.type == type }
        .fold(BigInteger.ZERO) { sum, entry -> sum + BigInteger.valueOf(entry.amountFen) }
