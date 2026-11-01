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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.caiji.android.data.LedgerPeriodTotals
import app.caiji.android.data.MonthLedgerTotal
import app.caiji.android.data.PeriodLedgerReport
import app.caiji.android.domain.EntryType
import app.caiji.android.ui.theme.CaijiPalette
import java.math.BigInteger
import java.time.Year
import java.time.YearMonth

internal enum class BillListMode { MONTH, YEAR }

@Composable
internal fun BillReportsScreen(
    mode: BillListMode,
    year: Int,
    monthReports: List<MonthLedgerTotal>,
    yearReports: List<Pair<Int, LedgerPeriodTotals>>,
    loading: Boolean,
    error: String?,
    onSelectMode: (BillListMode) -> Unit,
    onBack: () -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onRetryList: () -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    onOpenYear: (Int) -> Unit
) {
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("bills-back")) {
                CaijiIcon(CaijiIconName.BACK, "返回")
            }
            Text("账单", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(48.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                .background(CaijiPalette.SurfaceAlt, RoundedCornerShape(12.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            BillListMode.entries.forEach { candidate ->
                val label = if (candidate == BillListMode.MONTH) "月账单" else "年账单"
                val selected = candidate == mode
                Surface(
                    color = if (selected) CaijiPalette.Primary else CaijiPalette.Surface,
                    contentColor = if (selected) CaijiPalette.OnPrimary else CaijiPalette.SecondaryText,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(42.dp).clickable {
                        onSelectMode(candidate)
                    }.testTag(if (candidate == BillListMode.MONTH) "bill-tab-month" else "bill-tab-year")
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
        if (mode == BillListMode.MONTH) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val oldestYear = yearReports.minOfOrNull { it.first } ?: Year.now().value
                PeriodArrow("上一年", onPreviousYear, "bill-previous-year", enabled = year > oldestYear)
                Text("${year}年", Modifier.padding(horizontal = 16.dp).testTag("bill-selected-year"),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                PeriodArrow("下一年", onNextYear, "bill-next-year", enabled = year < Year.now().value)
            }
            val yearTotals = monthReports.fold(LedgerPeriodTotals(BigInteger.ZERO, BigInteger.ZERO)) { total, item ->
                LedgerPeriodTotals(total.expenseFen + item.totals.expenseFen, total.incomeFen + item.totals.incomeFen)
            }
            ReportSummaryCard("${year} 年度收支", yearTotals)
        } else {
            val allTime = yearReports.fold(LedgerPeriodTotals(BigInteger.ZERO, BigInteger.ZERO)) { total, item ->
                LedgerPeriodTotals(total.expenseFen + item.second.expenseFen, total.incomeFen + item.second.incomeFen)
            }
            ReportSummaryCard("全部账单总结余", allTime)
        }
        if (mode == BillListMode.MONTH) {
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            ) {
                ReportTableHeader("月份", "收入", "支出", "结余")
                monthReports.sortedByDescending { it.month }.forEach { item ->
                    ReportTableRow("${item.month.monthValue}月", item.totals, "month-row-${item.month.monthValue}") {
                        onOpenMonth(item.month)
                    }
                }
            }
        } else {
            val byYear = yearReports.associate { it.first to it.second }
            val firstYear = byYear.keys.minOrNull() ?: year
            val rows = (firstYear..Year.now().value).toList().asReversed()
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                ReportTableHeader("年份", "收入", "支出", "结余")
                rows.forEach { yearValue ->
                    val totals = byYear[yearValue] ?: LedgerPeriodTotals(BigInteger.ZERO, BigInteger.ZERO)
                    ReportTableRow("${yearValue}年", totals, "year-row-$yearValue") { onOpenYear(yearValue) }
                }
            }
        }
        ReportLoadState(loading, error) {
            onRetryList()
        }
    }
}

@Composable
internal fun BillPeriodReportScreen(
    year: Int,
    monthValue: Int,
    report: PeriodLedgerReport?,
    loading: Boolean,
    error: String?,
    selectedType: EntryType,
    selectedCategoryId: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectType: (EntryType) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onEditEntry: (app.caiji.android.data.LedgerEntry) -> Unit
) {
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (selectedCategoryId != null) onSelectCategory(null) else onBack() }, modifier = Modifier.testTag("period-report-back")) {
                CaijiIcon(CaijiIconName.BACK, if (selectedCategoryId != null) "返回分类排行" else "返回账单")
            }
            Text(
                if (selectedCategoryId != null) "分类明细" else if (monthValue == 0) "${year}年报告" else "${year}年${monthValue}月报告",
                Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(48.dp))
        }
        report?.let { ReportSummaryCard("${year}年${if (monthValue == 0) "度" else "${monthValue}月"}收支", it.totals) }
        if (selectedCategoryId == null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
                .background(CaijiPalette.SurfaceAlt, RoundedCornerShape(12.dp)).padding(3.dp)) {
                listOf(EntryType.EXPENSE to "支出排行", EntryType.INCOME to "收入排行").forEach { (type, label) ->
                    val selected = type == selectedType
                    Surface(
                        color = if (selected) CaijiPalette.Primary else CaijiPalette.Surface,
                        contentColor = if (selected) CaijiPalette.OnPrimary else CaijiPalette.SecondaryText,
                        shape = RoundedCornerShape(9.dp), modifier = Modifier.weight(1f).height(42.dp)
                            .clickable { onSelectType(type) }.testTag("report-type-${type.name}")
                    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label) } }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Text("分类排行", Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                report?.categories.orEmpty().forEach { total ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelectCategory(total.category.id) }
                            .testTag("report-category-${total.category.id}")
                            .padding(horizontal = 8.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(categoryEmoji(total.category.iconKey), Modifier.padding(end = 10.dp), style = MaterialTheme.typography.titleMedium)
                        Text(total.category.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("¥${formatYuan(total.amountFen)}", color = CaijiPalette.Ink,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = CaijiPalette.Divider)
                }
                if (!loading && error == null && report?.categories.isNullOrEmpty()) {
                    Text("此期间暂无${if (selectedType == EntryType.EXPENSE) "支出" else "收入"}",
                        Modifier.fillMaxWidth().padding(24.dp), color = CaijiPalette.SecondaryText)
                }
            }
        } else {
            val selectedCategory = report?.categories?.firstOrNull { it.category.id == selectedCategoryId }?.category
            Text(selectedCategory?.name.orEmpty(), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                report?.entries.orEmpty().forEach { entry ->
                    val category = selectedCategory
                    Row(
                        Modifier.fillMaxWidth().clickable { onEditEntry(entry) }
                            .testTag("report-entry-${entry.id}").padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(category?.name ?: "账单", color = CaijiPalette.Ink)
                            Text("${entry.occurredOn.monthValue}月${entry.occurredOn.dayOfMonth}日${entry.note?.let { " · $it" }.orEmpty()}",
                                color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${if (entry.type == EntryType.EXPENSE) "−" else "+"}¥${formatYuan(entry.amountFen)}",
                            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = CaijiPalette.Divider)
                }
                if (!loading && error == null && report?.entries.isNullOrEmpty()) {
                    Text("该分类暂无账单", Modifier.fillMaxWidth().padding(24.dp), color = CaijiPalette.SecondaryText)
                }
            }
        }
        ReportLoadState(loading, error, onRetry)
    }
}

