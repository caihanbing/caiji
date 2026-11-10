package app.caiji.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
            title = "汇总", onBack = onBack, backTag = "bills-back", actionTag = "bills-header-action",
            backStartPadding = 4.dp,
            action = if (mode == BillListMode.MONTH) ({
                Row(Modifier.padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CompactPeriodArrowButton("上一年", "bill-previous-year", isNext = false,
                        enabled = year > FIRST_BILL_YEAR, onClick = onPreviousYear)
                    Text("${year}年", Modifier.padding(horizontal = 2.dp).testTag("bill-selected-year"),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    CompactPeriodArrowButton("下一年", "bill-next-year", isNext = true,
                        enabled = year < Year.now().value, onClick = onNextYear)
                }
            }) else null
        )
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
                            PeriodAmountTableRow("${item.month.monthValue}月", billAmounts(item.totals), "month-row-${item.month.monthValue}") {
                                onOpenMonth(item.month)
                            }
                        }
                        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                            PeriodAmountTable("月份", listOf("收入", "支出", "结余"), rows,
                                "bill-table-scroll", listOf("bill-table-header-income", "bill-table-header-expense", "bill-table-header-balance"))
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
                    PeriodAmountTableRow("${yearValue}年", billAmounts(totals), "year-row-$yearValue") { onOpenYear(yearValue) }
                    }
                    Column(Modifier.fillMaxSize()) {
                        PeriodSummaryCard("累计结余", allTime, incomeLabel = "累计收入", expenseLabel = "累计支出")
                        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                            PeriodAmountTable("年份", listOf("收入", "支出", "结余"), tableRows,
                                "bill-table-scroll", listOf("bill-table-header-income", "bill-table-header-expense", "bill-table-header-balance"))
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
            actionTag = "period-report-header-action",
            backStartPadding = 4.dp
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
                        FinancialRankingRow(
                            icon = categoryEmoji(total.category.iconKey), label = total.category.name,
                            amountFen = total.amountFen, amountSign = if (pageType == EntryType.INCOME) "+" else "−",
                            amountColor = if (pageType == EntryType.INCOME) CaijiPalette.Income else CaijiPalette.Ink,
                            tag = "report-category-${total.category.id}"
                        ) { onSelectCategory(total.category.id) }
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
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                visibleReport?.entries.orEmpty().forEach { entry ->
                    val category = selectedCategory
                    FinancialDetailRow(
                        icon = categoryEmoji(category?.iconKey ?: "other"), title = category?.name ?: "已停用分类",
                        subtitle = "${entry.occurredOn.monthValue}月${entry.occurredOn.dayOfMonth}日${entry.note?.let { " · $it" }.orEmpty()}",
                        amount = "${if (entry.type == EntryType.EXPENSE) "−" else "+"}¥${formatYuan(entry.amountFen)}",
                        amountColor = if (entry.type == EntryType.INCOME) CaijiPalette.Income else CaijiPalette.Ink,
                        tag = "report-entry-${entry.id}", iconTag = "report-entry-icon-${entry.id}"
                    ) { onEditEntry(entry) }
                }
                if (!loading && error == null && visibleReport?.entries.isNullOrEmpty()) {
                    Text("该分类暂无账单", Modifier.fillMaxWidth().padding(24.dp), color = CaijiPalette.SecondaryText)
                }
            }
        }
        ReportLoadState(loading, error, onRetry)
    }
}

private fun billAmounts(totals: LedgerPeriodTotals): List<String> = listOf(
    "¥${formatYuan(totals.incomeFen)}",
    "¥${formatYuan(totals.expenseFen)}",
    "${if (totals.balanceFen.signum() < 0) "−" else ""}¥${formatYuan(totals.balanceFen.abs())}"
)

@Composable
private fun ReportLoadState(loading: Boolean, error: String?, onRetry: () -> Unit) {
    if (loading) CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp).testTag("report-loading"), strokeWidth = 2.dp)
    if (error != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(error, Modifier.weight(1f), color = CaijiPalette.Danger, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        TextButton(onClick = onRetry, modifier = Modifier.testTag("report-retry")) { Text("重试") }
    }
}
