package app.caiji.android.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

private enum class SR { HOME, EDIT, TYPES, IMPORT, EXPORT }
private enum class PM { MONTH, YEAR, ALL }
private enum class CF { ALL, CASH, NONCASH }

private data class SalaryDraft(val date:LocalDate,val typeId:String,val gross:String,val union:String,val pension:String,val medical:String,val unemployment:String,val housing:String,val annuity:String,val tax:String) {
 companion object {
  fun blank(typeId:String)=SalaryDraft(LocalDate.now(),typeId,"0.00","0.00","0.00","0.00","0.00","0.00","0.00","0.00")
  fun from(e:SalaryEntry)=SalaryDraft(e.occurredOn,e.incomeTypeId,inFen(e.grossFen),inFen(e.unionFen),inFen(e.pensionFen),inFen(e.medicalFen),inFen(e.unemploymentFen),inFen(e.housingFen),inFen(e.annuityFen),inFen(e.taxFen))
 }
}

@Composable
internal fun SalaryRootScreen(repo:RoomSalaryRepository,preview:SalaryImportPreview?,onImport:()->Unit,onClear:()->Unit,onExport:(SalaryPeriod)->Unit){
 val scope=rememberCoroutineScope();var page by remember{mutableStateOf(SR.HOME)};var typesOrigin by remember{mutableStateOf(SR.HOME)};var records by remember{mutableStateOf(emptyList<SalaryEntry>())};var duplicates by remember{mutableIntStateOf(0)};var types by remember{mutableStateOf(emptyList<SalaryIncomeType>())};var selected by remember{mutableStateOf<SalaryEntry?>(null)};var draft by remember{mutableStateOf<SalaryDraft?>(null)};var message by remember{mutableStateOf<String?>(null)};var mode by remember{mutableStateOf(PM.MONTH)};var month by remember{mutableStateOf(YearMonth.now())};var year by remember{mutableIntStateOf(LocalDate.now().year)}
 suspend fun refresh(){withContext(Dispatchers.IO){types=repo.incomeTypes();records=repo.entries()}}
 LaunchedEffect(repo){runCatching{refresh()}.onFailure{message=it.message}}
 LaunchedEffect(preview){if(preview!=null){page=SR.IMPORT;duplicates=withContext(Dispatchers.IO){repo.duplicateCount(preview.entries)}}}
 BackHandler(page!=SR.HOME){if(page==SR.TYPES)page=typesOrigin else {if(page==SR.IMPORT)onClear();page=SR.HOME;selected=null;draft=null}}
 when(page){
 SR.HOME->SalaryHome(records,types,mode,month,year,message,{message=null},{mode=it},{if(mode==PM.MONTH)month=month.minusMonths(1) else year--},{if(mode==PM.MONTH)month=month.plusMonths(1) else year++},{selected=null;draft=SalaryDraft.blank(types.firstOrNull()?.id.orEmpty());page=SR.EDIT},{selected=it;draft=SalaryDraft.from(it);page=SR.EDIT},{typesOrigin=SR.HOME;page=SR.TYPES},{page=SR.IMPORT;onClear();onImport()},{onExport(it);page=SR.HOME})
 SR.EDIT->SalaryEditor(selected,types,draft?:SalaryDraft.blank(types.firstOrNull()?.id.orEmpty()),{draft=it},{page=SR.HOME;selected=null;draft=null},{typesOrigin=SR.EDIT;page=SR.TYPES},{e->scope.launch{runCatching{withContext(Dispatchers.IO){repo.save(e)};refresh()}.onSuccess{message="工资记录已保存";selected=null;draft=null;page=SR.HOME}.onFailure{message=it.message?:"保存失败"}}},{id->scope.launch{runCatching{withContext(Dispatchers.IO){repo.delete(id)};refresh()}.onSuccess{message="工资记录已删除";selected=null;draft=null;page=SR.HOME}.onFailure{message=it.message?:"删除失败"}}})
 SR.TYPES->SalaryTypesScreen(types,{page=typesOrigin},{n,c->scope.launch{runCatching{withContext(Dispatchers.IO){repo.createIncomeType(n,c)};refresh()}.onSuccess{message="收入项已添加"}.onFailure{message=it.message?:"添加失败"}}})
 SR.IMPORT->SalaryImportScreen(preview,duplicates,(SalaryIncomeTypes.defaults+types+preview?.incomeTypesToCreate.orEmpty()).associate{it.id to it.name},{page=SR.HOME;onClear()},onImport){val p=preview?:return@SalaryImportScreen;if(p.errors.isNotEmpty())return@SalaryImportScreen;scope.launch{runCatching{withContext(Dispatchers.IO){repo.importNew(p.entries,p.incomeTypesToCreate)}.also{refresh()}}.onSuccess{message="导入完成：新增 "+it.insertedCount+" 笔，跳过重复 "+it.skippedDuplicateCount+" 笔";page=SR.HOME;onClear()}.onFailure{message=it.message?:"导入失败"}}}
 SR.EXPORT->SalaryExportScreen({page=SR.HOME},month,year,mode,{onExport(it);page=SR.HOME})
 }
}

