package app.caiji.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
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
import app.caiji.android.data.PeriodRankingSnapshot
import app.caiji.android.domain.EntryType
import app.caiji.android.ui.theme.CaijiPalette
import java.math.BigInteger
import java.time.Year
import java.time.YearMonth
import kotlinx.coroutines.launch

internal enum class BillListMode { MONTH, YEAR }
private const val FIRST_BILL_YEAR = 1900

@Composable
@OptIn(ExperimentalFoundationApi::class)
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
    onLoadLists: (Int) -> Unit = {},
    onOpenMonth: (YearMonth) -> Unit,
    onOpenYear: (Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = mode.ordinal, pageCount = { BillListMode.entries.size })
    val pagerScope = rememberCoroutineScope()
    val visibleMonthReports = monthReports.filter { it.month.year == year }
    LaunchedEffect(year) { onLoadLists(year) }
    LaunchedEffect(mode) { if (pagerState.currentPage != mode.ordinal) pagerState.animateScrollToPage(mode.ordinal) }
    LaunchedEffect(pagerState.settledPage) {
        val settled = BillListMode.entries[pagerState.settledPage]
        if (settled != mode) onSelectMode(settled)
    }
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        CaijiPageTopBar(
            title = "汇总",
            onBack = onBack,
            backTag = "bills-back",
            actionTag = "bills-header-action"
        )
        if (mode == BillListMode.MONTH) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                PeriodArrowButton("上一年", "bill-previous-year", isNext = false,
                    enabled = year > FIRST_BILL_YEAR, onClick = onPreviousYear)
                Text("${year}年", Modifier.padding(horizontal = 2.dp).testTag("bill-selected-year"),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                PeriodArrowButton("下一年", "bill-next-year", isNext = true,
                    enabled = year < Year.now().value, onClick = onNextYear)
            }
        }
        PagerSegmentedTabs(
            state = pagerState,
            labels = listOf("月账单", "年账单"),
            tags = listOf("bill-tab-month", "bill-tab-year"),
            containerTag = "bill-tabs",
            onSelectPage = { page -> if (page != pagerState.currentPage) pagerScope.launch { pagerState.animateScrollToPage(page) } }
        )
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth().testTag("bill-mode-pager")) { pageIndex ->
            when (BillListMode.entries[pageIndex]) {
                BillListMode.MONTH -> {
                    val yearTotals = visibleMonthReports.fold(LedgerPeriodTotals(BigInteger.ZERO, BigInteger.ZERO)) { total, item ->
                        LedgerPeriodTotals(total.expenseFen + item.totals.expenseFen, total.incomeFen + item.totals.incomeFen)
                    }
                    Column(Modifier.fillMaxSize()) {
                        PeriodSummaryCard("${year}年结余", yearTotals,
                            incomeLabel = "${year}年收入", expenseLabel = "${year}年支出")
                        val rows = visibleMonthReports.sortedByDescending { it.month }.map { item ->
                            BillTableRow("${item.month.monthValue}月", item.totals, "month-row-${item.month.monthValue}") {
                                onOpenMonth(item.month)
                            }
                        }
                        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                            BillPeriodTable("月份", rows)
                        }
                    }
                }
                BillListMode.YEAR -> {
                    val allTime = yearReports.fold(LedgerPeriodTotals(BigInteger.ZERO, BigInteger.ZERO)) { total, item ->
                        LedgerPeriodTotals(total.expenseFen + item.second.expenseFen, total.incomeFen + item.second.incomeFen)
                    }
                    val byYear = yearReports.associate { it.first to it.second }
                    val firstYear = byYear.keys.minOrNull() ?: year
                    val rows = (firstYear..Year.now().value).toList().asReversed()
                    val tableRows = rows.map { yearValue ->
                    val totals = byYear[yearValue] ?: LedgerPeriodTotals(BigInteger.ZERO, BigInteger.ZERO)
                    BillTableRow("${yearValue}年", totals, "year-row-$yearValue") { onOpenYear(yearValue) }
                    }
                    Column(Modifier.fillMaxSize()) {
                        PeriodSummaryCard("全部账单总结余", allTime, incomeLabel = "累计收入", expenseLabel = "累计支出")
                        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                            BillPeriodTable("年份", tableRows)
                        }
                    }
                }
            }
        }
        ReportLoadState(loading, error) {
            onRetryList()
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
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
    onEditEntry: (app.caiji.android.data.LedgerEntry) -> Unit,
    rankingSnapshot: PeriodRankingSnapshot? = null
) {
    val startInclusive = if (monthValue == 0) java.time.LocalDate.of(year, 1, 1) else YearMonth.of(year, monthValue).atDay(1)
    val endExclusive = if (monthValue == 0) java.time.LocalDate.of(year + 1, 1, 1) else YearMonth.of(year, monthValue).plusMonths(1).atDay(1)
    val visibleSnapshot = rankingSnapshot?.takeIf { it.startInclusive == startInclusive && it.endExclusive == endExclusive }
    val visibleReport = report?.takeIf { it.startInclusive == startInclusive && it.endExclusive == endExclusive }
    val summaryTotals = visibleSnapshot?.totals ?: visibleReport?.totals
    val rankingPager = rememberPagerState(initialPage = if (selectedType == EntryType.EXPENSE) 0 else 1, pageCount = { 2 })
    val rankingPagerScope = rememberCoroutineScope()
    LaunchedEffect(selectedType) {
        val page = if (selectedType == EntryType.EXPENSE) 0 else 1
        if (rankingPager.currentPage != page) rankingPager.animateScrollToPage(page)
    }
    LaunchedEffect(rankingPager.settledPage, selectedCategoryId) {
        if (selectedCategoryId == null) {
            val type = if (rankingPager.settledPage == 0) EntryType.EXPENSE else EntryType.INCOME
            if (type != selectedType) onSelectType(type)
        }
    }
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        CaijiPageTopBar(
            title = if (selectedCategoryId != null) "分类明细" else if (monthValue == 0) "${year}年报告" else "${year}年${monthValue}月报告",
            onBack = { if (selectedCategoryId != null) onSelectCategory(null) else onBack() },
            backTag = "period-report-back",
            actionTag = "period-report-header-action"
        )
        summaryTotals?.let {
            PeriodSummaryCard(
                "${year}年${if (monthValue == 0) "结余" else "${monthValue}月结余"}",
                it,
                incomeLabel = if (monthValue == 0) "${year}年收入" else "${monthValue}月收入",
                expenseLabel = if (monthValue == 0) "${year}年支出" else "${monthValue}月支出"
            )
        }
        if (selectedCategoryId == null) {
            PagerSegmentedTabs(
                state = rankingPager,
                labels = listOf("支出排行", "收入排行"),
                tags = listOf("report-type-EXPENSE", "report-type-INCOME"),
                containerTag = "report-type-tabs",
                onSelectPage = { page ->
                    if (page != rankingPager.currentPage) rankingPagerScope.launch { rankingPager.animateScrollToPage(page) }
                }
            )
            HorizontalPager(state = rankingPager, modifier = Modifier.weight(1f).fillMaxWidth().testTag("report-ranking-pager")) { pageIndex ->
                val pageType = if (pageIndex == 0) EntryType.EXPENSE else EntryType.INCOME
                val rankingCategories = visibleSnapshot?.categories?.filter { it.category.type == pageType }
                    ?: if (pageType == selectedType) visibleReport?.categories.orEmpty().filter { it.category.type == pageType }
                    else emptyList()
                val rankingDataReady = visibleSnapshot != null || (pageType == selectedType && visibleReport != null)
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Text("分类排行", Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    rankingCategories.forEach { total ->
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
                    if (rankingDataReady && !loading && error == null && rankingCategories.isEmpty()) {
                        Text("此期间暂无${if (pageType == EntryType.EXPENSE) "支出" else "收入"}",
                            Modifier.fillMaxWidth().padding(24.dp), color = CaijiPalette.SecondaryText)
                    }
                }
            }
        } else {
            val selectedCategory = visibleReport?.categories?.firstOrNull { it.category.id == selectedCategoryId }?.category
            Text(selectedCategory?.name.orEmpty(), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                visibleReport?.entries.orEmpty().forEach { entry ->
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
                if (!loading && error == null && visibleReport?.entries.isNullOrEmpty()) {
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
        val firstWidth = 48.dp
        val minimumNumericWidth = maxOf(74.dp, (maxNumberLength * 6.3f * fontScale + 12f).dp)
        val rowStartPadding = 6.dp
        val rowEndPadding = 2.dp
        val tableWidth = maxOf(maxWidth, firstWidth + rowStartPadding + rowEndPadding + minimumNumericWidth * 3)
        val numericWidth = (tableWidth - firstWidth - rowStartPadding - rowEndPadding) / 3
        val tableScroll = rememberScrollState()
        Column(Modifier.horizontalScroll(tableScroll, enabled = tableScroll.maxValue > 0).testTag("bill-table-scroll")) {
            Column(Modifier.width(tableWidth)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = rowStartPadding, end = rowEndPadding, top = 10.dp, bottom = 10.dp),
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
                            .padding(start = rowStartPadding, end = rowEndPadding, top = 12.dp, bottom = 12.dp),
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
    Box(Modifier.width(width), contentAlignment = Alignment.CenterEnd) {
        Text(value, color = color,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            maxLines = 1, softWrap = false)
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
