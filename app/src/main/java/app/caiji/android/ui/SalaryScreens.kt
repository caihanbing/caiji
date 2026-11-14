package app.caiji.android.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.caiji.android.data.*
import app.caiji.android.domain.SalaryAmountInput
import app.caiji.android.ui.theme.CaijiPalette
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.*
import kotlin.math.roundToInt

private enum class SR { HOME, SUMMARY, YEAR_REPORT, INCOME_DETAILS, EDIT, TYPES, IMPORT, EXPORT }
internal enum class SalaryManagementRoute { HOME, IMPORT, EXPORT, TYPES }

internal data class SalaryDraft(val date:LocalDate,val typeId:String,val gross:String,val union:String,val pension:String,val medical:String,val unemployment:String,val housing:String,val annuity:String,val tax:String) : java.io.Serializable {
 companion object {
  fun blank(typeId:String)=SalaryDraft(LocalDate.now(),typeId,"","","","","","","","")
  fun from(e:SalaryEntry)=SalaryDraft(e.occurredOn,e.incomeTypeId,inFen(e.grossFen),inFen(e.unionFen),inFen(e.pensionFen),inFen(e.medicalFen),inFen(e.unemploymentFen),inFen(e.housingFen),inFen(e.annuityFen),inFen(e.taxFen))
 }
}

private fun SalaryDraft.amountValues() = listOf(gross, union, pension, medical, unemployment, housing, annuity, tax)
internal fun needsSalaryDiscardConfirmation(draft: SalaryDraft?, original: SalaryEntry?): Boolean {
 if (draft == null) return false
 val amounts = draft.amountValues()
 return if (original == null) amounts.any(String::isNotEmpty)
 else amounts != SalaryDraft.from(original).amountValues()
}

