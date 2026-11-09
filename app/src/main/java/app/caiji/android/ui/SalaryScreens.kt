package app.caiji.android.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import app.caiji.android.data.*
import app.caiji.android.ui.theme.CaijiPalette
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.*
import kotlin.math.roundToInt

private enum class SR { HOME, SUMMARY, EDIT, TYPES, IMPORT, EXPORT }
internal enum class SalaryManagementRoute { HOME, IMPORT, EXPORT, TYPES }

internal data class SalaryDraft(val date:LocalDate,val typeId:String,val gross:String,val union:String,val pension:String,val medical:String,val unemployment:String,val housing:String,val annuity:String,val tax:String) : java.io.Serializable {
 companion object {
  fun blank(typeId:String)=SalaryDraft(LocalDate.now(),typeId,"","","","","","","","")
  fun from(e:SalaryEntry)=SalaryDraft(e.occurredOn,e.incomeTypeId,inFen(e.grossFen),inFen(e.unionFen),inFen(e.pensionFen),inFen(e.medicalFen),inFen(e.unemploymentFen),inFen(e.housingFen),inFen(e.annuityFen),inFen(e.taxFen))
 }
}

@Composable
internal fun SalaryRootScreen(
 repo:RoomSalaryRepository,preview:SalaryImportPreview?,onImport:()->Unit,onClear:()->Unit,onExport:(SalaryPeriod)->Unit,
 initialDraft:SalaryDraft?=null,initialEntry:SalaryEntry?=null,onDraftSnapshotChange:(SalaryDraft?,SalaryEntry?)->Unit={_,_->},
 onAsyncError:(String)->Unit={},onBottomNavigationVisibility:(Boolean)->Unit={},
 initialManagementRoute:SalaryManagementRoute=SalaryManagementRoute.HOME,onManagementBack:()->Unit={},
 onManagementSuccess:(String)->Unit={}
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
 var message by remember { mutableStateOf<String?>(null) }
 var typeSelectionNotice by remember { mutableStateOf<String?>(null) }
 var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
 var deductionsExpanded by rememberSaveable { mutableStateOf(false) }
 var chooseFirstActiveTypeWhenLoaded by rememberSaveable { mutableStateOf(false) }
 var homeSwipeId by rememberSaveable { mutableStateOf<String?>(null) }
 var summarySwipeId by rememberSaveable { mutableStateOf<String?>(null) }
 val homeListState = rememberLazyListState()
 val summaryListState = rememberLazyListState()
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
   SR.TYPES -> when { typesOrigin == SR.EDIT -> page = SR.EDIT; settingsOrigin -> onManagementBack(); else -> page = typesOrigin }
   SR.IMPORT, SR.EXPORT -> if (settingsOrigin) { if (page == SR.IMPORT) onClear(); onManagementBack() } else { if (page == SR.IMPORT) onClear(); page = SR.HOME }
   SR.EDIT -> { onDraftSnapshotChange(null, null); selected = null; draft = null; page = editOrigin }
   SR.HOME -> Unit
  }
 }
 when(page){
 SR.HOME->SalaryHome(
  records = records, types = types, year = year, summary = false, message = message,
  dismiss = { message = null }, previous = { year = (year - 1).coerceAtLeast(1900); homeSwipeId = null },
  next = { year = (year + 1).coerceAtMost(LocalDate.now().year); homeSwipeId = null }, setYear = { year = it; homeSwipeId = null },
  openSummary = { page = SR.SUMMARY }, backToYear = {}, listState = homeListState,
  openSwipeEntryId = homeSwipeId, setSwipeEntryId = { homeSwipeId = it },
  delete = deleteFromList,
  add = { selected = null; editOrigin = SR.HOME; deductionsExpanded = false; homeSwipeId = null; typeSelectionNotice = null
   chooseFirstActiveTypeWhenLoaded = types.isEmpty()
   val nextDraft = SalaryDraft.blank(types.firstOrNull { it.active }?.id.orEmpty()); draft = nextDraft
   onDraftSnapshotChange(nextDraft, null); page = SR.EDIT },
  edit = { entry -> selected = entry; editOrigin = SR.HOME; deductionsExpanded = false; homeSwipeId = null; typeSelectionNotice = null; chooseFirstActiveTypeWhenLoaded = false
   val nextDraft = SalaryDraft.from(entry); draft = nextDraft; onDraftSnapshotChange(nextDraft, entry); page = SR.EDIT }
 )
 SR.SUMMARY->SalaryHome(
  records = records, types = types, year = year, summary = true, message = message,
  dismiss = { message = null }, previous = {}, next = {}, setYear = {}, openSummary = {}, backToYear = { page = SR.HOME },
  listState = summaryListState,
  openSwipeEntryId = summarySwipeId, setSwipeEntryId = { summarySwipeId = it }, delete = deleteFromList, add = {},
  edit = { entry -> selected = entry; editOrigin = SR.SUMMARY; deductionsExpanded = false; summarySwipeId = null; typeSelectionNotice = null; chooseFirstActiveTypeWhenLoaded = false
   val nextDraft = SalaryDraft.from(entry); draft = nextDraft; onDraftSnapshotChange(nextDraft, entry); page = SR.EDIT }
 )
 SR.EDIT->SalaryEditor(selected,types,draft?:SalaryDraft.blank(types.firstOrNull { it.active }?.id.orEmpty()),deductionsExpanded,{deductionsExpanded=it},updateDraft,{page=editOrigin;selected=null;draft=null;onDraftSnapshotChange(null,null)},{e->scope.launch{runCatching{withContext(Dispatchers.IO){repo.save(e)};refresh()}.onSuccess{message="工资记录已保存";selected=null;draft=null;onDraftSnapshotChange(null,null);page=editOrigin}.onFailure{val text="工资保存失败，输入内容已保留，请重试；若仍失败，请在设置中提交问题反馈。";message=text;onAsyncError(text)}}},{id->scope.launch{runCatching{withContext(Dispatchers.IO){repo.delete(id)};refresh()}.onSuccess{message="工资记录已删除";selected=null;draft=null;onDraftSnapshotChange(null,null);page=editOrigin}.onFailure{val text="工资删除失败，请重试；记录仍保留。若仍失败，请在设置中提交问题反馈。";message=text;onAsyncError(text)}}},typeSelectionNotice)
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
    edit: (SalaryEntry) -> Unit
) {
    var yearMenu by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteMessage by remember { mutableStateOf<String?>(null) }
    val currentYear = LocalDate.now().year
    val periodRows = if (summary) records else SalaryReports.filter(records, SalaryPeriod.Year(year))
    val sums = SalaryReports.summarize(periodRows)
    val typesById = types.associateBy { it.id }
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        if (summary) {
            SalaryHeader("汇总", backToYear)
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
            primaryLabel = if (summary) "累计纯收入" else "本年纯收入",
            primaryFen = sums.netFen,
            leftLabel = if (summary) "累计收入" else "本年收入",
            leftFen = sums.grossFen,
            leftColor = CaijiPalette.Income,
            rightLabel = if (summary) "累计扣款" else "本年扣款",
            rightFen = sums.deductionFen,
            primaryTag = "salary-net-total",
            leftTag = "salary-gross-total"
        )
        if (message != null) TextButton(onClick = dismiss) { Text(message, color = CaijiPalette.SecondaryText) }
        deleteMessage?.let {
            Text(it, Modifier.padding(horizontal = 18.dp, vertical = 2.dp).testTag("salary-list-delete-error"),
                color = CaijiPalette.Danger, style = MaterialTheme.typography.bodySmall)
        }
        if (periodRows.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text("还没有工资记录", style = MaterialTheme.typography.titleLarge)
            Text("记录工资、补贴和福利，或从 Excel 导入。", color = CaijiPalette.SecondaryText,
                style = MaterialTheme.typography.bodySmall)
        } else LazyColumn(Modifier.weight(1f).testTag("salary-record-list"), state = listState) {
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
private fun SalaryEntryRow(
    entry: SalaryEntry,
    name: String,
    iconKey: String,
    summary: Boolean,
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
                val date = if (summary) entry.occurredOn.format(DateTimeFormatter.ofPattern("yyyy/M/d", Locale.CHINA))
                    else entry.occurredOn.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
                Text(date + " · " + if (entry.isCash) "现金" else "非现金", color = CaijiPalette.SecondaryText,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("salary-entry-date-${entry.id}"))
            }
            Text("+¥" + formatYuan(entry.netIncomeFen), Modifier.widthIn(max = 140.dp), color = CaijiPalette.Income,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SalaryEditor(
    entry: SalaryEntry?, types: List<SalaryIncomeType>, draft: SalaryDraft, expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit, change: (SalaryDraft) -> Unit, back: () -> Unit,
    save: (SalaryEntry) -> Unit, delete: (String) -> Unit,
    typeSelectionNotice: String?
) {
    val today = LocalDate.now()
    var error by remember(entry?.id) { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val net = runCatching {
        BigInteger.valueOf(draft.gross.money(false)) - listOf(draft.union, draft.pension, draft.medical,
            draft.unemployment, draft.housing, draft.annuity, draft.tax).sumFen()
    }.getOrDefault(BigInteger.ZERO)
    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        SalaryHeader(if (entry == null) "新增收入" else "编辑收入", back,
            action = if (entry == null) null else ({
                TextButton(onClick = { deleting = true }, modifier = Modifier.testTag("salary-delete-entry")) {
                    CaijiIcon(CaijiIconName.DELETE, "删除此笔收入", Modifier.size(20.dp))
                }
            }))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                    .testTag("salary-editor-content")) {
            Surface(color = CaijiPalette.PrimarySoft, shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text("纯收入 · 自动计算", color = CaijiPalette.SecondaryText,
                        style = MaterialTheme.typography.bodySmall)
                    Text("¥${formatYuan(net)}", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("salary-editor-net"))
                }
            }
            typeSelectionNotice?.let { Text(it, Modifier.padding(top = 8.dp).testTag("salary-disabled-type-notice"),
                color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall) }
            Surface(color = CaijiPalette.Surface, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    val context = LocalContext.current
                    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable {
                        DatePickerDialog(context, { _, year, month, day ->
                            change(draft.copy(date = LocalDate.of(year, month + 1, day)))
                        }, draft.date.year, draft.date.monthValue - 1, draft.date.dayOfMonth)
                            .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Text("日期"); Spacer(Modifier.weight(1f))
                        Text(draft.date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
                    }
                    HorizontalDivider(color = CaijiPalette.Divider)
                    var menu by remember { mutableStateOf(false) }
                    val selectedType = types.firstOrNull { it.id == draft.typeId }
                    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(enabled = types.any { it.active }) {
                        menu = true
                    }.testTag("salary-income-type-selector"), verticalAlignment = Alignment.CenterVertically) {
                        Text("收入项")
                        Spacer(Modifier.width(12.dp))
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End) {
                            if (selectedType != null) Text(categoryEmoji(selectedType.iconKey), modifier = Modifier.padding(end = 7.dp))
                            Text(selectedType?.name ?: if (types.none { it.active }) "暂无启用收入项" else "选择收入项",
                                maxLines = 1, textAlign = TextAlign.End, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(menu, { menu = false }) {
                            types.filter { it.active }.forEach { type ->
                                DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(categoryEmoji(type.iconKey)); Text(type.name, Modifier.padding(start = 9.dp),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } }, onClick = { change(draft.copy(typeId = type.id)); menu = false })
                            }
                        }
                    }
                }
            }
            Surface(color = CaijiPalette.Surface, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    SalaryMoneyField("收入", draft.gross, { change(draft.copy(gross = it)) })
                    HorizontalDivider(color = CaijiPalette.Divider)
                    val deduction = listOf(draft.union, draft.pension, draft.medical, draft.unemployment,
                        draft.housing, draft.annuity, draft.tax).sumFen()
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onExpandedChange(!expanded) }
                        .semantics { contentDescription = if (expanded) "收起扣款合计" else "展开扣款合计" }
                        .testTag("salary-deductions-toggle"), verticalAlignment = Alignment.CenterVertically) {
                        Text("扣款合计"); Spacer(Modifier.weight(1f))
                        Text("¥${formatYuan(deduction)}" + if (expanded) "　⌄" else "　⌃", color = CaijiPalette.SecondaryText)
                    }
                    if (expanded) listOf("工会费" to draft.union, "养老保险" to draft.pension,
                        "医疗保险" to draft.medical, "失业保险" to draft.unemployment,
                        "公积金" to draft.housing, "企业年金" to draft.annuity, "税" to draft.tax).forEach { (label, value) ->
                        HorizontalDivider(color = CaijiPalette.Divider)
                        val update: (String) -> Unit = { changed ->
                            change(when (label) {
                                "工会费" -> draft.copy(union = changed)
                                "养老保险" -> draft.copy(pension = changed)
                                "医疗保险" -> draft.copy(medical = changed)
                                "失业保险" -> draft.copy(unemployment = changed)
                                "公积金" -> draft.copy(housing = changed)
                                "企业年金" -> draft.copy(annuity = changed)
                                else -> draft.copy(tax = changed)
                            })
                        }
                        SalaryMoneyField(label, value, update, tax = label == "税")
                    }
                }
            }
            Text("扣款可留空；税额填负数表示退税。", Modifier.padding(top = 12.dp),
                color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, Modifier.padding(top = 8.dp), color = CaijiPalette.Danger) }
            Spacer(Modifier.height(12.dp))
        }
        Button(onClick = {
            try {
                val type = types.firstOrNull { it.id == draft.typeId } ?: error("请选择收入项")
                require(type.active || entry?.incomeTypeId == type.id) { "请选择启用的收入项" }
                val salary = SalaryEntry(entry?.id ?: java.util.UUID.randomUUID().toString(), draft.date, draft.typeId,
                    draft.gross.money(false), draft.union.money(false), draft.pension.money(false), draft.medical.money(false),
                    draft.unemployment.money(false), draft.housing.money(false), draft.annuity.money(false), draft.tax.money(true),
                    type.isCash, entry?.createdAtMillis ?: System.currentTimeMillis(), System.currentTimeMillis())
                salary.validate(today); error = null; save(salary)
            } catch (exception: Exception) { error = exception.message ?: "请检查金额和日期" }
        }, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CaijiPalette.Primary, contentColor = CaijiPalette.OnPrimary)) {
            Text("保存收入")
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, modifier = Modifier.testTag("salary-delete-dialog"),
        title = { Text("删除这笔收入？") }, text = { Text("删除后无法撤销。") },
        confirmButton = { TextButton(onClick = { deleting = false; entry?.let { delete(it.id) } }) {
            Text("删除", color = CaijiPalette.Danger)
        } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } })
}

