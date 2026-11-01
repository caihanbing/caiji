package app.caiji.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.caiji.android.data.LedgerEntry
import app.caiji.android.domain.Category
import app.caiji.android.domain.EntryType
import app.caiji.android.ui.theme.CaijiPalette
import java.math.BigInteger
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    onCreateEntry: () -> Unit
) {
    val balanceFen = incomeFen - expenseFen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CaijiPalette.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("财记", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("本机账本", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            val compactHeader = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.2f
            if (compactHeader) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("收支明细", style = MaterialTheme.typography.titleLarge)
                    Text("按月查看", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    MonthSelector(month, onPrevious, onNext, Modifier.fillMaxWidth())
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("收支明细", style = MaterialTheme.typography.titleLarge)
                        Text("按月查看", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    }
                    MonthSelector(month, onPrevious, onNext)
                }
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.large,
            color = CaijiPalette.PrimarySoft
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 15.dp)) {
                Text("本月结余", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${if (balanceFen.signum() < 0) "−" else ""}¥${formatYuan(balanceFen.abs())}",
                    style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("monthly-balance")
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(26.dp)
                ) {
                    SummaryAmount("本月收入", incomeFen, CaijiPalette.Income, Modifier.weight(1f))
                    SummaryAmount("本月支出", expenseFen, CaijiPalette.Ink, Modifier.weight(1f))
                }
            }
        }
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
                        val dailySummary = buildList {
                            if (expense.signum() > 0) add("支出 ¥${formatYuan(expense)}")
                            if (income.signum() > 0) add("收入 ¥${formatYuan(income)}")
                        }.joinToString(" · ")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 13.dp, bottom = 7.dp)
                        ) {
                            Text(
                                day.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)),
                                color = CaijiPalette.Ink,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                dailySummary,
                                color = CaijiPalette.SecondaryText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("daily-summary-$day")
                            )
                        }
                    }
                    items(dayEntries, key = { it.id }) { entry ->
                        val category = categories.firstOrNull { it.id == entry.categoryId }
                        EntryRow(
                            entry = entry,
                            categoryName = category?.name ?: "已停用分类",
                            iconKey = category?.iconKey ?: "other",
                            onClick = { onEdit(entry) }
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
}

@Composable
private fun MonthSelector(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        IconButton(onClick = onPrevious, modifier = Modifier.testTag("month-previous")) {
            CaijiIcon(CaijiIconName.BACK, contentDescription = "上个月")
        }
        Text(
            "${month.year}年${month.monthValue}月",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("home-month")
        )
        IconButton(onClick = onNext, modifier = Modifier.testTag("month-next")) {
            CaijiIcon(
                CaijiIconName.BACK,
                contentDescription = "下个月",
                modifier = Modifier.graphicsLayer(rotationZ = 180f)
            )
        }
    }
}

@Composable
private fun SummaryAmount(label: String, amountFen: BigInteger, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
        Text(
            "¥${formatYuan(amountFen)}",
            color = color,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
                Text("开始记账", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LedgerEntry, categoryName: String, iconKey: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("entry-row-${entry.id}")
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = CaijiPalette.SurfaceAlt,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(categoryEmoji(iconKey), style = MaterialTheme.typography.titleMedium)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                categoryName,
                modifier = Modifier.testTag("ledger-category-name-${entry.id}"),
                color = CaijiPalette.Ink,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.note.isNullOrBlank()) {
                Text(
                    entry.note,
                    color = CaijiPalette.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            "${if (entry.type == EntryType.EXPENSE) "−" else "+"}¥${formatYuan(entry.amountFen)}",
            modifier = Modifier.widthIn(max = 140.dp).testTag("ledger-amount-${entry.id}"),
            color = if (entry.type == EntryType.INCOME) CaijiPalette.Income else CaijiPalette.Ink,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun dailyTotal(entries: List<LedgerEntry>, type: EntryType): BigInteger =
    entries.asSequence()
        .filter { it.type == type }
        .fold(BigInteger.ZERO) { sum, entry -> sum + BigInteger.valueOf(entry.amountFen) }