@Composable
internal fun SalaryRootScreen(
 repo:RoomSalaryRepository,preview:SalaryImportPreview?,onImport:()->Unit,onClear:()->Unit,onExport:(SalaryPeriod)->Unit,
 initialDraft:SalaryDraft?=null,initialEntry:SalaryEntry?=null,onDraftSnapshotChange:(SalaryDraft?,SalaryEntry?)->Unit={_,_->},
 onAsyncError:(String)->Unit={},onBottomNavigationVisibility:(Boolean)->Unit={},
 initialManagementRoute:SalaryManagementRoute=SalaryManagementRoute.HOME,onManagementBack:()->Unit={},
 onManagementSuccess:(String)->Unit={},homeYear:Int?=null,onHomeYearChange:(Int)->Unit={},
 initialCashNature:Boolean?=null,onCashNatureChange:(Boolean?)->Unit={},
 initialKeyboardExpanded:Boolean?=null,onKeyboardExpandedChange:(Boolean)->Unit={}
){
 val scope = rememberCoroutineScope()
 val settingsOrigin = initialManagementRoute != SalaryManagementRoute.HOME
 var page by rememberSaveable {
  mutableStateOf(
   if (settingsOrigin) when (initialManagementRoute) {
    SalaryManagementRoute.IMPORT -> SR.IMPORT
    SalaryManagementRoute.EXPORT -> SR.EXPORT
    SalaryManagementRoute.TYPES -> SR.TYPES
    SalaryManagementRoute.HOME -> SR.HOME
   } else if (initialDraft == null) SR.HOME else SR.EDIT
  )
 }
 var typesOrigin by rememberSaveable { mutableStateOf(SR.HOME) }
 var editOrigin by rememberSaveable { mutableStateOf(SR.HOME) }
 var records by remember { mutableStateOf(emptyList<SalaryEntry>()) }
 var duplicates by remember { mutableIntStateOf(0) }
 var entriesToInsert by remember { mutableStateOf(emptyList<SalaryEntry>()) }
 var types by remember { mutableStateOf(emptyList<SalaryIncomeType>()) }
 var selected by remember { mutableStateOf(initialEntry) }
 var draft by remember { mutableStateOf(initialDraft) }
 var keypadExpanded by rememberSaveable { mutableStateOf(initialKeyboardExpanded ?: (initialEntry != null)) }
 var salaryActiveField by rememberSaveable { mutableStateOf("收入") }
 var discardPrompt by rememberSaveable { mutableStateOf(false) }
 var cashNature by rememberSaveable { mutableStateOf(initialCashNature) }
 fun updateKeypadExpanded(expanded: Boolean) {
  keypadExpanded = expanded
  onKeyboardExpandedChange(expanded)
 }
 fun exitEditor() {
  discardPrompt = false
  onDraftSnapshotChange(null, null); selected = null; draft = null
  updateKeypadExpanded(false); cashNature = null; onCashNatureChange(null); page = editOrigin
 }
 fun requestExitEditor() {
  if (needsSalaryDiscardConfirmation(draft, selected)) discardPrompt = true else exitEditor()
 }
 var message by remember { mutableStateOf<String?>(null) }
 var typeSelectionNotice by remember { mutableStateOf<String?>(null) }
 var localHomeYear by rememberSaveable { mutableIntStateOf(homeYear ?: LocalDate.now().year) }
 val selectedHomeYear = homeYear ?: localHomeYear
 val changeHomeYear:(Int)->Unit={updated->if(homeYear==null)localHomeYear=updated;onHomeYearChange(updated)}
 var reportYear by rememberSaveable { mutableIntStateOf(selectedHomeYear) }
 var selectedIncomeTypeId by rememberSaveable { mutableStateOf<String?>(null) }
 var chooseFirstActiveTypeWhenLoaded by rememberSaveable { mutableStateOf(false) }
 var homeSwipeId by rememberSaveable { mutableStateOf<String?>(null) }
 var detailSwipeId by rememberSaveable { mutableStateOf<String?>(null) }
 val homeListState = rememberLazyListState()
 val detailListState = rememberLazyListState()
 val annualTableVerticalState = rememberScrollState()
 val annualTableHorizontalState = rememberScrollState()
 val yearReportRankingState = rememberScrollState()
 var lastScrollReportYear by rememberSaveable { mutableIntStateOf(reportYear) }
 var lastDetailReportYear by rememberSaveable { mutableIntStateOf(reportYear) }
 var lastDetailIncomeTypeId by rememberSaveable { mutableStateOf(selectedIncomeTypeId) }
 LaunchedEffect(reportYear) {
  if (lastScrollReportYear != reportYear) {
   yearReportRankingState.scrollTo(0)
   lastScrollReportYear = reportYear
  }
 }
 LaunchedEffect(reportYear, selectedIncomeTypeId) {
  if (lastDetailReportYear != reportYear || lastDetailIncomeTypeId != selectedIncomeTypeId) {
   detailListState.scrollToItem(0)
   lastDetailReportYear = reportYear
   lastDetailIncomeTypeId = selectedIncomeTypeId
  }
 }
 val updateDraft:(SalaryDraft)->Unit={updated->draft=updated;if(updated.typeId.isNotEmpty())typeSelectionNotice=null;onDraftSnapshotChange(updated,selected)}
 suspend fun refresh(){withContext(Dispatchers.IO){types=repo.incomeTypes();records=repo.entries()}}
 val deleteFromList:(String,(String?)->Unit)->Unit={id,done->scope.launch{runCatching{withContext(Dispatchers.IO){repo.delete(id)};refresh()}.onSuccess{message="工资记录已删除";done(null)}.onFailure{val text="工资删除失败，记录仍保留，请重试。";message=text;onAsyncError(text);done(text)}}}
 LaunchedEffect(repo){runCatching{refresh()}.onFailure{val text="工资数据暂时无法读取，请重新进入工资页面；若仍失败，请在设置中提交问题反馈。";message=text;onAsyncError(text)}}
 LaunchedEffect(page,settingsOrigin){onBottomNavigationVisibility(!settingsOrigin&&(page==SR.HOME||page==SR.IMPORT))}
 LaunchedEffect(page, types, chooseFirstActiveTypeWhenLoaded) {
  if (page == SR.EDIT && selected == null && chooseFirstActiveTypeWhenLoaded && types.isNotEmpty()) {
   val firstActive = types.firstOrNull { it.active }
   if (firstActive != null) draft?.let { updateDraft(it.copy(typeId = firstActive.id)) }
   chooseFirstActiveTypeWhenLoaded = false
  }
 }
 LaunchedEffect(page, selected?.id, types, draft?.typeId) {
  val currentDraft = draft
  if (page == SR.EDIT && selected == null && currentDraft != null && types.isNotEmpty() && currentDraft.typeId.isNotEmpty() &&
      types.none { it.id == currentDraft.typeId && it.active }) {
   updateDraft(currentDraft.copy(typeId = ""))
   typeSelectionNotice = "所选收入项已停用，请重新选择启用的收入项。"
  }
 }
 LaunchedEffect(preview){if(preview!=null){page=SR.IMPORT;val delta=withContext(Dispatchers.IO){SalaryReports.importDelta(preview.entries,repo.entries())};duplicates=delta.skippedDuplicateCount;entriesToInsert=delta.entriesToInsert}else{duplicates=0;entriesToInsert=emptyList()}}
 BackHandler(page != SR.HOME) {
  when (page) {
   SR.SUMMARY -> page = SR.HOME
   SR.YEAR_REPORT -> page = SR.SUMMARY
   SR.INCOME_DETAILS -> page = SR.YEAR_REPORT
   SR.TYPES -> when { typesOrigin == SR.EDIT -> page = SR.EDIT; settingsOrigin -> onManagementBack(); else -> page = typesOrigin }
   SR.IMPORT, SR.EXPORT -> if (settingsOrigin) { if (page == SR.IMPORT) onClear(); onManagementBack() } else { if (page == SR.IMPORT) onClear(); page = SR.HOME }
   SR.EDIT -> requestExitEditor()
   SR.HOME -> Unit
  }
 }
 when(page){
 SR.HOME->SalaryHome(
  records = records, types = types, year = selectedHomeYear, summary = false, message = message,
  dismiss = { message = null }, previous = { changeHomeYear((selectedHomeYear - 1).coerceAtLeast(1900)); homeSwipeId = null },
  next = { changeHomeYear((selectedHomeYear + 1).coerceAtMost(LocalDate.now().year)); homeSwipeId = null }, setYear = { changeHomeYear(it); homeSwipeId = null },
  openSummary = { page = SR.SUMMARY }, backToYear = {}, listState = homeListState,
  openSwipeEntryId = homeSwipeId, setSwipeEntryId = { homeSwipeId = it },
  delete = deleteFromList,
  add = { selected = null; editOrigin = SR.HOME; cashNature = true; salaryActiveField = "收入"; onCashNatureChange(true); homeSwipeId = null; typeSelectionNotice = null
   chooseFirstActiveTypeWhenLoaded = false
   val nextDraft = SalaryDraft.blank(""); draft = nextDraft
   onDraftSnapshotChange(nextDraft, null); updateKeypadExpanded(false); page = SR.EDIT },
  edit = { entry -> selected = entry; editOrigin = SR.HOME; cashNature = entry.isCash; salaryActiveField = "收入"; onCashNatureChange(entry.isCash); homeSwipeId = null; typeSelectionNotice = null; chooseFirstActiveTypeWhenLoaded = false
   val nextDraft = SalaryDraft.from(entry); draft = nextDraft; updateKeypadExpanded(true)
   onDraftSnapshotChange(nextDraft, entry); page = SR.EDIT }
 )
 SR.SUMMARY->SalaryAnnualSummaryScreen(
  records = records, tableVerticalState = annualTableVerticalState, tableHorizontalState = annualTableHorizontalState,
  onBack = { page = SR.HOME },
  onOpenYear = { selectedYear -> reportYear = selectedYear; page = SR.YEAR_REPORT }
 )
 SR.YEAR_REPORT->SalaryYearReportScreen(
  year = reportYear, records = SalaryReports.filter(records, SalaryPeriod.Year(reportYear)), types = types,
  listState = yearReportRankingState,
  onBack = { page = SR.SUMMARY },
  onOpenIncomeType = { id -> selectedIncomeTypeId = id; detailSwipeId = null; page = SR.INCOME_DETAILS }
 )
 SR.INCOME_DETAILS -> {
  val yearEntries = SalaryReports.filter(records, SalaryPeriod.Year(reportYear))
  val visibleEntries = yearEntries.filter { it.incomeTypeId == selectedIncomeTypeId }
  val incomeTypeName = types.firstOrNull { it.id == selectedIncomeTypeId }?.name ?: selectedIncomeTypeId.orEmpty()
  SalaryHome(
   records = visibleEntries, types = types, year = reportYear, summary = true, message = message,
   dismiss = { message = null }, previous = {}, next = {}, setYear = {}, openSummary = {},
   backToYear = { page = SR.YEAR_REPORT }, listState = detailListState,
   openSwipeEntryId = detailSwipeId, setSwipeEntryId = { detailSwipeId = it }, delete = deleteFromList, add = {},
   edit = { entry ->
    selected = entry; editOrigin = SR.INCOME_DETAILS; cashNature = entry.isCash; salaryActiveField = "收入"; onCashNatureChange(entry.isCash)
    detailSwipeId = null; typeSelectionNotice = null; chooseFirstActiveTypeWhenLoaded = false
    val nextDraft = SalaryDraft.from(entry); draft = nextDraft; updateKeypadExpanded(true)
    onDraftSnapshotChange(nextDraft, entry); page = SR.EDIT
   },
   summaryHeaderTitle = "收入项明细", summaryYear = reportYear,
   summaryTotals = SalaryReports.summarize(yearEntries), summarySubtitle = incomeTypeName,
   detailEmptyState = true, summaryHeaderBackStartPadding = 4.dp
  )
 }
 SR.EDIT->SalaryEditor(
  entry = selected, types = types, draft = draft ?: SalaryDraft.blank(""), initialCash = cashNature ?: selected?.isCash,
  onCashChange = { next -> cashNature = next; onCashNatureChange(next) },
  activeField = salaryActiveField, onActiveFieldChange = { salaryActiveField = it },
  change = updateDraft, back = ::requestExitEditor,
  save = { e, done -> scope.launch {
   runCatching { withContext(Dispatchers.IO) { repo.save(e) }; refresh() }
    .onSuccess { message = "工资记录已保存"; selected = null; draft = null; updateKeypadExpanded(false); cashNature = null
     onCashNatureChange(null); onDraftSnapshotChange(null, null); page = editOrigin; done(true) }
    .onFailure { val text = "工资保存失败，输入内容已保留，请重试；若仍失败，请在设置中提交问题反馈。"
     message = text; onAsyncError(text); done(false) }
  } },
  delete = { id -> scope.launch {
   runCatching { withContext(Dispatchers.IO) { repo.delete(id) }; refresh() }
    .onSuccess { message = "工资记录已删除"; selected = null; draft = null; updateKeypadExpanded(false); cashNature = null
     onCashNatureChange(null); onDraftSnapshotChange(null, null); page = editOrigin }
    .onFailure { val text = "工资删除失败，请重试；记录仍保留。若仍失败，请在设置中提交问题反馈。"
     message = text; onAsyncError(text) }
  } },
  typeSelectionNotice = typeSelectionNotice, initialKeyboardVisible = keypadExpanded,
  onKeyboardVisibleChange = ::updateKeypadExpanded,
  onManageIncomeTypes = { typesOrigin = SR.EDIT; page = SR.TYPES }
 )
 SR.TYPES->SalaryTypesScreen(
  types = types,
  back = { if (settingsOrigin) onManagementBack() else page = typesOrigin },
  setActive = { id, active, done ->
   scope.launch { runCatching { withContext(Dispatchers.IO) { repo.setIncomeTypeActive(id, active) }; refresh() }
    .onSuccess { message = if (active) "收入项已启用" else "收入项已停用"; done(true, null) }
    .onFailure { done(false, it.message ?: "收入项状态未能保存，请重试。") }
   }
  },
  move = { id, direction, done ->
   scope.launch { runCatching { withContext(Dispatchers.IO) { repo.moveIncomeType(id, direction) }; refresh() }
    .onSuccess { done(true, null) }
    .onFailure { done(false, it.message ?: "收入项顺序未能保存，请重试。") }
   }
  },
  create = { name, cash, icon, done ->
   scope.launch { runCatching { withContext(Dispatchers.IO) { repo.createIncomeType(name, cash, icon) }; refresh() }
    .onSuccess { message = "收入项已添加"; done(true, null) }
    .onFailure { done(false, it.message ?: "收入项未能添加，请重试。") }
   }
  },
  update = { id, name, icon, done ->
   scope.launch { runCatching { withContext(Dispatchers.IO) { repo.updateIncomeType(id, name, icon) }; refresh() }
    .onSuccess { message = "收入项已更新"; done(true, null) }
    .onFailure { done(false, it.message ?: "收入项未能保存，请重试。") }
   }
  }
 )
 SR.IMPORT->SalaryImportScreen(preview,duplicates,entriesToInsert,(SalaryIncomeTypes.defaults+types+preview?.incomeTypesToCreate.orEmpty()+preview?.incomeTypesToRestore.orEmpty()).associate{it.id to it.name},{if(settingsOrigin)onManagementBack() else page=SR.HOME;onClear()},{onClear();onImport()}){val p=preview?:return@SalaryImportScreen;if(p.errors.isNotEmpty())return@SalaryImportScreen;scope.launch{runCatching{withContext(Dispatchers.IO){repo.importNew(p.entries,p.incomeTypesToCreate,p.incomeTypesToRestore)}.also{refresh()}}.onSuccess{val success="导入完成：新增 "+it.insertedCount+" 笔，跳过重复 "+it.skippedDuplicateCount+" 笔";message=success;onClear();if(settingsOrigin){onManagementSuccess(success);onManagementBack()}else page=SR.HOME}.onFailure{val text="工资导入未完成，已有工资记录仍保留。请重试；若仍失败，请在设置中提交问题反馈。";message=text;onAsyncError(text)}}}
 SR.EXPORT->SalaryExportScreen({if(settingsOrigin)onManagementBack() else page=SR.HOME},{period->onExport(period)})
 }
 if (discardPrompt) AlertDialog(
  onDismissRequest = { discardPrompt = false }, modifier = Modifier.testTag("salary-discard-dialog"),
  title = { Text("放弃本次收入？") }, text = { Text("已输入的金额尚未保存。") },
  confirmButton = { TextButton(onClick = ::exitEditor) { Text("放弃", color = CaijiPalette.Danger) } },
  dismissButton = { TextButton(onClick = { discardPrompt = false }) { Text("继续编辑") } }
 )
}