@Composable private fun SalaryHome(records:List<SalaryEntry>,types:List<SalaryIncomeType>,mode:PM,month:YearMonth,year:Int,message:String?,dismiss:()->Unit,setMode:(PM)->Unit,prev:()->Unit,next:()->Unit,add:()->Unit,edit:(SalaryEntry)->Unit,manage:()->Unit,import:()->Unit,exportPeriod:(SalaryPeriod)->Unit){
 var cf by remember{mutableStateOf(CF.ALL)};var menu by remember{mutableStateOf(false)};var exportDialog by remember{mutableStateOf(false)}
 val period=when(mode){PM.MONTH->SalaryPeriod.Month(month.year,month.monthValue);PM.YEAR->SalaryPeriod.Year(year);PM.ALL->SalaryPeriod.All}
 val rows=SalaryReports.filter(records,period);val visible=rows.filter{cf==CF.ALL||(cf==CF.CASH)==it.isCash};val sums=SalaryReports.summarize(rows);val names=types.associate{it.id to it.name}
 Column(Modifier.fillMaxSize().background(CaijiPalette.Background)){
  Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Text("工资",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f));Box{TextButton(onClick={menu=true}){Text("更多")};DropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("导入 Excel")},onClick={menu=false;import()});DropdownMenuItem(text={Text("导出 Excel")},onClick={menu=false;exportDialog=true});DropdownMenuItem(text={Text("管理收入项")},onClick={menu=false;manage()})}}}
  Row(Modifier.fillMaxWidth().padding(horizontal=20.dp).background(CaijiPalette.SurfaceAlt,RoundedCornerShape(12.dp)).padding(4.dp)){listOf(PM.MONTH to "月",PM.YEAR to "年",PM.ALL to "全部").forEach{(p,label)->val sel=mode==p;Surface(color=if(sel)CaijiPalette.Primary else CaijiPalette.SurfaceAlt,shape=RoundedCornerShape(9.dp),modifier=Modifier.weight(1f).clickable{setMode(p)}.testTag("salary-period-"+label)){Text(label,Modifier.padding(vertical=9.dp),color=if(sel)CaijiPalette.OnPrimary else CaijiPalette.SecondaryText,textAlign=TextAlign.Center)}}}
  Row(Modifier.fillMaxWidth().height(42.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){if(mode!=PM.ALL)IconButton(onClick=prev){Text("‹",style=MaterialTheme.typography.headlineSmall)};Text(when(mode){PM.MONTH->month.format(DateTimeFormatter.ofPattern("yyyy年M月",Locale.CHINA));PM.YEAR->year.toString()+"年";PM.ALL->"全部记录"},style=MaterialTheme.typography.titleMedium);if(mode!=PM.ALL)IconButton(onClick=next){Text("›",style=MaterialTheme.typography.headlineSmall)}}
  Surface(color=CaijiPalette.PrimarySoft,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp)){Column(Modifier.padding(horizontal=18.dp,vertical=13.dp)){Text(when(mode){PM.MONTH->"本月纯收入";PM.YEAR->"本年纯收入";PM.ALL->"累计纯收入"},color=CaijiPalette.SecondaryText,style=MaterialTheme.typography.bodySmall);Text("¥"+formatYuan(sums.netFen),style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.SemiBold,maxLines=1,modifier=Modifier.testTag("salary-net-total"));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp)){Column(Modifier.weight(1f)){Text("毛收入",color=CaijiPalette.SecondaryText);Text("¥"+formatYuan(sums.grossFen))};Column(Modifier.weight(1f)){Text("扣款合计",color=CaijiPalette.SecondaryText);Text("¥"+formatYuan(sums.deductionFen))}};Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("现金 ¥"+formatYuan(sums.cashNetFen),style=MaterialTheme.typography.bodySmall);Text("非现金 ¥"+formatYuan(sums.nonCashNetFen),style=MaterialTheme.typography.bodySmall)}}}
  if(message!=null)TextButton(onClick=dismiss){Text(message,color=CaijiPalette.SecondaryText)}
  Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=7.dp)){Text("收入明细",fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f));Text(rows.size.toString()+" 笔",color=CaijiPalette.SecondaryText)}
  Row(Modifier.fillMaxWidth().padding(horizontal=20.dp).background(CaijiPalette.SurfaceAlt,RoundedCornerShape(10.dp))){listOf(CF.ALL to "全部",CF.CASH to "现金",CF.NONCASH to "非现金").forEach{(f,l)->TextButton(onClick={cf=f},modifier=Modifier.weight(1f)){Text(l,color=if(cf==f)CaijiPalette.Primary else CaijiPalette.SecondaryText)}}}
  if(visible.isEmpty())Column(Modifier.weight(1f).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("还没有工资记录",style=MaterialTheme.typography.titleLarge);Text("记录工资、补贴和福利，或从 Excel 导入。",color=CaijiPalette.SecondaryText,style=MaterialTheme.typography.bodySmall)}
  else LazyColumn(Modifier.weight(1f)){items(visible, key = { it.id }){e->Row(Modifier.fillMaxWidth().clickable{edit(e)}.padding(horizontal=18.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Surface(color=CaijiPalette.SurfaceAlt,shape=RoundedCornerShape(12.dp),modifier=Modifier.size(40.dp)){Box(contentAlignment=Alignment.Center){Text(if(e.isCash)"¥" else "✦",color=CaijiPalette.Primary)}};Column(Modifier.weight(1f).padding(start=12.dp)){Text(names[e.incomeTypeId]?:e.incomeTypeId);Text(e.occurredOn.format(DateTimeFormatter.ofPattern("M月d日",Locale.CHINA))+" · "+if(e.isCash)"现金" else "非现金",color=CaijiPalette.SecondaryText,style=MaterialTheme.typography.bodySmall)};Text("+¥"+formatYuan(e.netIncomeFen),color=CaijiPalette.Income)};HorizontalDivider(color=CaijiPalette.Divider,modifier=Modifier.padding(start=70.dp,end=18.dp))}}
  Button(add,Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=7.dp).height(48.dp),colors=ButtonDefaults.buttonColors(containerColor=CaijiPalette.Primary,contentColor=CaijiPalette.OnPrimary),shape=RoundedCornerShape(12.dp)){Text("＋  新增收入")}
  Text("工资独立统计，不计入记账收支。",Modifier.padding(horizontal=18.dp,vertical=3.dp),color=CaijiPalette.SecondaryText,style=MaterialTheme.typography.bodySmall)
 }
 if(exportDialog)AlertDialog(onDismissRequest={exportDialog=false},title={Text("导出 Excel")},text={Text("选择导出当前期间或全部记录。")},confirmButton={TextButton(onClick={exportDialog=false;exportPeriod(period)}){Text("当前期间")}},dismissButton={Row{TextButton(onClick={exportDialog=false;exportPeriod(SalaryPeriod.All)}){Text("全部记录")};TextButton(onClick={exportDialog=false}){Text("取消")}}})
}