@Composable
private fun ReportSummaryCard(title: String, totals: LedgerPeriodTotals) {
    BoxWithConstraints {
        val compact = maxWidth < 390.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
        Surface(
            color = CaijiPalette.PrimarySoft, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(title, color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
                Text("${if (totals.balanceFen.signum() < 0) "−" else ""}¥${formatYuan(totals.balanceFen.abs())}", Modifier.testTag("report-balance"),
                    style = if (compact) MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
                    else MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (compact) {
                    Text("收入 ¥${formatYuan(totals.incomeFen)}", Modifier.fillMaxWidth().padding(top = 6.dp),
                        color = CaijiPalette.Income, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), maxLines = 2)
                    Text("支出 ¥${formatYuan(totals.expenseFen)}", Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), maxLines = 2)
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("收入 ¥${formatYuan(totals.incomeFen)}", Modifier.weight(1f), color = CaijiPalette.Income,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("支出 ¥${formatYuan(totals.expenseFen)}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodArrow(label: String, onClick: () -> Unit, tag: String, enabled: Boolean) {
    Surface(color = if (enabled) CaijiPalette.SurfaceAlt else CaijiPalette.Surface,
        shape = CircleShape, modifier = Modifier.size(48.dp).clickable(enabled = enabled, onClick = onClick).testTag(tag)) {
        Box(contentAlignment = Alignment.Center) {
            CaijiIcon(CaijiIconName.BACK, contentDescription = label,
                modifier = Modifier.size(18.dp).graphicsLayer(rotationZ = if (label == "下一年") 180f else 0f))
        }
    }
}

@Composable
private fun ReportTableHeader(first: String, income: String, expense: String, balance: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp)) {
        listOf(first, income, expense, balance).forEachIndexed { index, text ->
            Text(text, Modifier.weight(if (index == 0) 0.8f else 1f), color = CaijiPalette.SecondaryText,
                style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
    HorizontalDivider(color = CaijiPalette.Divider)
}

@Composable
private fun ReportTableRow(label: String, totals: LedgerPeriodTotals, tag: String, onClick: () -> Unit) {
    BoxWithConstraints {
        val compact = maxWidth < 390.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag).padding(horizontal = 6.dp, vertical = 11.dp)) {
            if (compact) {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("结余 ${if (totals.balanceFen.signum() < 0) "−" else ""}¥${formatYuan(totals.balanceFen.abs())}",
                    Modifier.fillMaxWidth().padding(top = 4.dp), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text("收入 ¥${formatYuan(totals.incomeFen)}",
                    Modifier.fillMaxWidth().padding(top = 4.dp), color = CaijiPalette.SecondaryText,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), maxLines = 2)
                Text("支出 ¥${formatYuan(totals.expenseFen)}",
                    Modifier.fillMaxWidth(), color = CaijiPalette.SecondaryText,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), maxLines = 2)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(0.8f), style = MaterialTheme.typography.bodyMedium)
                    listOf(totals.incomeFen to false, totals.expenseFen to false, totals.balanceFen to true).forEach { (amount, signed) ->
                        Text("${if (signed && amount.signum() < 0) "−" else ""}¥${formatYuan(amount.abs())}", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        HorizontalDivider(color = CaijiPalette.Divider, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ReportLoadState(loading: Boolean, error: String?, onRetry: () -> Unit) {
    if (loading) CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp).testTag("report-loading"), strokeWidth = 2.dp)
    if (error != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(error, Modifier.weight(1f), color = CaijiPalette.Danger, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        TextButton(onClick = onRetry, modifier = Modifier.testTag("report-retry")) { Text("重试") }
    }
}