@Composable
private fun SalaryHome(
    records: List<SalaryEntry>,
    types: List<SalaryIncomeType>,
    year: Int,
    summary: Boolean,
    message: String?,
    dismiss: () -> Unit,
    previous: () -> Unit,
    next: () -> Unit,
    setYear: (Int) -> Unit,
    openSummary: () -> Unit,
    backToYear: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    openSwipeEntryId: String?,
    setSwipeEntryId: (String?) -> Unit,
    delete: (String, (String?) -> Unit) -> Unit,
    add: () -> Unit,
    edit: (SalaryEntry) -> Unit,
    summaryHeaderTitle: String = "汇总",
    summaryYear: Int? = null,
    summaryTotals: SalaryTotals? = null,
    summarySubtitle: String? = null,
    detailEmptyState: Boolean = false,
    summaryHeaderBackStartPadding: Dp = 0.dp
) {
    var yearMenu by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteMessage by remember { mutableStateOf<String?>(null) }
    val currentYear = LocalDate.now().year
    val periodRows = if (summary) records else SalaryReports.filter(records, SalaryPeriod.Year(year))
    val sums = summaryTotals ?: SalaryReports.summarize(periodRows)
    val typesById = types.associateBy { it.id }
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        if (summary) {
            SalaryHeader(summaryHeaderTitle, backToYear, backStartPadding = summaryHeaderBackStartPadding)
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = openSummary, modifier = Modifier.testTag("salary-open-summary")) {
                    CaijiIcon(CaijiIconName.DATE, "汇总", Modifier.size(18.dp))
                    Text("汇总", Modifier.padding(start = 5.dp), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    PeriodArrowButton("上一年", "salary-year-previous", isNext = false,
                        enabled = year > 1900, onClick = previous)
                    Box {
                        TextButton(onClick = { yearMenu = true }, modifier = Modifier.testTag("salary-year-label"),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
                            Text("${year}年", color = CaijiPalette.Ink, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false },
                            modifier = Modifier.background(CaijiPalette.Surface).testTag("salary-year-options")) {
                            (currentYear downTo 1900).forEach { option ->
                                DropdownMenuItem(text = { Text("$option 年") }, onClick = { yearMenu = false; setYear(option) },
                                    modifier = Modifier.testTag("salary-year-option-$option"))
                            }
                        }
                    }
                    PeriodArrowButton("下一年", "salary-year-next", isNext = true,
                        enabled = year < currentYear, onClick = next)
                }
            }
        }
        FinancialSummaryCard(
            primaryLabel = when { summaryYear != null -> "${summaryYear}年纯收入"; summary -> "累计纯收入"; else -> "本年纯收入" },
            primaryFen = sums.netFen,
            leftLabel = when { summaryYear != null -> "${summaryYear}年收入"; summary -> "累计收入"; else -> "本年收入" },
            leftFen = sums.grossFen,
            leftColor = CaijiPalette.Income,
            rightLabel = when { summaryYear != null -> "${summaryYear}年扣款"; summary -> "累计扣款"; else -> "本年扣款" },
            rightFen = sums.deductionFen,
            primaryTag = "salary-net-total",
            leftTag = "salary-gross-total"
        )
        if (message != null) TextButton(onClick = dismiss) { Text(message, color = CaijiPalette.SecondaryText) }
        summarySubtitle?.let {
            Text(it, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("salary-income-detail-title"),
                color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        deleteMessage?.let {
            Text(it, Modifier.padding(horizontal = 18.dp, vertical = 2.dp).testTag("salary-list-delete-error"),
                color = CaijiPalette.Danger, style = MaterialTheme.typography.bodySmall)
        }
        if (periodRows.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(if (detailEmptyState) "该收入项暂无工资记录" else "还没有工资记录", style = MaterialTheme.typography.titleLarge)
            Text(if (detailEmptyState) "返回年度报告查看其他收入项。" else "记录工资、补贴和福利，或从 Excel 导入。", color = CaijiPalette.SecondaryText,
                style = MaterialTheme.typography.bodySmall)
        } else LazyColumn(Modifier.weight(1f).testTag("salary-record-list"), state = listState) {
            if (summarySubtitle != null) {
                items(periodRows.sortedWith(compareByDescending<SalaryEntry> { it.occurredOn }
                    .thenByDescending { it.updatedAtMillis }), key = { it.id }) { entry ->
                    SalaryEntryRow(entry, typesById[entry.incomeTypeId]?.name ?: entry.incomeTypeId,
                        typesById[entry.incomeTypeId]?.iconKey ?: "other", summary = false, incomeTypeDetail = true,
                        deleteRevealed = openSwipeEntryId == entry.id,
                        onSwipeChanged = { setSwipeEntryId(if (it) entry.id else null) },
                        onClick = { if (openSwipeEntryId == entry.id) setSwipeEntryId(null) else edit(entry) },
                        onDelete = { setSwipeEntryId(null); deleteCandidate = entry.id })
                }
            } else {
                val grouped = periodRows.groupBy { YearMonth.from(it.occurredOn) }.toSortedMap(reverseOrder())
                grouped.forEach { (month, entries) ->
                    val cashFen = entries.asSequence().filter { it.isCash }
                        .fold(BigInteger.ZERO) { total, entry -> total + BigInteger.valueOf(entry.netIncomeFen) }
                    val nonCashFen = entries.asSequence().filterNot { it.isCash }
                        .fold(BigInteger.ZERO) { total, entry -> total + BigInteger.valueOf(entry.netIncomeFen) }
                    val monthKey = "${month.year}-${month.monthValue.toString().padStart(2, '0')}"
                    item(key = "salary-month-${month.year}-${month.monthValue}") {
                        FinancialGroupHeader(
                            title = "${month.year}年${month.monthValue}月",
                            headerTag = "salary-month-group-$monthKey",
                            titleTag = "salary-month-title-${month.year}-${month.monthValue}",
                            summaryTag = "salary-month-summary-$monthKey"
                        ) {
                            Text("现金 ¥${formatYuan(cashFen)}", color = CaijiPalette.SecondaryText,
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
                                modifier = Modifier.testTag("salary-group-cash-total-$monthKey"))
                            Text("非现金 ¥${formatYuan(nonCashFen)}", color = CaijiPalette.SecondaryText,
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, softWrap = false,
                                modifier = Modifier.testTag("salary-group-noncash-total-$monthKey"))
                        }
                    }
                    items(entries.sortedWith(compareByDescending<SalaryEntry> { it.occurredOn }
                        .thenByDescending { it.updatedAtMillis }), key = { it.id }) { entry ->
                        SalaryEntryRow(entry, typesById[entry.incomeTypeId]?.name ?: entry.incomeTypeId,
                            typesById[entry.incomeTypeId]?.iconKey ?: "other", summary,
                            deleteRevealed = openSwipeEntryId == entry.id,
                            onSwipeChanged = { setSwipeEntryId(if (it) entry.id else null) },
                            onClick = { if (openSwipeEntryId == entry.id) setSwipeEntryId(null) else edit(entry) },
                            onDelete = { setSwipeEntryId(null); deleteCandidate = entry.id })
                    }
                    item(key = "salary-divider-${month.year}-${month.monthValue}") {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 3.dp).testTag("salary-month-divider-$monthKey"),
                            color = CaijiPalette.Divider,
                            thickness = 1.dp
                        )
                    }
                }
            }
        }
        if (!summary) {
            Button(add, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CaijiPalette.Primary, contentColor = CaijiPalette.OnPrimary),
                shape = RoundedCornerShape(12.dp)) { Text("＋  新增收入") }
            Text("工资独立统计，不计入账单收支。", Modifier.padding(horizontal = 18.dp, vertical = 3.dp),
                color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
    }
    deleteCandidate?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteCandidate = null },
            modifier = Modifier.testTag("salary-list-delete-dialog"),
            title = { Text("删除这笔收入？") },
            text = { Text("删除后会立即从工资汇总中移除。") },
            confirmButton = {
                TextButton(onClick = {
                    if (!deleting) {
                        deleting = true
                        delete(id) { error ->
                            deleting = false
                            deleteCandidate = null
                            if (error != null) deleteMessage = error
                        }
                    }
                }, enabled = !deleting, modifier = Modifier.testTag("salary-list-confirm-delete")) {
                    Text(if (deleting) "删除中" else "删除", color = CaijiPalette.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }, enabled = !deleting) { Text("取消") } }
        )
    }
}