@Composable private fun SalaryEditor(entry:SalaryEntry?,types:List<SalaryIncomeType>,draft:SalaryDraft,change:(SalaryDraft)->Unit,back:()->Unit,manage:()->Unit,save:(SalaryEntry)->Unit,delete:(String)->Unit){
 val today=LocalDate.now();var expanded by remember(entry?.id){mutableStateOf(true)};var error by remember(entry?.id){mutableStateOf<String?>(null)};var deleting by remember{mutableStateOf(false)}
 Column(Modifier.fillMaxSize().background(CaijiPalette.Background)){
  SalaryHeader(if(entry==null)"新增收入" else "编辑收入",back)
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp)){
   Surface(color=CaijiPalette.Surface,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().padding(top=8.dp)){
    Column(Modifier.padding(horizontal=14.dp)){
     val ctx=LocalContext.current
     Row(Modifier.fillMaxWidth().heightIn(min=54.dp).clickable{DatePickerDialog(ctx,{_,y,m,d->change(draft.copy(date=LocalDate.of(y,m+1,d)))},draft.date.year,draft.date.monthValue-1,draft.date.dayOfMonth).apply{datePicker.maxDate=System.currentTimeMillis()}.show()},verticalAlignment=Alignment.CenterVertically){Text("时间");Spacer(Modifier.weight(1f));Text(draft.date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))}
     HorizontalDivider(color=CaijiPalette.Divider);var menu by remember{mutableStateOf(false)}
     Box{Row(Modifier.fillMaxWidth().heightIn(min=54.dp).clickable{menu=true},verticalAlignment=Alignment.CenterVertically){Text("收入项");Spacer(Modifier.weight(1f));Text(types.firstOrNull{it.id==draft.typeId}?.name?:"选择收入项",maxLines=1)};DropdownMenu(menu,{menu=false}){types.forEach{t->DropdownMenuItem(text={Text(t.name)},onClick={change(draft.copy(typeId=t.id));menu=false})}}}
     HorizontalDivider(color=CaijiPalette.Divider);Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){Text("收入性质");Spacer(Modifier.weight(1f));Text(if(types.firstOrNull{it.id==draft.typeId}?.isCash==true)"现金" else "非现金",color=CaijiPalette.SecondaryText)}
     TextButton(onClick=manage,Modifier.align(Alignment.End)){Text("管理收入项　›")}
    }
   }
   Surface(color=CaijiPalette.Surface,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().padding(top=12.dp)){
    Column(Modifier.padding(horizontal=14.dp)){
     SalaryMoneyField("毛收入",draft.gross,{change(draft.copy(gross=it))});HorizontalDivider(color=CaijiPalette.Divider)
     TextButton(onClick={expanded=!expanded},Modifier.fillMaxWidth()){Text("扣款明细");Spacer(Modifier.weight(1f));Text("¥"+formatYuan(listOf(draft.union,draft.pension,draft.medical,draft.unemployment,draft.housing,draft.annuity,draft.tax).sumFen())+if(expanded)"　⌃" else "　⌄")}
     if(expanded)listOf("工会费" to draft.union,"养老保险" to draft.pension,"医疗保险" to draft.medical,"失业保险" to draft.unemployment,"公积金" to draft.housing,"企业年金" to draft.annuity,"税" to draft.tax).forEach{(label,value)->
      HorizontalDivider(color=CaijiPalette.Divider)
      val update:(String)->Unit={v->change(when(label){"工会费"->draft.copy(union=v);"养老保险"->draft.copy(pension=v);"医疗保险"->draft.copy(medical=v);"失业保险"->draft.copy(unemployment=v);"公积金"->draft.copy(housing=v);"企业年金"->draft.copy(annuity=v);else->draft.copy(tax=v)})}
      SalaryMoneyField(label,value,update,label=="税")
     }
    }
   }
   Text("扣款可留空；税额填负数表示退税。",Modifier.padding(top=12.dp),color=CaijiPalette.SecondaryText,style=MaterialTheme.typography.bodySmall)
   val net=runCatching{BigInteger.valueOf(draft.gross.money(false))-listOf(draft.union,draft.pension,draft.medical,draft.unemployment,draft.housing,draft.annuity,draft.tax).sumFen()}.getOrDefault(BigInteger.ZERO)
   Surface(color=CaijiPalette.PrimarySoft,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(top=12.dp)){Column(Modifier.padding(18.dp)){Text("纯收入 · 自动计算",color=CaijiPalette.SecondaryText,style=MaterialTheme.typography.bodySmall);Text("¥"+formatYuan(net),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("salary-editor-net"))}}
   error?.let{Text(it,Modifier.padding(top=8.dp),color=CaijiPalette.Danger)}
   if(entry!=null)TextButton(onClick={deleting=true},Modifier.align(Alignment.CenterHorizontally)){Text("删除此笔收入",color=CaijiPalette.Danger)}
  }
  Button(onClick={try{
   val t=types.firstOrNull{it.id==draft.typeId}?:error("请选择收入项")
   val e=SalaryEntry(entry?.id?:java.util.UUID.randomUUID().toString(),draft.date,draft.typeId,draft.gross.money(false),draft.union.money(false),draft.pension.money(false),draft.medical.money(false),draft.unemployment.money(false),draft.housing.money(false),draft.annuity.money(false),draft.tax.money(true),t.isCash,entry?.createdAtMillis?:System.currentTimeMillis(),System.currentTimeMillis())
   e.validate(today);error=null;save(e)
  }catch(e:Exception){error=e.message?:"请检查金额和日期"}},Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp).height(48.dp),colors=ButtonDefaults.buttonColors(containerColor=CaijiPalette.Primary,contentColor=CaijiPalette.OnPrimary)){Text("保存收入")}
 }
 if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("删除这笔收入？")},text={Text("删除后无法撤销。")},confirmButton={TextButton(onClick={deleting=false;entry?.let{delete(it.id)}}){Text("删除",color=CaijiPalette.Danger)}},dismissButton={TextButton(onClick={deleting=false}){Text("取消")}})
}

