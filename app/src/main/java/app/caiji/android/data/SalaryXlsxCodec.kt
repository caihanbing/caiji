package app.caiji.android.data

import org.w3c.dom.Document
import org.w3c.dom.Element
import app.caiji.android.domain.CategoryCatalog
import java.io.*
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Locale
import java.util.zip.*
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import javax.xml.transform.*
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

enum class SalaryImportErrorKind { FILE, APPLICATION, CELL }
data class SalaryImportError(
    val kind: SalaryImportErrorKind,
    val reason: String,
    val suggestion: String,
    val code: String,
    val row: Int? = null,
    val cell: String? = null,
    val field: String? = null,
    val currentValue: String? = null
)
data class SalaryImportPreview(
    val entries: List<SalaryEntry>,
    val errors: List<SalaryImportError>,
    val rowsRead: Int,
    val incomeTypesToCreate: List<SalaryIncomeType>,
    val duplicateCount: Int = 0,
    val incomeTypesToRestore: List<SalaryIncomeType> = emptyList()
)

private class SalaryXlsxProblem(val userReason: String, val userSuggestion: String, val errorCode: String) : IllegalArgumentException()
private class SalaryCellProblem(val userReason: String, val userSuggestion: String) : IllegalArgumentException()

class SalaryXlsxCodec(private val template: () -> InputStream) {
    companion object {
        private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val PACKAGE_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
        private const val MAX_PARTS = 40
        private const val MAX_BYTES = 16 * 1024 * 1024
        private const val MAX_XML = 8 * 1024 * 1024
        private const val MAX_XML_ELEMENTS = 100_000
        private const val MAX_XML_DEPTH = 128
        private const val DOCUMENT_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private val totals = setOf("所有收入合计", "现金收入合计", "非现金收入合计")
        private val headers = listOf("时间", "收入项", "毛收入", "工会费", "养老保险", "医疗保险", "失业保险", "公积金", "企业年金", "税", "纯收入")
    }

    fun importPreview(
        source: InputStream,
        knownTypes: List<SalaryIncomeType>,
        today: LocalDate,
        restoreDefaultDirectory: Boolean = false
    ): SalaryImportPreview {
        val entries = mutableListOf<SalaryEntry>()
        val errors = mutableListOf<SalaryImportError>()
        val typesToCreate = mutableListOf<SalaryIncomeType>()
        val typesToRestore = mutableListOf<SalaryIncomeType>()
        var rowsRead = 0
        try {
            val parts = archive(source)
            parts.filterKeys { it.endsWith(".rels") }.values.forEach { rels ->
                val relationships = xml(rels).getElementsByTagNameNS(PACKAGE_REL, "Relationship")
                for (index in 0 until relationships.length) if ((relationships.item(index) as Element).getAttribute("TargetMode") == "External")
                    throw SalaryXlsxProblem("工作簿包含外部链接", "请在 Excel/WPS 中另存为不含外部链接的 .xlsx 文件后重试。", "IMP-LINK-001")
            }
            val workbook = xml(parts["xl/workbook.xml"] ?: throw SalaryXlsxProblem("文件中缺少工作簿", "请用 Excel/WPS 打开后另存为 .xlsx。", "IMP-BOOK-001"))
            val sheets = workbook.getElementsByTagNameNS(MAIN, "sheet")
            if (sheets.length != 1 || (sheets.item(0) as Element).getAttribute("name") != "Sheet1") {
                throw SalaryXlsxProblem("未找到唯一的 Sheet1 工作表", "请保留样例中的 Sheet1 工作表，再重新导入。", "IMP-SHEET-001")
            }
            val worksheetPart = worksheetPart(parts, sheets.item(0) as Element)
            val shared = parts["xl/sharedStrings.xml"]?.let(::strings).orEmpty()
            val legacyTypes = metadata(parts["docProps/custom.xml"])
            val directoryTypes = directoryMetadata(parts["docProps/custom.xml"])
            val resolvedTypes = resolveIncomeTypes(knownTypes, legacyTypes, directoryTypes, restoreDefaultDirectory)
            typesToCreate += resolvedTypes.toCreate
            typesToRestore += resolvedTypes.toRestore
            val doc = xml(parts[worksheetPart] ?: throw SalaryXlsxProblem("文件中缺少 Sheet1 内容", "请用 Excel/WPS 打开后另存为新的 .xlsx 文件。", "IMP-SHEET-002"))
            val rows = doc.getElementsByTagNameNS(MAIN, "row")
            var headerSeen = false
            for (i in 0 until rows.length) {
                val row = rows.item(i) as Element
                val rowNumber = row.getAttribute("r").toIntOrNull() ?: i + 1
                val cells = row.getElementsByTagNameNS(MAIN, "c")
                val values = linkedMapOf<String, String>()
                for (j in 0 until cells.length) {
                    val cell = cells.item(j) as Element
                    val column = cell.getAttribute("r").takeWhile(Char::isLetter)
                    val raw = cell.getElementsByTagNameNS(MAIN, "v").item(0)?.textContent.orEmpty()
                    values[column] = when (cell.getAttribute("t")) {
                        "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                        "inlineStr" -> (cell.getElementsByTagNameNS(MAIN, "t").item(0)?.textContent).orEmpty()
                        "b" -> if (raw == "1") "1" else "0"
                        else -> raw
                    }
                }
                if (rowNumber == 1) {
                    val headerErrors = validateHeader(row, shared)
                    errors += headerErrors
                    headerSeen = true
                    if (headerErrors.isNotEmpty()) break
                    continue
                }
                val dateRaw = values["A"].orEmpty()
                if (dateRaw.isBlank() && listOf("B", "C", "D", "E", "F", "G", "H", "I", "J").all { values[it].isNullOrBlank() }) continue
                if (dateRaw in totals) continue
                rowsRead++
                parseSalaryRow(rowNumber, values, knownTypes, resolvedTypes.byWorkbookName, today, entries, errors)
            }
            if (!headerSeen) errors += fileError("工作簿中没有表头行", "请检查 Sheet1 的第 1 行是否保留了样例表头。", "IMP-HEADER-001")
            if (rowsRead == 0 && errors.isEmpty()) errors += fileError("工作簿中没有收入明细", "请在样例表头下添加收入记录后重试。", "IMP-EMPTY-001")
        } catch (e: SalaryXlsxProblem) {
            errors += fileError(e.userReason, e.userSuggestion, e.errorCode); entries.clear(); rowsRead = 0
        } catch (e: javax.xml.parsers.ParserConfigurationException) {
            errors += appError("应用无法读取此 Excel 文件", "请重新选择文件；若问题仍然出现，请在设置的反馈中附上错误编号。", "IMP-READ-001"); entries.clear(); rowsRead = 0
        } catch (e: SAXException) {
            errors += fileError("Excel 工作簿 XML 内容损坏", "请用 Excel/WPS 打开文件并另存为新的 .xlsx 后重新选择。", "IMP-XML-001"); entries.clear(); rowsRead = 0
        } catch (e: java.io.IOException) {
            errors += fileError("无法读取 Excel 文件内容", "请用 Excel/WPS 打开文件并另存为 .xlsx 后重新选择。", "IMP-ZIP-001"); entries.clear(); rowsRead = 0
        } catch (e: IllegalArgumentException) {
            errors += fileError("文件不是有效的 .xlsx 工作簿", "请确认文件格式为 Excel 工作簿 .xlsx，而不是重命名后的其他文件。", "IMP-ZIP-001"); entries.clear(); rowsRead = 0
        } catch (e: Exception) {
            errors += appError("应用读取 Excel 时遇到异常", "无需按行修改文件。请重新选择；若仍失败，在设置的反馈中附上编号 IMP-READ-002。", "IMP-READ-002"); entries.clear(); rowsRead = 0
        }
        if (errors.isNotEmpty() && errors.none { it.kind == SalaryImportErrorKind.CELL }) entries.clear()
        if (errors.isNotEmpty()) {
            entries.clear()
            typesToCreate.clear()
            typesToRestore.clear()
        }
        return SalaryImportPreview(entries, errors, rowsRead, typesToCreate, incomeTypesToRestore = typesToRestore)
    }