@Composable
private fun SalaryAnnualSummaryScreen(
    records: List<SalaryEntry>,
    tableVerticalState: androidx.compose.foundation.ScrollState,
    tableHorizontalState: androidx.compose.foundation.ScrollState,
    onBack: () -> Unit,
    onOpenYear: (Int) -> Unit
) {
    val currentYear = LocalDate.now().year
    val allTotals = SalaryReports.summarize(records)
    val totalsByYear = records.groupBy { it.occurredOn.year }
        .mapValues { (_, entries) -> SalaryReports.summarize(entries) }
    val firstYear = totalsByYear.keys.minOrNull() ?: currentYear
    val rows = (firstYear..currentYear).toList().asReversed().map { year ->
        val totals = totalsByYear[year] ?: SalaryTotals()
        PeriodAmountTableRow(
            label = "${year}年",
            amounts = listOf(yuanAmount(totals.grossFen), yuanAmount(totals.deductionFen), yuanAmount(totals.netFen)),
            tag = "salary-year-row-$year",
            onClick = { onOpenYear(year) }
        )
    }
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        SalaryHeader("汇总", onBack, backStartPadding = 4.dp)
        FinancialSummaryCard(
            primaryLabel = "累计纯收入", primaryFen = allTotals.netFen,
            leftLabel = "累计收入", leftFen = allTotals.grossFen, leftColor = CaijiPalette.Income,
            rightLabel = "累计扣款", rightFen = allTotals.deductionFen,
            primaryTag = "salary-net-total", leftTag = "salary-gross-total"
        )
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(tableVerticalState).padding(horizontal = 16.dp)) {
            PeriodAmountTable(
                heading = "年份", amountHeadings = listOf("收入", "扣款", "纯收入"), rows = rows,
                scrollTag = "salary-year-table-scroll",
                headerTags = listOf("salary-year-table-header-income", "salary-year-table-header-deduction", "salary-year-table-header-net"),
                horizontalScrollState = tableHorizontalState
            )
        }
    }
}

@Composable
private fun SalaryYearReportScreen(
    year: Int,
    records: List<SalaryEntry>,
    types: List<SalaryIncomeType>,
    listState: androidx.compose.foundation.ScrollState,
    onBack: () -> Unit,
    onOpenIncomeType: (String) -> Unit
) {
    val totals = SalaryReports.summarize(records)
    val ranking = SalaryReports.incomeTypeTotals(records, types)
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        SalaryHeader("${year}年报告", onBack, backStartPadding = 4.dp)
        FinancialSummaryCard(
            primaryLabel = "${year}年纯收入", primaryFen = totals.netFen,
            leftLabel = "${year}年收入", leftFen = totals.grossFen, leftColor = CaijiPalette.Income,
            rightLabel = "${year}年扣款", rightFen = totals.deductionFen,
            primaryTag = "salary-report-net-total", leftTag = "salary-report-gross-total"
        )
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(listState).padding(horizontal = 16.dp)
            .testTag("salary-year-report-ranking-list")) {
            Text("收入项排行", Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                color = CaijiPalette.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (ranking.isEmpty()) {
                Text("该年暂无工资记录", Modifier.fillMaxWidth().padding(24.dp).testTag("salary-year-report-empty"),
                    color = CaijiPalette.SecondaryText)
            } else ranking.forEach { item ->
                FinancialRankingRow(
                    icon = categoryEmoji(item.incomeType.iconKey), label = item.incomeType.name,
                    amountFen = item.netFen, amountSign = "+", amountColor = CaijiPalette.Income,
                    tag = "salary-income-ranking-${item.incomeType.id}"
                ) { onOpenIncomeType(item.incomeType.id) }
            }
        }
    }
}