@Composable private fun SalaryMoneyField(label:String,value:String,change:(String)->Unit,tax:Boolean=false){Row(Modifier.fillMaxWidth().heightIn(min=54.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));OutlinedTextField(value,change,Modifier.weight(1f).widthIn(max=190.dp).testTag("salary-field-"+label),singleLine=true,textStyle=MaterialTheme.typography.bodyLarge.copy(textAlign=TextAlign.End),placeholder={Text("0.00",modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.End)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),prefix={Text(if(tax)"±¥" else "¥")})}}
@Composable
private fun SalaryTypesScreen(
    types: List<SalaryIncomeType>,
    back: () -> Unit,
    setActive: (String, Boolean, (Boolean, String?) -> Unit) -> Unit,
    move: (String, Int, (Boolean, String?) -> Unit) -> Unit,
    create: (String, Boolean, String, (Boolean, String?) -> Unit) -> Unit,
    update: (String, String, String, (Boolean, String?) -> Unit) -> Unit
) {
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var cash by rememberSaveable { mutableStateOf(true) }
    var iconKey by rememberSaveable { mutableStateOf("other") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var operationInProgress by remember { mutableStateOf(false) }
    var operationError by remember { mutableStateOf<String?>(null) }
    val editing = types.firstOrNull { it.id == editingId }
    fun beginAdd() {
        addOpen = true
        editingId = null
        name = ""
        cash = true
        iconKey = "other"
        error = null
    }

    fun beginEdit(type: SalaryIncomeType) {
        addOpen = false
        editingId = type.id
        name = type.name
        cash = type.isCash
        iconKey = type.iconKey
        error = null
    }

    Column(Modifier.fillMaxSize().background(CaijiPalette.Background)) {
        CaijiPageTopBar(
            title = "收入项管理",
            onBack = back,
            backTag = "salary-header-back",
            actionTag = "salary-income-types-add",
            action = {
                TextButton(onClick = ::beginAdd) {
                    CaijiIcon(CaijiIconName.ADD, "添加收入项", Modifier.size(18.dp))
                    Text("添加", Modifier.padding(start = 4.dp))
                }
            }
        )
        if (types.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("还没有收入项，点击右上角添加", color = CaijiPalette.SecondaryText)
            }
        } else LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).testTag("salary-income-types-list"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(types.sortedWith(compareBy<SalaryIncomeType> { it.sortOrder }.thenBy { it.name }), key = { it.id }) { type ->
                Surface(
                    color = CaijiPalette.Surface,
                    shape = RoundedCornerShape(15.dp),
                    modifier = Modifier.fillMaxWidth().testTag("salary-income-type-row-${type.id}")
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = CaijiPalette.SurfaceAlt,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(categoryEmoji(type.iconKey), style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.testTag("salary-income-type-icon-${type.id}"))
                            }
                        }
                        Column(Modifier.weight(1f).clickable(enabled = !operationInProgress) { beginEdit(type) }
                            .testTag("salary-income-type-name-${type.id}"),
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                type.name,
                                color = CaijiPalette.Ink,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(if (type.isCash) "现金" else "非现金", color = CaijiPalette.SecondaryText,
                                style = MaterialTheme.typography.labelSmall)
                            if (!type.active) Text("已停用", color = CaijiPalette.SecondaryText,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        ManagementReorderButton("上移收入项 ${type.name}", "salary-income-type-move-up-${type.id}",
                            enabled = !operationInProgress && types.minOfOrNull { it.sortOrder } != type.sortOrder) {
                            if (!operationInProgress) {
                                operationInProgress = true; operationError = null
                                move(type.id, -1) { ok, message -> operationInProgress = false; operationError = if (ok) null else message }
                            }
                        }
                        ManagementReorderButton("下移收入项 ${type.name}", "salary-income-type-move-down-${type.id}",
                            enabled = !operationInProgress && types.maxOfOrNull { it.sortOrder } != type.sortOrder) {
                            if (!operationInProgress) {
                                operationInProgress = true; operationError = null
                                move(type.id, 1) { ok, message -> operationInProgress = false; operationError = if (ok) null else message }
                            }
                        }
                        Switch(
                            checked = type.active,
                            onCheckedChange = { enabled ->
                                if (!operationInProgress) {
                                    operationInProgress = true; operationError = null
                                    setActive(type.id, enabled) { ok, message -> operationInProgress = false; operationError = if (ok) null else message }
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
        operationError?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = CaijiPalette.Danger, style = MaterialTheme.typography.bodySmall) }
    }

    if (addOpen || editing != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) { addOpen = false; editingId = null; error = null } },
            title = { Text(if (editing == null) "添加收入项" else "编辑收入项") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (editing == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("现金收入")
                                Text("关闭开关表示非现金", color = CaijiPalette.SecondaryText,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(cash, { cash = it }, modifier = Modifier.testTag("salary-income-type-cash"))
                        }
                    } else Text(
                        if (editing.isCash) "现金收入" else "非现金收入",
                        color = CaijiPalette.SecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("salary-income-type-cash-fixed")
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        label = { Text("收入项名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("salary-income-type-name")
                    )
                    Text("选择图标", color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.labelMedium)
                    IconCatalogPicker(iconKey, Modifier.height(210.dp).testTag("salary-income-type-icon-grid")) {
                        iconKey = it
                        error = null
                    }
                    error?.let {
                        Text(it, color = CaijiPalette.Danger, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("salary-income-type-error"))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (saving) return@Button
                        saving = true
                        error = null
                        val done: (Boolean, String?) -> Unit = { saved, message ->
                            saving = false
                            if (saved) { addOpen = false; editingId = null }
                            else error = message ?: "收入项保存失败，请检查内容后重试。"
                        }
                        if (editing == null) create(name.trim(), cash, iconKey, done)
                        else update(editing.id, name.trim(), iconKey, done)
                    },
                    enabled = name.trim().isNotEmpty() && !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = CaijiPalette.Primary, contentColor = CaijiPalette.OnPrimary),
                    modifier = Modifier.testTag("salary-income-type-save")
        ) { Text(if (saving) "保存中" else if (editing == null) "添加" else "保存") }
            },
            dismissButton = {
                TextButton(onClick = { addOpen = false; editingId = null; error = null }, enabled = !saving) { Text("取消") }
            }
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
private fun SalaryHeader(title: String, back: () -> Unit, action: (@Composable () -> Unit)? = null) {
    CaijiPageTopBar(
        title = title,
        onBack = back,
        backTag = "salary-header-back",
        actionTag = "salary-header-action",
        action = action
    )
}
private fun inFen(f:Long)=BigDecimal.valueOf(f,2).toPlainString()
private fun String.money(negative:Boolean):Long{val x=BigDecimal(trim().ifEmpty{"0"});require(x.scale()<=2||x.stripTrailingZeros().scale()<=2){"金额最多保留两位小数"};if(!negative)require(x.signum()>=0){"金额不能为负数"};return x.movePointRight(2).longValueExact()}
private fun List<String>.sumFen()=fold(BigInteger.ZERO){a,s->a+BigInteger.valueOf(runCatching{s.money(true)}.getOrDefault(0L))}