    private data class ResolvedIncomeTypes(
        val byWorkbookName: Map<String, SalaryIncomeType>,
        val toCreate: List<SalaryIncomeType>,
        val toRestore: List<SalaryIncomeType>
    )

    private fun resolveIncomeTypes(
        known: List<SalaryIncomeType>,
        legacy: List<SalaryIncomeType>,
        directory: List<SalaryIncomeType>,
        restoreDefaultDirectory: Boolean
    ): ResolvedIncomeTypes {
        require(directory.map { it.id }.distinct().size == directory.size) { "工资元数据含重复收入项 ID" }
        require(directory.map { it.name }.distinct().size == directory.size) { "工资元数据含重复收入项名称" }
        val candidates = linkedMapOf<String, SalaryIncomeType>()
        legacy.forEach { candidates[it.id] = it }
        directory.forEach { candidates[it.id] = it }
        require(candidates.values.map { it.name }.distinct().size == candidates.size) { "工资元数据含重复收入项名称" }
        val byWorkbookName = linkedMapOf<String, SalaryIncomeType>()
        val byId = linkedMapOf<String, SalaryIncomeType>()
        val toCreate = mutableListOf<SalaryIncomeType>()
        val toRestore = mutableListOf<SalaryIncomeType>()
        var nextSort = (known.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val restoredBuiltinNames = if (restoreDefaultDirectory) {
            directory.filter { type -> SalaryIncomeTypes.defaults.any { it.id == type.id } }.associate { it.id to it.name }
        } else emptyMap()

        candidates.values.forEach { incoming ->
            val builtIn = SalaryIncomeTypes.defaults.firstOrNull { it.id == incoming.id }
            val localById = known.firstOrNull { it.id == incoming.id }
            val localByName = known.firstOrNull { local ->
                local.name == incoming.name &&
                    !(local.builtIn && restoredBuiltinNames[local.id]?.let { it != local.name } == true)
            }
            val resolved = when {
                builtIn != null -> {
                    require(incoming.isCash == builtIn.isCash) { "工资元数据与内置收入项的现金性质不一致" }
                    val local = localById ?: error("内置收入项 ID 不存在")
                    require(local.isCash == incoming.isCash) { "工资元数据与本机收入项的现金性质不一致" }
                    if (restoreDefaultDirectory) {
                        incoming.copy(builtIn = true, sortOrder = local.sortOrder).also { updated ->
                            if (local.name != updated.name || local.iconKey != updated.iconKey) toRestore += updated
                        }
                    } else local
                }
                localById != null -> {
                    require(localById.isCash == incoming.isCash) { "工资元数据与本机收入项的现金性质不一致" }
                    localById
                }
                localByName != null -> {
                    require(localByName.isCash == incoming.isCash) { "工资元数据与本机收入项的现金性质不一致" }
                    localByName
                }
                else -> incoming.copy(id = incoming.id, builtIn = false, sortOrder = nextSort++).also(toCreate::add)
            }
            byId[incoming.id] = resolved
        }

        (legacy + directory).forEach { incoming ->
            byWorkbookName[incoming.name] = byId[incoming.id] ?: incoming
        }
        return ResolvedIncomeTypes(byWorkbookName, toCreate, toRestore)
    }

    private fun fileError(reason: String, suggestion: String, code: String) =
        SalaryImportError(SalaryImportErrorKind.FILE, reason, suggestion, code)

    private fun appError(reason: String, suggestion: String, code: String) =
        SalaryImportError(SalaryImportErrorKind.APPLICATION, reason, suggestion, code)

    private fun cellError(row: Int, column: String, field: String, value: String?, reason: String, suggestion: String, code: String = "IMP-CELL-001") =
        SalaryImportError(SalaryImportErrorKind.CELL, reason, suggestion, code, row, column + row, field, displayValue(value))

    private fun displayValue(value: String?): String? = value?.takeIf(String::isNotBlank)?.let { if (it.length <= 80) it else it.take(77) + "…" }

    private fun worksheetPart(parts: Map<String, ByteArray>, sheet: Element): String {
        val relationId = sheet.getAttributeNS(DOCUMENT_REL, "id")
        if (relationId.isBlank()) throw SalaryXlsxProblem("Sheet1 没有关联工作表内容", "请用 Excel/WPS 打开工作簿并另存为新的 .xlsx 文件。", "IMP-SHEET-003")
        val relsBytes = parts["xl/_rels/workbook.xml.rels"]
            ?: throw SalaryXlsxProblem("工作簿缺少工作表关系", "请用 Excel/WPS 打开工作簿并另存为新的 .xlsx 文件。", "IMP-SHEET-003")
        val rels = xml(relsBytes).getElementsByTagNameNS(PACKAGE_REL, "Relationship")
        val matches = (0 until rels.length).map { rels.item(it) as Element }.filter { it.getAttribute("Id") == relationId }
        val relation = matches.singleOrNull()
            ?: throw SalaryXlsxProblem("Sheet1 的工作簿关系无效", "请用 Excel/WPS 打开工作簿并另存为新的 .xlsx 文件。", "IMP-SHEET-003")
        if (relation.getAttribute("TargetMode") == "External" || !relation.getAttribute("Type").endsWith("/worksheet")) {
            throw SalaryXlsxProblem("Sheet1 关联的内容不是工作表", "请用 Excel/WPS 打开工作簿并另存为新的 .xlsx 文件。", "IMP-SHEET-003")
        }
        val target = runCatching { URI(relation.getAttribute("Target")) }.getOrNull()
            ?: throw SalaryXlsxProblem("Sheet1 的工作簿关系路径无效", "请用 Excel/WPS 打开工作簿并另存为新的 .xlsx 文件。", "IMP-SHEET-003")
        if (target.isAbsolute || target.rawAuthority != null || target.rawQuery != null || target.rawFragment != null || target.path.isNullOrBlank()) {
            throw SalaryXlsxProblem("Sheet1 的工作簿关系路径无效", "请用 Excel/WPS 打开工作簿并另存为新的 .xlsx 文件。", "IMP-SHEET-003")
        }
        val path = target.path
        val segments = if (path.startsWith("/")) path.removePrefix("/").split('/') else ("xl/" + path).split('/')
        val normalized = mutableListOf<String>()
        for (segment in segments) when (segment) {
            "", "." -> Unit
            ".." -> throw SalaryXlsxProblem("Sheet1 的工作簿关系路径超出文件范围", "请用 Excel/WPS 打开工作簿并另存为新的 .xlsx 文件。", "IMP-SHEET-003")
            else -> normalized += segment
        }
        val resolved = normalized.joinToString("/")
        if (!resolved.startsWith("xl/worksheets/") || !resolved.endsWith(".xml") || resolved.contains('\\')) {
            throw SalaryXlsxProblem("Sheet1 的工作簿关系路径无效", "请用 Excel/WPS 打开工作簿并另存为新的 .xlsx 文件。", "IMP-SHEET-003")
        }
        return resolved
    }

    private fun parseSalaryRow(
        row: Int,
        v: Map<String, String>,
        known: List<SalaryIncomeType>,
        importedTypesByName: Map<String, SalaryIncomeType>,
        today: LocalDate,
        entries: MutableList<SalaryEntry>,
        errors: MutableList<SalaryImportError>
    ) {
        val issues=mutableListOf<SalaryImportError>()
        val labels=mapOf("A" to "时间","B" to "收入项","C" to "毛收入","D" to "工会费","E" to "养老保险","F" to "医疗保险","G" to "失业保险","H" to "公积金","I" to "企业年金","J" to "税","K" to "纯收入")
        val rawDate=v["A"].orEmpty()
        val date=try { parseDate(rawDate).also{if(it>today)throw SalaryCellProblem("日期晚于今天","请填写不晚于今天的到账或发放日期。")}
        } catch(e:Exception) {
            issues+=cellError(row,"A",labels.getValue("A"),rawDate,if(e is SalaryCellProblem)e.userReason else if(rawDate.isBlank())"日期不能为空" else "无法识别此日期",if(e is SalaryCellProblem)e.userSuggestion else "请填写 Excel 日期或 yyyy-MM-dd，例如 2026-11-05。");null
        }
        val name = v["B"].orEmpty().trim()
        val type = importedTypesByName[name]
            ?: SalaryIncomeTypes.defaultForName(name)?.let { default -> known.firstOrNull { it.id == default.id } }
            ?: known.firstOrNull { it.name == name }
        when {
            name.isBlank()->issues+=cellError(row,"B","收入项",v["B"],"收入项不能为空","请选择收入项；自定义项目请先在“设置 → 工资 → 收入项管理”中创建。")
            type==null->issues+=cellError(row,"B","收入项",name,"应用中没有此收入项","在“设置 → 工资 → 收入项管理”中创建同名项目并设置现金属性，再重新导入。")
        }
        val amounts=mutableMapOf<String,Long>()
        listOf("C","D","E","F","G","H","I","J").forEach{col->try{amounts[col]=parseAmount(v[col],labels.getValue(col),col=="J")}catch(e:SalaryCellProblem){issues+=cellError(row,col,labels.getValue(col),v[col],e.userReason,e.userSuggestion)}}
        if(issues.isNotEmpty()||date==null||type==null){errors+=issues;return}
        val now=System.currentTimeMillis()
        val entry=SalaryEntry("import-"+row,date,type.id,amounts.getValue("C"),amounts.getValue("D"),amounts.getValue("E"),amounts.getValue("F"),amounts.getValue("G"),amounts.getValue("H"),amounts.getValue("I"),amounts.getValue("J"),type.isCash,now,now)
        val net=BigInteger.valueOf(entry.grossFen).subtract(entry.deductionsFen)
        if(net.signum()<0){errors+=cellError(row,"K",labels.getValue("K"),v["K"],"扣款合计大于毛收入，纯收入为负数","请核对本行毛收入 C 和扣款 D:J；纯收入必须大于或等于 0。");return}
        try{entry.validate(today)}catch(_:Exception){errors+=cellError(row,"K",labels.getValue("K"),v["K"],"计算后的纯收入超出可处理范围","请检查本行 C:J 的金额和小数点位置。");return}
        val supplied=v["K"]?.takeIf(String::isNotBlank)
        if(supplied!=null)try{if(parseAmount(supplied,labels.getValue("K"),false)!=entry.netIncomeFen){errors+=cellError(row,"K",labels.getValue("K"),supplied,"纯收入与毛收入减扣款的结果不一致","本行纯收入应为 ¥"+yuan(entry.netIncomeFen)+"。请修正 K"+row+"，或检查 C:J 金额。");return}}catch(e:SalaryCellProblem){errors+=cellError(row,"K",labels.getValue("K"),supplied,e.userReason,e.userSuggestion);return}
        entries+=entry
    }

    private fun parseAmount(raw:String?,field:String,negative:Boolean):Long {
        if(raw.isNullOrBlank())return 0L
        val decimal=try{BigDecimal(raw.trim())}catch(_:NumberFormatException){throw SalaryCellProblem("内容不是有效金额","请输入数字，例如 123.45；请去掉币种符号、千位逗号和文字。")}
        if(decimal.scale()>2&&decimal.stripTrailingZeros().scale()>2)throw SalaryCellProblem("金额最多保留两位小数","请将“"+field+"”改为最多两位小数，例如 123.45。")
        val fen=try{decimal.movePointRight(2).longValueExact()}catch(_:ArithmeticException){throw SalaryCellProblem("金额超出可处理范围","请检查“"+field+"”的小数点位置和金额大小。")}
        if(!negative&&fen<0)throw SalaryCellProblem("此金额不能为负数","毛收入和税以外的扣款应为非负数；退税请在“税”列填写负数。")
        return fen
    }

    fun export(output: OutputStream, entries: List<SalaryEntry>, types: List<SalaryIncomeType>) {
        val p=template().use(::archive).toMutableMap()
        val sheet=xml(p["xl/worksheets/sheet1.xml"] ?: error("工资模板无效"))
        val data=sheet.getElementsByTagNameNS(MAIN,"sheetData").item(0) as Element
        val original=(0 until data.childNodes.length).mapNotNull { data.childNodes.item(it) as? Element }
        val h=original.first { it.getAttribute("r")=="1" }.cloneNode(true)
        val detailTemplate=original.first { it.getAttribute("r")=="2" }
        val sumTemplates=(3..5).map { n->original.first { it.getAttribute("r")==n.toString() } }
        val values=p["xl/sharedStrings.xml"]?.let(::strings)?.toMutableList() ?: error("工资模板损坏")
        while(data.firstChild!=null)data.removeChild(data.firstChild)
        data.appendChild(sheet.importNode(h,true))
        val ordered=entries.sortedWith(compareBy<SalaryEntry>{it.occurredOn}.thenBy{it.createdAtMillis}.thenBy{it.id})
        ordered.forEachIndexed { ix,e ->
            e.validate()
            val r=detailTemplate.cloneNode(true) as Element; val rn=ix+2; r.setAttribute("r",rn.toString())
            val vals=mapOf("A" to excelDate(e.occurredOn),"B" to indexShared(values,types.firstOrNull{it.id==e.incomeTypeId}?.name ?: error("收入项不存在")).toString(),
                "C" to yuan(e.grossFen),"D" to yuan(e.unionFen),"E" to yuan(e.pensionFen),"F" to yuan(e.medicalFen),"G" to yuan(e.unemploymentFen),
                "H" to yuan(e.housingFen),"I" to yuan(e.annuityFen),"J" to yuan(e.taxFen),"K" to yuan(e.netIncomeFen))
            cells(r).forEach { c ->
                val col=c.getAttribute("r").takeWhile(Char::isLetter); c.setAttribute("r",col+rn)
                c.getElementsByTagNameNS(MAIN,"f").item(0)?.let(c::removeChild); c.getElementsByTagNameNS(MAIN,"v").item(0)?.let(c::removeChild)
                if(col=="B")c.setAttribute("t","s") else c.removeAttribute("t")
                val v=vals[col]; if(v!=null && (col=="A" || col=="B" || v!="0")){
                    val el=sheet.createElementNS(MAIN,"v");el.textContent=v.toString();c.appendChild(el)
                }
                if(col=="K"){val f=sheet.createElementNS(MAIN,"f");f.textContent="C"+rn+"-SUM(D"+rn+":J"+rn+")";c.insertBefore(f,c.firstChild)}
            }
            val cashFlag=sheet.createElementNS(MAIN,"c");cashFlag.setAttribute("r","N"+rn)
            val cashValue=sheet.createElementNS(MAIN,"v");cashValue.textContent=if(e.isCash)"1" else "0";cashFlag.appendChild(cashValue);r.appendChild(cashFlag)
            data.appendChild(r)
        }
        val last=maxOf(2,ordered.size+1); val sumStart=last+1
        sumTemplates.forEachIndexed { idx,source ->
            val r=source.cloneNode(true) as Element;val rn=sumStart+idx;r.setAttribute("r",rn.toString())
            cells(r).forEach { c ->
                val col=c.getAttribute("r").takeWhile(Char::isLetter);c.setAttribute("r",col+rn)
                if(col in "CDEFGHIJK"){
                    c.getElementsByTagNameNS(MAIN,"f").item(0)?.let(c::removeChild);c.getElementsByTagNameNS(MAIN,"v").item(0)?.let(c::removeChild)
                    val formula=when(idx){
                        0->"SUM("+col+"2:"+col+last+")"
                        1->"SUMIF(N2:N"+last+",\"1\","+col+"2:"+col+last+")"
                        else->col+(rn-2)+"-"+col+(rn-1)
                    }
                    val f=sheet.createElementNS(MAIN,"f");f.textContent=formula;c.appendChild(f)
                    val cached=ordered.filter { idx==0 || (idx==1)==it.isCash }.fold(java.math.BigInteger.ZERO){a,e->a+java.math.BigInteger.valueOf(e.value(col))}
                    val v=sheet.createElementNS(MAIN,"v");v.textContent=yuan(cached);c.appendChild(v)
                }
            };data.appendChild(r)
        }
        val end=sumStart+2
        val merged = sheet.getElementsByTagNameNS(MAIN, "mergeCells").item(0) as? Element
        if (merged != null) {
            val ranges = merged.getElementsByTagNameNS(MAIN, "mergeCell")
            if (ranges.length != sumTemplates.size) error("工资模板合并单元格与合计行不匹配")
            val refPattern = Regex("""^([A-Z]+)\d+:([A-Z]+)\d+$""")
            for (index in 0 until ranges.length) {
                val cell = ranges.item(index) as Element
                val range = refPattern.matchEntire(cell.getAttribute("ref")) ?: error("工资模板合并区域无效")
                val row = sumStart + index
                cell.setAttribute("ref", range.groupValues[1] + row + ":" + range.groupValues[2] + row)
            }
        }
        val typeNames=types.map { it.name }.distinct()
        val sheetRows=(0 until data.childNodes.length).mapNotNull { data.childNodes.item(it) as? Element }.filter { it.localName=="row" }.associateBy { it.getAttribute("r").toInt() }.toMutableMap()
        typeNames.forEachIndexed { index,name ->
            val rowNumber=index+2
            val row=sheetRows[rowNumber] ?: sheet.createElementNS(MAIN,"row").also { it.setAttribute("r",rowNumber.toString());data.appendChild(it);sheetRows[rowNumber]=it }
            val cell=sheet.createElementNS(MAIN,"c");cell.setAttribute("r","M"+rowNumber);cell.setAttribute("t","s")
            val value=sheet.createElementNS(MAIN,"v");value.textContent=indexShared(values,name).toString();cell.appendChild(value);row.appendChild(cell)
        }
        while(data.firstChild!=null)data.removeChild(data.firstChild)
        sheetRows.toSortedMap().values.forEach(data::appendChild)
        val cols=sheet.getElementsByTagNameNS(MAIN,"cols").item(0) as? Element
        if(cols!=null){val hidden=sheet.createElementNS(MAIN,"col");hidden.setAttribute("min","13");hidden.setAttribute("max","14");hidden.setAttribute("width","0");hidden.setAttribute("hidden","1");cols.appendChild(hidden)}
        (sheet.getElementsByTagNameNS(MAIN,"dimension").item(0) as? Element)?.setAttribute("ref","A1:N"+maxOf(end,typeNames.size+1))
        (sheet.getElementsByTagNameNS(MAIN,"autoFilter").item(0) as? Element)?.setAttribute("ref","B1:B"+last)
        (sheet.getElementsByTagNameNS(MAIN,"dataValidation").item(0) as? Element)?.apply { setAttribute("sqref","B2:B"+last);getElementsByTagNameNS(MAIN,"formula1").item(0)?.textContent="SalaryIncomeItems" }
        p["xl/worksheets/sheet1.xml"]=bytes(sheet)
        p["xl/sharedStrings.xml"]=writeStrings(p["xl/sharedStrings.xml"]!!,values)
        p["docProps/custom.xml"]=custom(types)
        p["_rels/.rels"]=relationship(p["_rels/.rels"]!!)
        p["[Content_Types].xml"]=contentType(p["[Content_Types].xml"]!!)
        val wb=xml(p["xl/workbook.xml"]!!)
        val definitions=wb.getElementsByTagNameNS(MAIN,"definedNames").item(0) as? Element ?: wb.createElementNS(MAIN,"definedNames").also { wb.documentElement.appendChild(it) }
        while(definitions.firstChild!=null)definitions.removeChild(definitions.firstChild)
        val defined=wb.createElementNS(MAIN,"definedName");defined.setAttribute("name","SalaryIncomeItems");defined.textContent="Sheet1!\$M\$2:\$M\$"+(typeNames.size+1);definitions.appendChild(defined)
        (wb.getElementsByTagNameNS(MAIN,"calcPr").item(0) as? Element)?.apply{setAttribute("calcMode","auto");setAttribute("fullCalcOnLoad","1");setAttribute("forceFullCalc","1")}
        p["xl/workbook.xml"]=bytes(wb)
        ZipOutputStream(output).use { z->p.forEach{(name,b)->z.putNextEntry(ZipEntry(name));z.write(b);z.closeEntry()} }
    }

    private fun SalaryEntry.value(c:String)=when(c){"C"->grossFen;"D"->unionFen;"E"->pensionFen;"F"->medicalFen;"G"->unemploymentFen;"H"->housingFen;"I"->annuityFen;"J"->taxFen;else->netIncomeFen}
    private fun cells(e:Element)=(0 until e.childNodes.length).mapNotNull{e.childNodes.item(it) as? Element}.filter{it.localName=="c"}
    private fun yuan(fen:Long)=BigDecimal.valueOf(fen,2).toPlainString()
    private fun yuan(fen:java.math.BigInteger)=BigDecimal(fen,2).toPlainString()
    private fun indexShared(a:MutableList<String>,v:String):Int {val n=a.indexOf(v);if(n>=0)return n;a+=v;return a.lastIndex}
    private fun amount(s:String?):Long {val n=s?.takeIf(String::isNotBlank)?.let(::BigDecimal)?:BigDecimal.ZERO;require(n.scale()<=2||n.stripTrailingZeros().scale()<=2){"金额最多保留两位小数"};return n.movePointRight(2).longValueExact()}
    private fun parseDate(s:String):LocalDate {runCatching{return LocalDate.parse(s)};val n=s.toDoubleOrNull()?:error("日期格式无效");require(n>=1&&n<2958466){"日期超出 Excel 范围"};return LocalDate.of(1899,12,30).plusDays(n.toLong())}
    private fun excelDate(d:LocalDate)=ChronoUnit.DAYS.between(LocalDate.of(1899,12,30),d)
    private fun validateHeader(row: Element, shared: List<String>): List<SalaryImportError> {
        val expectedColumns = headers.indices.map { ('A'.code + it).toChar().toString() }
        val byColumn = cells(row).associateBy { it.getAttribute("r").takeWhile(Char::isLetter) }
        val errors = mutableListOf<SalaryImportError>()
        headers.forEachIndexed { index, expected ->
            val column = expectedColumns[index]
            val cell = byColumn[column]
            val value = cell?.let { c ->
                val raw = c.getElementsByTagNameNS(MAIN, "v").item(0)?.textContent.orEmpty()
                if (c.getAttribute("t") == "inlineStr") c.getElementsByTagNameNS(MAIN, "t").item(0)?.textContent
                else shared.getOrNull(raw.toIntOrNull() ?: -1)
            }
            if (value != expected) errors += cellError(1, column, "表头", value ?: "缺失", "列标题与样例不一致",
                "请将 " + column + "1 改为“" + expected + "”，并保持样例的 11 列顺序。", "IMP-HEADER-002")
        }
        if (byColumn.keys.any { it !in expectedColumns }) errors += fileError("Sheet1 列数与工资样例不一致", "请仅保留样例的 A:K 这 11 列。", "IMP-HEADER-003")
        return errors
    }
    private fun readZip(input:InputStream):Map<String,ByteArray>{val out=linkedMapOf<String,ByteArray>();var size=0;var count=0;ZipInputStream(input).use{z->while(true){val e=z.nextEntry?:break;require(++count<=MAX_PARTS){"Excel 文件部件过多"};if(e.isDirectory) { z.closeEntry(); continue };require(!e.name.startsWith("/")&&!e.name.split("/").contains("..")&&!e.name.contains("\\")){"文件路径无效"};require(!e.name.contains("vbaProject")&&!e.name.startsWith("xl/externalLinks/")&&!e.name.startsWith("xl/embeddings/")){"不支持宏、外部链接或嵌入内容"};require(!out.containsKey(e.name)){"Excel 文件含重复部件"};val b=ByteArrayOutputStream();val buf=ByteArray(8192);var nsum=0;while(true){val n=z.read(buf);if(n<0)break;nsum+=n;size+=n;require(nsum<=MAX_XML&&size<=MAX_BYTES){"Excel 文件超过安全读取上限"};b.write(buf,0,n)};out[e.name]=b.toByteArray()}};if(!out.containsKey("xl/workbook.xml"))throw SalaryXlsxProblem("未找到 Excel 工作簿部件","请确认文件是有效的 .xlsx 工作簿，并用 Excel/WPS 重新另存。","IMP-ZIP-001");return out}
    private fun archive(input:InputStream)=readZip(input)
    private fun xml(bytes: ByteArray): Document {
        val text = decodeXml(bytes)
        rejectXmlDeclarations(text)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, systemId ->
            if (!systemId.isNullOrBlank()) throw SAXException("External XML resources are blocked")
            null
        }
        return builder.parse(InputSource(StringReader(text)))
    }