private fun yuanAmount(value: BigInteger): String =
    if (value.signum() < 0) "−¥${formatYuan(value.abs())}" else "¥${formatYuan(value)}"

@Composable
private fun SalaryEntryRow(
    entry: SalaryEntry,
    name: String,
    iconKey: String,
    summary: Boolean,
    incomeTypeDetail: Boolean = false,
    deleteRevealed: Boolean,
    onSwipeChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val actionWidth = 100.dp
    val actionWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { actionWidth.toPx() }
    var offsetX by remember(entry.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(deleteRevealed) { offsetX = if (deleteRevealed) -actionWidthPx else 0f }
    Box(Modifier.fillMaxWidth().clipToBounds().testTag("salary-entry-container-${entry.id}")) {
        Surface(color = CaijiPalette.DangerSoft, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.CenterEnd).size(width = actionWidth, height = 48.dp)
                .clickable(onClick = onDelete).testTag("salary-delete-${entry.id}")) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                CaijiIcon(CaijiIconName.DELETE, "删除收入", Modifier.size(18.dp))
                Text("删除", Modifier.padding(start = 4.dp), color = CaijiPalette.Danger,
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(Modifier.fillMaxWidth().offset { IntOffset(offsetX.roundToInt(), 0) }
            .pointerInput(entry.id, deleteRevealed) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceIn(-actionWidthPx, 0f)
                    },
                    onDragEnd = {
                        val reveal = offsetX < -actionWidthPx * .4f
                        offsetX = if (reveal) -actionWidthPx else 0f
                        onSwipeChanged(reveal)
                    },
                    onDragCancel = {
                        offsetX = if (deleteRevealed) -actionWidthPx else 0f
                        onSwipeChanged(deleteRevealed)
                    }
                )
            }
            .clickable(onClick = onClick)
            .background(CaijiPalette.Background)
            .testTag("salary-entry-row-${entry.id}")
            .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = CaijiPalette.SurfaceAlt, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(categoryEmoji(iconKey), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("salary-entry-icon-${entry.id}"))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name, color = CaijiPalette.Ink, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val date = if (summary && !incomeTypeDetail) entry.occurredOn.format(DateTimeFormatter.ofPattern("yyyy/M/d", Locale.CHINA))
                    else entry.occurredOn.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
                Text(date + " · " + if (entry.isCash) "现金" else "非现金", color = CaijiPalette.SecondaryText,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("salary-entry-date-${entry.id}"))
            }
            Box(Modifier.widthIn(max = 140.dp).testTag("salary-entry-amount-${entry.id}"), contentAlignment = Alignment.CenterEnd) {
                Text("+¥" + formatYuan(entry.netIncomeFen), color = CaijiPalette.Income,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SalaryEditor(
    entry: SalaryEntry?, types: List<SalaryIncomeType>, draft: SalaryDraft, initialCash: Boolean?,
    onCashChange: (Boolean) -> Unit,
    activeField: String, onActiveFieldChange: (String) -> Unit,
    change: (SalaryDraft) -> Unit, back: () -> Unit,
    save: (SalaryEntry, (Boolean) -> Unit) -> Unit, delete: (String) -> Unit,
    typeSelectionNotice: String?, initialKeyboardVisible: Boolean, onKeyboardVisibleChange: (Boolean) -> Unit,
    onManageIncomeTypes: () -> Unit
) {
    val today = LocalDate.now()
    val selectedType = types.firstOrNull { it.id == draft.typeId }
    var cash by rememberSaveable(entry?.id) { mutableStateOf(initialCash ?: entry?.isCash ?: types.firstOrNull { it.id == draft.typeId }?.isCash ?: true) }
    var keyboardVisible by rememberSaveable(entry?.id) { mutableStateOf(initialKeyboardVisible) }
    fun setKeyboardVisible(visible: Boolean) {
        keyboardVisible = visible
        onKeyboardVisibleChange(visible)
    }
    var error by remember(entry?.id) { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val deductionFields = listOf("工会费" to draft.union, "养老保险" to draft.pension,
        "医疗保险" to draft.medical, "失业保险" to draft.unemployment,
        "公积金" to draft.housing, "企业年金" to draft.annuity, "税" to draft.tax)
    fun valueOf(label: String): String = when (label) {
        "收入" -> draft.gross
        "工会费" -> draft.union
        "养老保险" -> draft.pension
        "医疗保险" -> draft.medical
        "失业保险" -> draft.unemployment
        "公积金" -> draft.housing
        "企业年金" -> draft.annuity
        else -> draft.tax
    }
    fun setValue(label: String, value: String) = change(when (label) {
        "收入" -> draft.copy(gross = value)
        "工会费" -> draft.copy(union = value)
        "养老保险" -> draft.copy(pension = value)
        "医疗保险" -> draft.copy(medical = value)
        "失业保险" -> draft.copy(unemployment = value)
        "公积金" -> draft.copy(housing = value)
        "企业年金" -> draft.copy(annuity = value)
        else -> draft.copy(tax = value)
    })
    val amounts = listOf(draft.gross) + deductionFields.map { it.second }
    val parsed = amounts.mapIndexed { index, amount ->
        runCatching { SalaryAmountInput.evaluate(amount, allowNegative = index == amounts.lastIndex) }.getOrNull()
    }
    val valuesValid = parsed.all { it != null } && (parsed[0] ?: -1) >= 0 &&
        parsed.drop(1).dropLast(1).all { (it ?: -1) >= 0 }
    val deductionFen = parsed.drop(1).filterNotNull().fold(BigInteger.ZERO) { total, amount -> total + BigInteger.valueOf(amount) }
    val net = if (valuesValid) BigInteger.valueOf(parsed.first()!!) - deductionFen else null
    val deductionLabel = deductionFen.takeIf { valuesValid }?.let(::formatYuan) ?: "—"
    var saving by remember(entry?.id) { mutableStateOf(false) }
    val validToSave = draft.typeId.isNotEmpty()
    val canEditAmount = selectedType != null && selectedType.isCash == cash &&
        (selectedType.active || entry?.incomeTypeId == selectedType.id)

    fun showDatePicker() {
        DatePickerDialog(context, { _, year, month, day -> change(draft.copy(date = LocalDate.of(year, month + 1, day))) },
            draft.date.year, draft.date.monthValue - 1, draft.date.dayOfMonth)
            .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }
    fun saveSalary() {
        try {
            val type = types.firstOrNull { it.id == draft.typeId } ?: error("请选择收入项")
            require(type.isCash == cash) { "请选择当前现金性质的收入项" }
            require(type.active || entry?.incomeTypeId == type.id) { "请选择启用的收入项" }
            require(valuesValid) { "请检查金额；最多保留两位小数" }
            val salary = SalaryEntry(entry?.id ?: java.util.UUID.randomUUID().toString(), draft.date, draft.typeId,
                parsed[0]!!, parsed[1]!!, parsed[2]!!, parsed[3]!!, parsed[4]!!, parsed[5]!!,
                parsed[6]!!, parsed[7]!!, type.isCash, entry?.createdAtMillis ?: System.currentTimeMillis(), System.currentTimeMillis())
            salary.validate(today)
            error = null
            saving = true
            save(salary) { succeeded ->
                saving = false
                if (!succeeded) error = "工资保存失败，请检查后重试。"
            }
        } catch (exception: Exception) { error = exception.message ?: "请检查金额和日期" }
    }
    fun editKey(key: String) {
        val current = valueOf(activeField)
        val next = when (key) {
            "⌫" -> current.dropLast(1)
            "+", "−" -> if (current.isNotEmpty() && current.last().isDigit()) current + if (key == "−") "-" else "+" else current
            "." -> {
                val part = current.substringAfterLast('+').substringAfterLast('-')
                if (part.contains('.')) current else current + if (part.isEmpty()) "0." else "."
            }
            else -> if (current.length < 32) current + key else current
        }
        if (next != current) { setValue(activeField, next); error = null }
    }
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        SalaryHeader(if (entry == null) "新增收入" else "编辑收入", back,
            action = if (entry == null) null else ({
                TextButton(onClick = { deleting = true }, modifier = Modifier.testTag("salary-delete-entry")) {
                    CaijiIcon(CaijiIconName.DELETE, "删除此笔收入", Modifier.size(20.dp))
                }
            }))
        val cashPagerState = rememberPagerState(initialPage = if (cash) 0 else 1, pageCount = { 2 })
        val cashPagerScope = rememberCoroutineScope()
        fun selectCash(nextCash: Boolean) {
            if (nextCash != cash) {
                cash = nextCash
                onCashChange(nextCash)
                change(draft.copy(typeId = ""))
                onActiveFieldChange("收入")
                setKeyboardVisible(false)
                error = null
            }
        }
        LaunchedEffect(cashPagerState.settledPage) { selectCash(cashPagerState.settledPage == 0) }
        LaunchedEffect(draft.typeId, types, entry?.id, initialCash) {
            val restoredCash = initialCash ?: entry?.isCash ?: types.firstOrNull { it.id == draft.typeId }?.isCash ?: true
            if (restoredCash != cash) {
                cash = restoredCash
                onCashChange(restoredCash)
                cashPagerState.scrollToPage(if (restoredCash) 0 else 1)
            }
        }
        PagerSegmentedTabs(
            state = cashPagerState,
            labels = listOf("现金", "非现金"), tags = listOf("salary-cash-tab", "salary-income-noncash-tab"),
            containerTag = "salary-income-type-tabs", enabled = true,
            onSelectPage = { page ->
                cashPagerScope.launch { cashPagerState.animateScrollToPage(page) }
            }
        )
        HorizontalPager(state = cashPagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { pageIndex ->
            val pageCash = pageIndex == 0
            val availableTypes = types.filter { it.active && it.isCash == pageCash }
            val historicalSelectedType = selectedType?.takeIf { type ->
                !type.active && type.isCash == pageCash && entry?.incomeTypeId == type.id
            }
            val visibleTypes = availableTypes + listOfNotNull(historicalSelectedType)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .testTag("salary-editor-content-${if (pageCash) "cash" else "noncash"}")) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 0.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选择收入项", color = CaijiPalette.Ink, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onManageIncomeTypes, enabled = !saving,
                    modifier = Modifier.testTag("salary-manage-income-types")) {
                    CaijiIcon(CaijiIconName.CATEGORY, contentDescription = "管理收入项", modifier = Modifier.size(18.dp))
                    Text("管理收入项", modifier = Modifier.padding(start = 5.dp))
                }
            }
            if (visibleTypes.isEmpty()) {
                Text("暂无${if (pageCash) "现金" else "非现金"}收入项", Modifier.fillMaxWidth().padding(vertical = 24.dp)
                    .testTag("salary-no-income-types"), color = CaijiPalette.SecondaryText, textAlign = TextAlign.Center)
            } else {
                visibleTypes.chunked(4).forEach { rowTypes ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowTypes.forEach { type ->
                            val selected = draft.typeId == type.id
                            Column(Modifier.weight(1f).clickable {
                                change(draft.copy(typeId = type.id)); onActiveFieldChange("收入"); setKeyboardVisible(true); error = null
                            }.semantics { if (selected) contentDescription = "${type.name}，已选择" }
                                .testTag("salary-income-type-${type.id}").padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(shape = RoundedCornerShape(16.dp),
                                    color = if (selected) CaijiPalette.PrimarySoft else CaijiPalette.SurfaceAlt,
                                    modifier = Modifier.size(43.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Text(categoryEmoji(type.iconKey), style = MaterialTheme.typography.titleMedium) }
                                }
                                Text(type.name, Modifier.padding(top = 4.dp), color = CaijiPalette.SecondaryText,
                                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
                            }
                        }
                        repeat(4 - rowTypes.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (typeSelectionNotice != null) Text(typeSelectionNotice, Modifier.padding(vertical = 4.dp)
                .testTag("salary-disabled-type-notice"), color = CaijiPalette.SecondaryText)
            error?.let { Text(it, Modifier.padding(top = 8.dp).testTag("salary-editor-error"), color = CaijiPalette.Danger) }
            Spacer(Modifier.height(8.dp))
        }
        }
            if (keyboardVisible && canEditAmount) Column(Modifier.fillMaxWidth().testTag("salary-input-dock")) {
            HorizontalDivider(color = CaijiPalette.Divider)
            Row(Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 16.dp).testTag("salary-editor-summary"),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("纯收入 ¥${net?.let(::formatYuan) ?: "—"}", Modifier.testTag("salary-editor-net"),
                    color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("扣款合计 ¥$deductionLabel", Modifier.testTag("salary-deduction-total"),
                    color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AmountDisplay(
                label = "",
                amount = runCatching {
                    formatYuan(SalaryAmountInput.evaluate(valueOf(activeField), allowNegative = activeField == "税"))
                }.getOrDefault(valueOf(activeField).ifEmpty { "0.00" }),
                modifier = Modifier.padding(horizontal = 16.dp).testTag("salary-active-amount-container"),
                tag = "salary-active-amount"
            )
            val fieldNames = listOf("收入") + deductionFields.map { it.first }
            val fieldScrollState = rememberScrollState()
            val fieldBringIntoView = remember(fieldNames) { fieldNames.associateWith { BringIntoViewRequester() } }
            LaunchedEffect(activeField, canEditAmount, keyboardVisible) {
                if (canEditAmount && keyboardVisible) fieldBringIntoView[activeField]?.bringIntoView()
            }
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
              Row(
                modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(fieldScrollState)
                    .testTag("salary-field-selector"),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
              ) {
                fieldNames.forEach { field ->
                    val selected = activeField == field
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) CaijiPalette.PrimarySoft else CaijiPalette.SurfaceAlt,
                        modifier = Modifier.heightIn(min = 44.dp).bringIntoViewRequester(fieldBringIntoView.getValue(field)).clickable {
                            onActiveFieldChange(field)
                            setKeyboardVisible(true)
                            error = null
                        }.semantics { if (selected) contentDescription = "$field，正在输入" }
                            .testTag("salary-field-$field")
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp).defaultMinSize(minWidth = 44.dp), contentAlignment = Alignment.Center) {
                            Text(field, Modifier.testTag(if (selected) "salary-active-amount-label" else "salary-field-label-$field"),
                                color = if (selected) CaijiPalette.Ink else CaijiPalette.SecondaryText,
                                style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
              }
              if (activeField == "税") TextButton(onClick = {
                        val value = valueOf(activeField)
                        if (value.startsWith("-")) setValue(activeField, value.removePrefix("-"))
                        else setValue(activeField, "-$value")
                        error = null
                    }, modifier = Modifier.size(48.dp).testTag("salary-tax-sign")) { Text("±") }
            }
                Column(Modifier.testTag("salary-keypad")) {
                    AmountKeypad(date = draft.date, saving = saving,
                        canSave = !saving && validToSave && valuesValid && net?.signum() in listOf(0, 1) &&
                            !draft.date.isAfter(today), tagPrefix = "salary",
                        onDate = { showDatePicker() },
                        onSave = { saveSalary() },
                        onKey = { key -> editKey(key) })
                }
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, modifier = Modifier.testTag("salary-delete-dialog"),
        title = { Text("删除这笔收入？") }, text = { Text("删除后无法撤销。") },
        confirmButton = { TextButton(onClick = { deleting = false; entry?.let { delete(it.id) } }) {
            Text("删除", color = CaijiPalette.Danger)
        } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } })
}
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SalaryTypesScreen(
    types: List<SalaryIncomeType>,
    back: () -> Unit,
    setActive: (String, Boolean, (Boolean, String?) -> Unit) -> Unit,
    move: (String, Int, (Boolean, String?) -> Unit) -> Unit,
    create: (String, Boolean, String, (Boolean, String?) -> Unit) -> Unit,
    update: (String, String, String, (Boolean, String?) -> Unit) -> Unit
) {
    var selectedPage by rememberSaveable { mutableIntStateOf(0) }
    val pager = rememberPagerState(initialPage = selectedPage, pageCount = { 2 })
    val pagerScope = rememberCoroutineScope()
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var operationInProgress by remember { mutableStateOf(false) }
    var operationError by remember { mutableStateOf<String?>(null) }
    var orderedTypes by remember { mutableStateOf(types.sortedBy { it.sortOrder }) }
    val editing = types.firstOrNull { it.id == editingId }

    LaunchedEffect(pager.settledPage) { selectedPage = pager.settledPage }
    LaunchedEffect(types) { orderedTypes = types.sortedBy { it.sortOrder } }

    fun beginAdd() {
        if (operationInProgress || pager.isScrollInProgress) return
        addOpen = true
        editingId = null
        operationError = null
    }

    fun beginEdit(type: SalaryIncomeType) {
        if (operationInProgress || pager.isScrollInProgress || pager.currentPage != selectedPage) return
        editingId = type.id
        addOpen = false
        operationError = null
    }

    fun move(type: SalaryIncomeType, delta: Int, sameNature: List<SalaryIncomeType>) {
        val currentIndex = sameNature.indexOfFirst { it.id == type.id }
        val targetIndex = currentIndex + delta
        if (operationInProgress || pager.isScrollInProgress || pager.currentPage != selectedPage || targetIndex !in sameNature.indices) return
        operationInProgress = true
        operationError = null
        move(type.id, delta) { saved, message ->
            if (saved) {
                val reordered = orderedTypes.toMutableList()
                val from = reordered.indexOfFirst { it.id == type.id }
                val to = reordered.indexOfFirst { it.id == sameNature[targetIndex].id }
                val moved = reordered[from]
                reordered[from] = reordered[to]
                reordered[to] = moved
                orderedTypes = reordered
            } else operationError = message ?: "更新收入项顺序失败，请重试。"
            operationInProgress = false
        }
    }

    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        CaijiPageTopBar(
            title = "收入项管理", onBack = back, backTag = "salary-header-back",
            actionTag = "salary-income-types-add",
            action = {
                TextButton(onClick = ::beginAdd, enabled = !operationInProgress) {
                    CaijiIcon(CaijiIconName.ADD, "添加收入项", Modifier.size(18.dp))
                    Text("添加", Modifier.padding(start = 4.dp))
                }
            }
        )
        PagerSegmentedTabs(
            state = pager, labels = listOf("现金", "非现金"),
            tags = listOf("salary-income-cash-tab", "salary-income-noncash-tab"),
            containerTag = "salary-income-type-tabs", enabled = !operationInProgress,
            onSelectPage = { page -> if (page != pager.currentPage) pagerScope.launch { pager.animateScrollToPage(page) } }
        )
        Surface(
            color = CaijiPalette.PrimarySoft, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("停用的收入项仍会保留在历史工资记录中。",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
        }
        operationError?.let { message ->
            Surface(color = CaijiPalette.DangerSoft, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(message, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = CaijiPalette.Danger, style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalPager(
            state = pager,
            userScrollEnabled = !operationInProgress,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("salary-income-type-pager")
        ) { pageIndex ->
            val pageCash = pageIndex == 0
            val pageTypes = orderedTypes.filter { it.isCash == pageCash }
            if (pageTypes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有收入项，点击右上角添加", color = CaijiPalette.SecondaryText)
                }
            } else LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("salary-income-types-list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(pageTypes, key = { _, type -> type.id }) { index, type ->
                    Surface(color = CaijiPalette.Surface, shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.fillMaxWidth().testTag("salary-income-type-row-${type.id}")) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = CaijiPalette.SurfaceAlt, shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(42.dp).clickable(enabled = !operationInProgress) { beginEdit(type) }
                                    .testTag("salary-income-type-icon-button-${type.id}")) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(categoryEmoji(type.iconKey), style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.testTag("salary-income-type-icon-${type.id}"))
                                }
                            }
                            Column(Modifier.weight(1f).clickable(enabled = !operationInProgress) { beginEdit(type) }
                                .testTag("salary-income-type-name-${type.id}"),
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(type.name, color = if (type.active) CaijiPalette.Ink else CaijiPalette.SecondaryText,
                                    style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (!type.active) Text("已停用", color = CaijiPalette.SecondaryText,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            ManagementReorderButton("上移收入项 ${type.name}", "salary-income-type-move-up-${type.id}",
                                enabled = !operationInProgress && index > 0) { move(type, -1, pageTypes) }
                            ManagementReorderButton("下移收入项 ${type.name}", "salary-income-type-move-down-${type.id}",
                                enabled = !operationInProgress && index < pageTypes.lastIndex) { move(type, 1, pageTypes) }
                            Switch(
                                checked = type.active,
                                onCheckedChange = { active ->
                                    if (!operationInProgress && !pager.isScrollInProgress && pager.currentPage == pageIndex) {
                                        operationInProgress = true
                                        operationError = null
                                        setActive(type.id, active) { saved, message ->
                                            operationInProgress = false
                                            operationError = if (saved) null else message ?: "更新收入项状态失败，请重试。"
                                        }
                                    }
                                },
                                enabled = !operationInProgress,
                                modifier = Modifier.testTag("salary-income-type-active-${type.id}")
                                    .semantics { contentDescription = "启用收入项 ${type.name}" }
                            )
                        }
                    }
                }
            }
        }
    }

    if (addOpen || editing != null) {
        val cash = editing?.isCash ?: (selectedPage == 0)
        var name by rememberSaveable(editingId, selectedPage) { mutableStateOf(editing?.name.orEmpty()) }
        var iconKey by rememberSaveable(editingId, selectedPage) { mutableStateOf(editing?.iconKey ?: "other") }
        var error by rememberSaveable(editingId, selectedPage) { mutableStateOf<String?>(null) }
        var saving by remember(editingId, selectedPage) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!saving) { addOpen = false; editingId = null; error = null } },
            title = { Text(if (editing == null) "添加收入项" else "编辑收入项") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(if (cash) "现金收入" else "非现金收入", color = CaijiPalette.SecondaryText,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("salary-income-type-cash-fixed"))
                    OutlinedTextField(value = name, onValueChange = { name = it; error = null },
                        label = { Text("收入项名称") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("salary-income-type-name"))
                    Text("选择图标", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.labelMedium)
                    IconCatalogPicker(iconKey, Modifier.height(210.dp).testTag("salary-income-type-icon-grid")) {
                        iconKey = it; error = null
                    }
                    error?.let { Text(it, color = CaijiPalette.Danger, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("salary-income-type-error")) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (saving) return@Button
                    saving = true
                    error = null
                    operationInProgress = true
                    val done: (Boolean, String?) -> Unit = { saved, message ->
                        saving = false
                        operationInProgress = false
                        if (saved) { addOpen = false; editingId = null }
                        else error = message ?: "收入项保存失败，请检查内容后重试。"
                    }
                    if (editing == null) create(name.trim(), cash, iconKey, done)
                    else update(editing.id, name.trim(), iconKey, done)
                }, enabled = name.trim().isNotEmpty() && !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = CaijiPalette.Primary, contentColor = CaijiPalette.OnPrimary),
                    modifier = Modifier.testTag("salary-income-type-save")) { Text(if (saving) "保存中" else "保存") }
            },
            dismissButton = { TextButton(onClick = { addOpen = false; editingId = null; error = null }, enabled = !saving) { Text("取消") } }
        )
    }
}
@Composable
private fun SalaryImportScreen(preview: SalaryImportPreview?, duplicates: Int, entriesToInsert: List<SalaryEntry>, typeNames: Map<String, String>, back: () -> Unit, select: () -> Unit, confirm: () -> Unit) {
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        SalaryHeader("导入 Excel", back)
        if (preview == null) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("选择工资 .xlsx 文件", style = MaterialTheme.typography.titleLarge)
                Text("导入前会检查重复记录和金额", Modifier.padding(8.dp), color = CaijiPalette.SecondaryText)
                Button(onClick = select) { Text("选择 Excel 文件") }
            }
        } else {
            val fileIssues = preview.errors.filter { it.kind != SalaryImportErrorKind.CELL }
            val cellIssues = preview.errors.filter { it.kind == SalaryImportErrorKind.CELL }
            val hasIssues = preview.errors.isNotEmpty()
            if (!hasIssues) {
                val net = SalaryReports.summarize(entriesToInsert).netFen
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("新增 " + entriesToInsert.size + " 笔 · 跳过重复 " + duplicates + " 笔")
                    Text("新增纯收入 ¥" + formatYuan(net), Modifier.padding(top = 8.dp))
                    if (preview.incomeTypesToCreate.isNotEmpty()) Text("将新增 " + preview.incomeTypesToCreate.size + " 个自定义收入项", Modifier.padding(top = 8.dp), color = CaijiPalette.Income)
                    if (preview.incomeTypesToRestore.isNotEmpty()) Text("将更新 " + preview.incomeTypesToRestore.size + " 个预置收入项的名称和图标", Modifier.padding(top = 8.dp), color = CaijiPalette.Income)
                    entriesToInsert.take(30).forEach { Text(it.occurredOn.toString() + "　" + (typeNames[it.incomeTypeId] ?: it.incomeTypeId) + "　¥" + formatYuan(it.netIncomeFen), Modifier.padding(vertical = 5.dp)) }
                    if (entriesToInsert.size > 30) Text("其余 " + (entriesToInsert.size - 30) + " 笔将在确认后导入", color = CaijiPalette.SecondaryText)
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(if (fileIssues.isEmpty()) "发现 " + cellIssues.size + " 处问题，尚未导入" else "文件未能读取，尚未导入", color = CaijiPalette.Danger, style = MaterialTheme.typography.titleMedium)
                    Text(if (fileIssues.isEmpty()) "请按下方位置修正原文件，保存后重新选择。存在问题时，整份文件不会导入。" else "这是文件级错误，无需按行修改表格。已有工资记录未更改。", Modifier.padding(top = 7.dp), color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
                    fileIssues.forEach { issue ->
                        ImportIssueCard(issue, null)
                    }
                    cellIssues.forEach { issue ->
                        ImportIssueCard(issue, "Sheet1 · " + (issue.cell ?: "") + " · " + (issue.field ?: ""))
                    }
                }
                Button(onClick = select, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp), colors = ButtonDefaults.buttonColors(containerColor = CaijiPalette.Primary, contentColor = CaijiPalette.OnPrimary)) { Text("重新选择文件") }
            }
            Button(onClick = confirm, enabled = !hasIssues && preview.entries.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(18.dp), colors = ButtonDefaults.buttonColors(containerColor = CaijiPalette.Primary, contentColor = CaijiPalette.OnPrimary)) {
                Text(if (hasIssues) "修正问题后可确认导入" else "确认导入")
            }
        }
    }
}