@Composable private fun SalaryMoneyField(label:String,value:String,change:(String)->Unit,tax:Boolean=false){Row(Modifier.fillMaxWidth().heightIn(min=54.dp),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));OutlinedTextField(value,change,Modifier.weight(1f).widthIn(max=190.dp).testTag("salary-field-"+label),singleLine=true,placeholder={Text("0.00")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),prefix={Text(if(tax)"±¥" else "¥")})}}
@Composable private fun SalaryTypesScreen(types:List<SalaryIncomeType>,back:()->Unit,create:(String,Boolean)->Unit){var show by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")};var cash by remember{mutableStateOf(true)}
 Column(Modifier.fillMaxSize().background(CaijiPalette.Background)){SalaryHeader("收入项",back);LazyColumn(Modifier.weight(1f)){items(types, key = { it.id }){t->Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically){Text(t.name,Modifier.weight(1f));Text(if(t.isCash)"现金" else "非现金",color=CaijiPalette.SecondaryText)};HorizontalDivider(color=CaijiPalette.Divider)}};Button(onClick={show=true},Modifier.fillMaxWidth().padding(18.dp),colors=ButtonDefaults.buttonColors(containerColor=CaijiPalette.Primary,contentColor=CaijiPalette.OnPrimary)){Text("新增收入项")}}
 if(show)AlertDialog(onDismissRequest={show=false},title={Text("新增收入项")},text={Column{OutlinedTextField(name,{name=it},label={Text("收入项名称")});Row(verticalAlignment=Alignment.CenterVertically){Text("现金收入");Spacer(Modifier.weight(1f));Switch(cash,{cash=it})};Text("关闭开关表示非现金",color=CaijiPalette.SecondaryText)}},confirmButton={TextButton(onClick={create(name,cash);name="";show=false}){Text("添加")}},dismissButton={TextButton(onClick={show=false}){Text("取消")}})
}
@Composable private fun SalaryImportScreen(preview:SalaryImportPreview?,duplicates:Int,typeNames:Map<String,String>,back:()->Unit,select:()->Unit,confirm:()->Unit){Column(Modifier.fillMaxSize().background(CaijiPalette.Background)){SalaryHeader("导入预览",back)
 if(preview==null)Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("选择工资 .xlsx 文件",style=MaterialTheme.typography.titleLarge);Text("导入前会检查重复记录和金额",Modifier.padding(8.dp),color=CaijiPalette.SecondaryText);Button(select){Text("选择 Excel 文件")}}
 else{val sum=SalaryReports.summarize(preview.entries);Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)){Text("新增 "+(preview.entries.size-duplicates)+" 笔 · 跳过重复 "+duplicates+" 笔 · 纯收入 ¥"+formatYuan(sum.netFen));if(preview.incomeTypesToCreate.isNotEmpty())Text("将恢复 "+preview.incomeTypesToCreate.size+" 个自定义收入项",Modifier.padding(top=8.dp),color=CaijiPalette.Income);preview.errors.forEach{Text("第 "+it.row+" 行："+it.message,color=CaijiPalette.Danger,modifier=Modifier.padding(top=8.dp))};preview.entries.take(30).forEach{Text(it.occurredOn.toString()+"　"+(typeNames[it.incomeTypeId]?:it.incomeTypeId)+"　¥"+formatYuan(it.netIncomeFen),Modifier.padding(vertical=5.dp))};if(preview.entries.size>30)Text("其余 "+(preview.entries.size-30)+" 笔将在确认后导入",color=CaijiPalette.SecondaryText)};Button(onClick=confirm,enabled=preview.errors.isEmpty()&&preview.entries.isNotEmpty(),modifier=Modifier.fillMaxWidth().padding(18.dp)){Text("确认导入")}}
}}
@Composable private fun SalaryExportScreen(back:()->Unit,month:YearMonth,year:Int,mode:PM,export:(SalaryPeriod)->Unit){var all by remember{mutableStateOf(true)}
 Column(Modifier.fillMaxSize().background(CaijiPalette.Background)){SalaryHeader("导出 Excel",back);Column(Modifier.weight(1f).padding(18.dp)){Text("文件格式",color=CaijiPalette.SecondaryText);Text(".xlsx · Sheet1 · 11 列，与工资样例一致",Modifier.padding(top=8.dp));HorizontalDivider(Modifier.padding(vertical=16.dp));Row(verticalAlignment=Alignment.CenterVertically){Text("全部记录（推荐备份）",Modifier.weight(1f));RadioButton(all,{all=true})};Row(verticalAlignment=Alignment.CenterVertically){Text("当前查看期间",Modifier.weight(1f));RadioButton(!all,{all=false})};Text("含所有收入、现金收入和非现金收入合计。",Modifier.padding(top=12.dp),color=CaijiPalette.SecondaryText)};Button(onClick={export(if(all)SalaryPeriod.All else when(mode){PM.MONTH->SalaryPeriod.Month(month.year,month.monthValue);PM.YEAR->SalaryPeriod.Year(year);PM.ALL->SalaryPeriod.All})},Modifier.fillMaxWidth().padding(18.dp)){Text("选择保存位置")}}
}
@Composable private fun SalaryHeader(title:String,back:()->Unit){Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=back,Modifier.width(60.dp)){Text("‹",style=MaterialTheme.typography.headlineSmall)};Text(title,Modifier.weight(1f),textAlign=TextAlign.Center,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Spacer(Modifier.width(60.dp))}}
private fun inFen(f:Long)=BigDecimal.valueOf(f,2).toPlainString()
private fun String.money(negative:Boolean):Long{val x=BigDecimal(trim().ifEmpty{"0"});require(x.scale()<=2||x.stripTrailingZeros().scale()<=2){"金额最多保留两位小数"};if(!negative)require(x.signum()>=0){"金额不能为负数"};return x.movePointRight(2).longValueExact()}
private fun List<String>.sumFen()=fold(BigInteger.ZERO){a,s->a+BigInteger.valueOf(runCatching{s.money(true)}.getOrDefault(0L))}
