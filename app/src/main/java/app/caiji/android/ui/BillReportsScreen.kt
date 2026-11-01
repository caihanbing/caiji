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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
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
            if (mode == BillListMode.MONTH) {
                val oldestYear = yearReports.minOfOrNull { it.first } ?: Year.now().value
                PeriodArrowButton("上一年", "bill-previous-year", isNext = false,
                    enabled = year > oldestYear, onClick = onPreviousYear)
                Text("${year}年", Modifier.padding(horizontal = 2.dp).testTag("bill-selected-year"),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                PeriodArrowButton("下一年", "bill-next-year", isNext = true,
                    enabled = year < Year.now().value, onClick = onNextYear)
            }
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
            val yearTotals = monthReports.fold(LedgerPeriodTotals(BigInteger.ZERO, BigInteger.ZERO)) { total, item ->
                LedgerPeriodTotals(total.expenseFen + item.totals.expenseFen, total.incomeFen + item.totals.incomeFen)
            }
            PeriodSummaryCard("${year}年结余", yearTotals,
                incomeLabel = "${year}年收入", expenseLabel = "${year}年支出")
        } else {
            val allTime = yearReports.fold(LedgerPeriodTotals(BigInteger.ZERO, BigInteger.ZERO)) { total, item ->
                LedgerPeriodTotals(total.expenseFen + item.second.expenseFen, total.incomeFen + item.second.incomeFen)
            }
            PeriodSummaryCard("全部账单总结余", allTime, incomeLabel = "累计收入", expenseLabel = "累计支出")
        }
        if (mode == BillListMode.MONTH) {
            val rows = monthReports.sortedByDescending { it.month }.map { item ->
                BillTableRow("${item.month.monthValue}月", item.totals, "month-row-${item.month.monthValue}") {
                    onOpenMonth(item.month)
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                BillPeriodTable("月份", rows)
            }
        } else {
            val byYear = yearReports.associate { it.first to it.second }
            val firstYear = byYear.keys.minOrNull() ?: year
            val rows = (firstYear..Year.now().value).toList().asReversed()
            val tableRows = rows.map { yearValue ->
                    val totals = byYear[yearValue] ?: LedgerPeriodTotals(BigInteger.ZERO, BigInteger.ZERO)
                    BillTableRow("${yearValue}年", totals, "year-row-$yearValue") { onOpenYear(yearValue) }
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                BillPeriodTable("年份", tableRows)
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
        report?.let {
            PeriodSummaryCard(
                "${year}年${if (monthValue == 0) "结余" else "${monthValue}月结余"}",
                it.totals,
                incomeLabel = if (monthValue == 0) "${year}年收入" else "${monthValue}月收入",
                expenseLabel = if (monthValue == 0) "${year}年支出" else "${monthValue}月支出"
            )
        }
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

private data class BillTableRow(val label: String, val totals: LedgerPeriodTotals, val tag: String, val onClick: () -> Unit)

@Composable
private fun BillPeriodTable(heading: String, rows: List<BillTableRow>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val maxNumberLength = rows.maxOfOrNull { row ->
            listOf(
                "¥${formatYuan(row.totals.incomeFen)}",
                "¥${formatYuan(row.totals.expenseFen)}",
                "${if (row.totals.balanceFen.signum() < 0) "−" else ""}¥${formatYuan(row.totals.balanceFen.abs())}"
            ).maxOf(String::length)
        } ?: 8
        val firstWidth = 112.dp
        val numericWidth = maxOf(112.dp, (maxNumberLength * 8.3f * fontScale + 20f).dp)
        val tableWidth = maxOf(maxWidth, firstWidth + numericWidth * 3)
        Column(Modifier.horizontalScroll(rememberScrollState()).testTag("bill-table-scroll")) {
            Column(Modifier.width(tableWidth)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(heading, Modifier.width(firstWidth), color = CaijiPalette.SecondaryText,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    TableHeaderCell("收入", numericWidth, "bill-table-header-income")
                    TableHeaderCell("支出", numericWidth, "bill-table-header-expense")
                    TableHeaderCell("结余", numericWidth, "bill-table-header-balance")
                }
                HorizontalDivider(color = CaijiPalette.Divider)
                rows.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = item.onClick).testTag(item.tag)
                            .padding(horizontal = 6.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.label, Modifier.width(firstWidth), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TableAmount("¥${formatYuan(item.totals.incomeFen)}", numericWidth, CaijiPalette.Ink)
                        TableAmount("¥${formatYuan(item.totals.expenseFen)}", numericWidth, CaijiPalette.Ink)
                        TableAmount(
                            "${if (item.totals.balanceFen.signum() < 0) "−" else ""}¥${formatYuan(item.totals.balanceFen.abs())}",
                            numericWidth, CaijiPalette.Ink
                        )
                    }
                    HorizontalDivider(color = CaijiPalette.Divider)
                }
            }
        }
    }
}

@Composable
private fun TableHeaderCell(label: String, width: androidx.compose.ui.unit.Dp, tag: String) {
    Text(label, Modifier.width(width).testTag(tag), color = CaijiPalette.SecondaryText,
        style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, maxLines = 1)
}

@Composable
private fun TableAmount(value: String, width: androidx.compose.ui.unit.Dp, color: androidx.compose.ui.graphics.Color) {
    Text(value, Modifier.width(width), color = color,
        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
        textAlign = TextAlign.End, maxLines = 1, softWrap = false)
}

@Composable
private fun ReportLoadState(loading: Boolean, error: String?, onRetry: () -> Unit) {
    if (loading) CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp).testTag("report-loading"), strokeWidth = 2.dp)
    if (error != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(error, Modifier.weight(1f), color = CaijiPalette.Danger, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        TextButton(onClick = onRetry, modifier = Modifier.testTag("report-retry")) { Text("重试") }
    }
}