@Composable
private fun ImportIssueCard(issue: SalaryImportError, location: String?) {
    Surface(color = CaijiPalette.Surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(location ?: issue.reason, color = CaijiPalette.Danger, fontWeight = FontWeight.SemiBold)
            if (location != null && issue.currentValue != null) Text("当前内容：" + issue.currentValue, Modifier.padding(top = 6.dp), color = CaijiPalette.SecondaryText)
            if (location != null) Text(issue.reason, Modifier.padding(top = 6.dp), fontWeight = FontWeight.Medium)
            Text("修改方法", Modifier.padding(top = 8.dp), color = CaijiPalette.Primary, style = MaterialTheme.typography.bodySmall)
            Text(issue.suggestion, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall)
            Text("错误编号：" + issue.code, Modifier.padding(top = 6.dp), color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable
private fun SalaryExportScreen(back: () -> Unit, export: (SalaryPeriod) -> Unit) {
    var all by remember { mutableStateOf(true) }
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var yearMenu by remember { mutableStateOf(false) }
    val thisYear = LocalDate.now().year
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        SalaryHeader("导出 Excel", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("文件格式", color = CaijiPalette.SecondaryText)
            Text(".xlsx · Sheet1 · 11 列，与工资样例一致", Modifier.padding(top = 8.dp))
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Row(Modifier.fillMaxWidth().clickable { all = true }, verticalAlignment = Alignment.CenterVertically) {
                Text("全部记录（推荐备份）", Modifier.weight(1f)); RadioButton(selected = all, onClick = { all = true }, modifier = Modifier.testTag("salary-export-all"))
            }
            Row(Modifier.fillMaxWidth().clickable { all = false }, verticalAlignment = Alignment.CenterVertically) {
                Text("指定年份", Modifier.weight(1f)); RadioButton(selected = !all, onClick = { all = false }, modifier = Modifier.testTag("salary-export-year"))
            }
            if (!all) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("导出年份", Modifier.weight(1f), color = CaijiPalette.SecondaryText)
                    Box {
                        TextButton(onClick = { yearMenu = true }, modifier = Modifier.testTag("salary-export-year-label")) { Text("$year 年") }
                        DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }, modifier = Modifier.background(CaijiPalette.Surface).testTag("salary-export-year-options")) {
                            (thisYear downTo 1900).forEach { option -> DropdownMenuItem(
                                text = { Text("$option 年") }, onClick = { year = option; yearMenu = false },
                                modifier = Modifier.testTag("salary-export-year-option-$option")) }
                        }
                    }
                }
            }
            Text("含所有收入、现金收入和非现金收入合计。", Modifier.padding(top = 12.dp), color = CaijiPalette.SecondaryText)
        }
        Button(onClick = { export(if (all) SalaryPeriod.All else SalaryPeriod.Year(year)) },
            Modifier.fillMaxWidth().padding(18.dp).testTag("salary-export-save"),
            colors = ButtonDefaults.buttonColors(containerColor = CaijiPalette.Primary, contentColor = CaijiPalette.OnPrimary)) {
            Text("选择保存位置")
        }
    }
}

@Composable
private fun SalaryHeader(
    title: String,
    back: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    backStartPadding: Dp = 0.dp
) {
    CaijiPageTopBar(
        title = title,
        onBack = back,
        backTag = "salary-header-back",
        actionTag = "salary-header-action",
        backStartPadding = backStartPadding,
        action = action
    )
}
private fun inFen(f:Long)=BigDecimal.valueOf(f,2).toPlainString()
private fun String.money(negative:Boolean):Long{val x=BigDecimal(trim().ifEmpty{"0"});require(x.scale()<=2||x.stripTrailingZeros().scale()<=2){"金额最多保留两位小数"};if(!negative)require(x.signum()>=0){"金额不能为负数"};return x.movePointRight(2).longValueExact()}
private fun List<String>.sumFen()=fold(BigInteger.ZERO){a,s->a+BigInteger.valueOf(runCatching{s.money(true)}.getOrDefault(0L))}