    private fun decodeXml(bytes: ByteArray): String {
        if (bytes.size > MAX_XML) throw SalaryXlsxProblem("XML 数据超出安全读取范围", "请用 Excel/WPS 另存为 .xlsx 后重试。", "IMP-XML-SIZE-001")
        var offset = 0
        val charset = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> { offset = 3; StandardCharsets.UTF_8 }
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> { offset = 2; StandardCharsets.UTF_16BE }
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> { offset = 2; StandardCharsets.UTF_16LE }
            bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == '<'.code.toByte() -> StandardCharsets.UTF_16BE
            bytes.size >= 4 && bytes[0] == '<'.code.toByte() && bytes[1] == 0.toByte() -> StandardCharsets.UTF_16LE
            else -> StandardCharsets.UTF_8
        }
        val text = try {
            charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString().removePrefix("\uFEFF")
        } catch (_: Exception) {
            throw SalaryXlsxProblem("XML 文本编码无法读取", "请用 Excel/WPS 另存为 UTF-8 或 UTF-16 编码的 .xlsx 文件。", "IMP-XML-ENC-001")
        }
        val declared = Regex("""^\s*<\?xml[^?]*encoding\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.uppercase(Locale.ROOT)
        val compatible = when (declared) {
            null -> true
            "UTF-8", "UTF8" -> charset == StandardCharsets.UTF_8
            "UTF-16" -> charset == StandardCharsets.UTF_16BE || charset == StandardCharsets.UTF_16LE
            "UTF-16BE" -> charset == StandardCharsets.UTF_16BE
            "UTF-16LE" -> charset == StandardCharsets.UTF_16LE
            else -> false
        }
        if (!compatible) throw SalaryXlsxProblem("XML 声明的字符编码不受支持", "请用 Excel/WPS 另存为 UTF-8 编码的 .xlsx 文件。", "IMP-XML-ENC-002")
        return text
    }

    private fun rejectXmlDeclarations(text: String) {
        var index = 0
        var elementCount = 0
        var depth = 0
        while (index < text.length) {
            val terminator = when {
                text.startsWith("<!--", index) -> "-->"
                text.startsWith("<![CDATA[", index) -> "]]>"
                text.startsWith("<?", index) -> "?>"
                else -> null
            }
            if (terminator != null) {
                val end = text.indexOf(terminator, index + 2)
                if (end < 0) return
                index = end + terminator.length
                continue
            }
            if (text.regionMatches(index, "<!DOCTYPE", 0, 9, ignoreCase = true) ||
                text.regionMatches(index, "<!ENTITY", 0, 8, ignoreCase = true)) {
                throw SalaryXlsxProblem("工作簿包含不支持的 XML 声明", "请用 Excel/WPS 另存为不含 DTD 或自定义实体的 .xlsx 文件。", "IMP-XML-SEC-001")
            }
            if (text[index] == '<') {
                var cursor = index + 1
                var quote: Char? = null
                while (cursor < text.length) {
                    val character = text[cursor]
                    if (quote == null && (character == '\'' || character == '"')) quote = character
                    else if (quote == character) quote = null
                    else if (quote == null && character == '>') break
                    cursor++
                }
                if (cursor >= text.length) return
                val tag = text.substring(index + 1, cursor).trim()
                when {
                    tag.startsWith("/") -> depth = (depth - 1).coerceAtLeast(0)
                    tag.startsWith("!") || tag.startsWith("?") || tag.isEmpty() -> Unit
                    else -> {
                        if (++elementCount > MAX_XML_ELEMENTS) {
                            throw SalaryXlsxProblem("工作簿 XML 结构超过安全读取上限", "请删除多余空行、空白单元格和无用格式后另存；较大的工资文件可按年份拆分。", "IMP-XML-SIZE-002")
                        }
                        if (!tag.endsWith("/")) {
                            if (++depth > MAX_XML_DEPTH) {
                                throw SalaryXlsxProblem("工作簿 XML 嵌套层级超过安全读取上限", "请用 Excel/WPS 打开文件并另存为新的 .xlsx 工作簿。", "IMP-XML-SIZE-003")
                            }
                        }
                    }
                }
                index = cursor + 1
                continue
            }
            index++
        }
    }
    private fun bytes(d:Document):ByteArray{val t=TransformerFactory.newInstance().apply{runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") };runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "") }}.newTransformer();val o=ByteArrayOutputStream();t.setOutputProperty(OutputKeys.ENCODING,"UTF-8");t.transform(DOMSource(d),StreamResult(o));return o.toByteArray()}
    private fun strings(b:ByteArray):List<String>{val d=xml(b);val n=d.getElementsByTagNameNS(MAIN,"si");return (0 until n.length).map{i->val t=(n.item(i) as Element).getElementsByTagNameNS(MAIN,"t");(0 until t.length).joinToString(""){j->t.item(j).textContent}}}
    private fun writeStrings(b:ByteArray,values:List<String>):ByteArray{val d=xml(b);val r=d.documentElement;while(r.firstChild!=null)r.removeChild(r.firstChild);values.forEach{s->val i=d.createElementNS(MAIN,"si");val t=d.createElementNS(MAIN,"t");t.textContent=s;i.appendChild(t);r.appendChild(i)};r.setAttribute("count",values.size.toString());r.setAttribute("uniqueCount",values.size.toString());return bytes(d)}
    private fun custom(types: List<SalaryIncomeType>): ByteArray {
        val d = xml("""<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/custom-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"/>""".toByteArray())
        val properties = d.documentElement
        fun add(name: String, pid: String, value: String) {
            val property = d.createElementNS("http://schemas.openxmlformats.org/officeDocument/2006/custom-properties", "property")
            property.setAttribute("fmtid", "{D5CDD505-2E9C-101B-9397-08002B2CF9AE}")
            property.setAttribute("pid", pid)
            property.setAttribute("name", name)
            val text = d.createElementNS("http://schemas.openxmlformats.org/officeDocument/2006/custom-properties", "vt:lpwstr")
            text.textContent = value
            property.appendChild(text)
            properties.appendChild(property)
        }
        add("CaijiSalaryIncomeTypes", "2", types.filterNot { it.builtIn }.joinToString("\n") {
            it.id + "|" + Base64.getEncoder().encodeToString(it.name.toByteArray()) + "|" + if (it.isCash) "1" else "0"
        })
        val directory = "2\n" + types.sortedBy { it.sortOrder }.joinToString("\n") {
            it.id + "|" + Base64.getEncoder().encodeToString(it.name.toByteArray()) + "|" +
                (if (it.isCash) "1" else "0") + "|" + Base64.getEncoder().encodeToString(it.iconKey.toByteArray())
        }
        add("CaijiSalaryIncomeDirectoryV2", "3", directory)
        return bytes(d)
    }
    private fun metadata(b:ByteArray?):List<SalaryIncomeType>{
        if(b==null)return emptyList()
        val d=xml(b);val n=d.getElementsByTagNameNS("http://schemas.openxmlformats.org/officeDocument/2006/custom-properties","property")
        val x=(0 until n.length).map{n.item(it) as Element}.firstOrNull{it.getAttribute("name")=="CaijiSalaryIncomeTypes"}?.textContent.orEmpty()
        return x.lineSequence().filter(String::isNotBlank).map{row->
            val p=row.split("|");require(p.size==3){"工资元数据损坏"}
            val name=String(Base64.getDecoder().decode(p[1]),Charsets.UTF_8)
            require(p[0].isNotBlank()&&p[0].length<=128&&name.isNotBlank()&&name==name.trim()&&name.length<=100&&p[2] in setOf("0","1")){"工资元数据损坏"}
            SalaryIncomeType(p[0],name,p[2]=="1",false)
        }.toList()
    }

    private fun directoryMetadata(b: ByteArray?): List<SalaryIncomeType> {
        if (b == null) return emptyList()
        val properties = xml(b).getElementsByTagNameNS(
            "http://schemas.openxmlformats.org/officeDocument/2006/custom-properties", "property"
        )
        val value = (0 until properties.length).map { properties.item(it) as Element }
            .firstOrNull { it.getAttribute("name") == "CaijiSalaryIncomeDirectoryV2" }
            ?.textContent.orEmpty()
        if (value.isBlank()) return emptyList()
        val lines = value.lineSequence().toList()
        require(lines.firstOrNull() == "2") { "工资目录元数据版本不受支持" }
        return lines.drop(1).filter(String::isNotBlank).map { line ->
            val fields = line.split('|')
            require(fields.size == 4) { "工资目录元数据损坏" }
            val id = fields[0]
            val name = String(Base64.getDecoder().decode(fields[1]), Charsets.UTF_8)
            val cashFlag = fields[2]
            require(cashFlag in setOf("0", "1")) { "工资目录元数据损坏" }
            val isCash = cashFlag == "1"
            val savedIcon = String(Base64.getDecoder().decode(fields[3]), Charsets.UTF_8)
            require(id.isNotBlank() && id.length <= 128 && name.isNotBlank() && name == name.trim() && name.length <= 100) {
                "工资目录元数据损坏"
            }
            val builtIn = SalaryIncomeTypes.defaults.firstOrNull { it.id == id }
            if (builtIn != null) require(builtIn.isCash == isCash) { "工资元数据与内置收入项的现金性质不一致" }
            SalaryIncomeType(
                id = id,
                name = name,
                isCash = isCash,
                builtIn = builtIn != null,
                sortOrder = builtIn?.sortOrder ?: 0,
                iconKey = if (CategoryCatalog.isAllowedIcon(savedIcon)) savedIcon else "other"
            )
        }
    }
    private fun relationship(b:ByteArray):ByteArray{val d=xml(b);val r=d.documentElement;val n=(0 until r.childNodes.length).mapNotNull{r.childNodes.item(it) as? Element};if(n.none{it.getAttribute("Type").endsWith("/custom-properties")}){val e=d.createElementNS(r.namespaceURI,"Relationship");e.setAttribute("Id","rIdSalaryCustom");e.setAttribute("Type","http://schemas.openxmlformats.org/officeDocument/2006/relationships/custom-properties");e.setAttribute("Target","docProps/custom.xml");r.appendChild(e)};return bytes(d)}
    private fun contentType(b:ByteArray):ByteArray{val d=xml(b);val r=d.documentElement;val n=(0 until r.childNodes.length).mapNotNull{r.childNodes.item(it) as? Element};if(n.none{it.getAttribute("PartName")=="/docProps/custom.xml"}){val e=d.createElementNS(r.namespaceURI,"Override");e.setAttribute("PartName","/docProps/custom.xml");e.setAttribute("ContentType","application/vnd.openxmlformats-officedocument.custom-properties+xml");r.appendChild(e)};return bytes(d)}
}
