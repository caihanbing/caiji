package app.caiji.android.data

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.zip.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.*
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class SalaryImportError(val row: Int, val message: String)
data class SalaryImportPreview(val entries: List<SalaryEntry>, val errors: List<SalaryImportError>, val rowsRead: Int, val incomeTypesToCreate: List<SalaryIncomeType>, val duplicateCount: Int = 0)

class SalaryXlsxCodec(private val template: () -> InputStream) {
    companion object {
        private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val MAX_PARTS = 40
        private const val MAX_BYTES = 16 * 1024 * 1024
        private const val MAX_XML = 8 * 1024 * 1024
        private val totals = setOf("所有收入合计", "现金收入合计", "非现金收入合计")
        private val headers = listOf("时间", "收入项", "毛收入", "工会费", "养老保险", "医疗保险", "失业保险", "公积金", "企业年金", "税", "纯收入")
    }

    fun importPreview(source: InputStream, knownTypes: List<SalaryIncomeType>, today: LocalDate): SalaryImportPreview = try {
        val parts = archive(source)
        parts.filterKeys { it.endsWith(".rels") }.values.forEach { rels ->
            val relationships = xml(rels).getElementsByTagNameNS("http://schemas.openxmlformats.org/package/2006/relationships", "Relationship")
            for (index in 0 until relationships.length) require((relationships.item(index) as Element).getAttribute("TargetMode") != "External") { "不支持含外部链接的 Excel" }
        }
        val workbook = xml(parts["xl/workbook.xml"] ?: error("缺少工作簿"))
        val sheets = workbook.getElementsByTagNameNS(MAIN, "sheet")
        require(sheets.length == 1 && (sheets.item(0) as Element).getAttribute("name") == "Sheet1") { "工作簿必须仅包含 Sheet1 工作表" }
        val strings = parts["xl/sharedStrings.xml"]?.let(::strings).orEmpty()
        val metadata = metadata(parts["docProps/custom.xml"])
        val doc = xml(parts["xl/worksheets/sheet1.xml"] ?: error("缺少工作表"))
        val rows = doc.getElementsByTagNameNS(MAIN, "row")
        val entries = mutableListOf<SalaryEntry>(); val errors = mutableListOf<SalaryImportError>()
        val typesToCreate = mutableListOf<SalaryIncomeType>()
        val byName = knownTypes.associateBy { it.name }; val metaByName = metadata.associateBy { it.name }
        var rowsRead = 0
        var headerSeen = false
        for (i in 0 until rows.length) {
            val row = rows.item(i) as Element
            val rn = row.getAttribute("r").toIntOrNull() ?: i + 1
            if (rn == 1) { validateHeader(row, strings); headerSeen = true; continue }
            val cells = row.getElementsByTagNameNS(MAIN, "c"); val values = mutableMapOf<String,String>()
            for (j in 0 until cells.length) {
                val c = cells.item(j) as Element; val col = c.getAttribute("r").takeWhile(Char::isLetter)
                val raw = c.getElementsByTagNameNS(MAIN,"v").item(0)?.textContent.orEmpty()
                values[col] = when(c.getAttribute("t")) {
                    "s" -> strings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                    "inlineStr" -> (c.getElementsByTagNameNS(MAIN,"t").item(0)?.textContent).orEmpty()
                    "b" -> if(raw=="1") "1" else "0"
                    else -> raw
                }
            }
            val dateRaw=values["A"].orEmpty()
            if (dateRaw.isBlank() && listOf("B","C","D","E","F","G","H","I","J").all { values[it].isNullOrBlank() }) continue
            if (dateRaw in totals) continue
            rowsRead++
            try {
                val date=parseDate(dateRaw); val name=values["B"].orEmpty().trim()
                require(name.isNotEmpty()) { "收入项不能为空" }
                val meta=metaByName[name]
                val type=byName[name] ?: meta?.let { m ->
                    typesToCreate.firstOrNull { it.name==name } ?: SalaryIncomeType(m.id,m.name,m.isCash,false,knownTypes.size+typesToCreate.size).also(typesToCreate::add)
                } ?: error("未知收入项“" + name + "”，请先创建收入项后重新导入")
                if (meta!=null) require(type.isCash==meta.isCash) { "收入项现金性质与本机设置不一致" }
                val gross=amount(values["C"]); val union=amount(values["D"]); val pension=amount(values["E"])
                val medical=amount(values["F"]); val unemp=amount(values["G"]); val house=amount(values["H"])
                val annuity=amount(values["I"]); val tax=amount(values["J"])
                val now=System.currentTimeMillis()
                val entry=SalaryEntry("import-" + rn,date,type.id,gross,union,pension,medical,unemp,house,annuity,tax,type.isCash,now,now)
                entry.validate(today)
                values["K"]?.takeIf(String::isNotBlank)?.let { require(amount(it)==entry.netIncomeFen) { "纯收入与毛收入减扣款不一致" } }
                entries+=entry
            } catch(e:Exception) { errors+=SalaryImportError(rn,e.message ?: "数据无效") }
        }
        require(headerSeen) { "Excel 表头与样例不一致" }
        require(rowsRead>0 || errors.isNotEmpty()) { "工作簿没有可导入的收入记录" }
        SalaryImportPreview(entries,errors,rowsRead,typesToCreate)
    } catch(e:Exception) { SalaryImportPreview(emptyList(),listOf(SalaryImportError(0,e.message ?: "无法读取 Excel 文件")),0,emptyList()) }

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
                        else->"C"+rn+"-C"+(rn-1)
                    }
                    val f=sheet.createElementNS(MAIN,"f");f.textContent=formula;c.appendChild(f)
                    val cached=ordered.filter { idx==0 || (idx==1)==it.isCash }.fold(java.math.BigInteger.ZERO){a,e->a+java.math.BigInteger.valueOf(e.value(col))}
                    val v=sheet.createElementNS(MAIN,"v");v.textContent=yuan(cached);c.appendChild(v)
                }
            };data.appendChild(r)
        }
        val end=sumStart+2
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
        p["docProps/custom.xml"]=custom(types.filterNot{it.builtIn})
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
    private fun validateHeader(r:Element,ss:List<String>){val c=cells(r).filter { it.getAttribute("r").takeWhile(Char::isLetter) in headers.indices.map { index -> ('A'.code + index).toChar().toString() } };require(c.size==headers.size){"Excel 列数与样例不一致"};headers.forEachIndexed{i,h->val v=c[i].getElementsByTagNameNS(MAIN,"v").item(0)?.textContent.orEmpty();val actual=if(c[i].getAttribute("t")=="inlineStr")c[i].getElementsByTagNameNS(MAIN,"t").item(0)?.textContent else ss.getOrNull(v.toIntOrNull()?:-1);require(actual==h){"Excel 表头与样例不一致"}}}
    private fun readZip(input:InputStream):Map<String,ByteArray>{val out=linkedMapOf<String,ByteArray>();var size=0;var count=0;ZipInputStream(input).use{z->while(true){val e=z.nextEntry?:break;require(++count<=MAX_PARTS){"Excel 文件部件过多"};if(e.isDirectory) { z.closeEntry(); continue };require(!e.name.startsWith("/")&&!e.name.split("/").contains("..")&&!e.name.contains("\\")){"文件路径无效"};require(!e.name.contains("vbaProject")&&!e.name.startsWith("xl/externalLinks/")&&!e.name.startsWith("xl/embeddings/")){"不支持宏、外部链接或嵌入内容"};require(!out.containsKey(e.name)){"Excel 文件含重复部件"};val b=ByteArrayOutputStream();val buf=ByteArray(8192);var nsum=0;while(true){val n=z.read(buf);if(n<0)break;nsum+=n;size+=n;require(nsum<=MAX_XML&&size<=MAX_BYTES){"Excel 文件超过安全读取上限"};b.write(buf,0,n)};out[e.name]=b.toByteArray()}};require(out.containsKey("xl/workbook.xml")&&out.containsKey("xl/worksheets/sheet1.xml")){"文件不是有效的 XLSX"};return out}
    private fun archive(input:InputStream)=readZip(input)
    private fun factory()=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true;setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);setFeature("http://xml.org/sax/features/external-general-entities",false);setFeature("http://xml.org/sax/features/external-parameter-entities",false);runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") };runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }}
    private fun xml(b:ByteArray)=factory().newDocumentBuilder().parse(ByteArrayInputStream(b))
    private fun bytes(d:Document):ByteArray{val t=TransformerFactory.newInstance().apply{runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") };runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "") }}.newTransformer();val o=ByteArrayOutputStream();t.setOutputProperty(OutputKeys.ENCODING,"UTF-8");t.transform(DOMSource(d),StreamResult(o));return o.toByteArray()}
    private fun strings(b:ByteArray):List<String>{val d=xml(b);val n=d.getElementsByTagNameNS(MAIN,"si");return (0 until n.length).map{i->val t=(n.item(i) as Element).getElementsByTagNameNS(MAIN,"t");(0 until t.length).joinToString(""){j->t.item(j).textContent}}}
    private fun writeStrings(b:ByteArray,values:List<String>):ByteArray{val d=xml(b);val r=d.documentElement;while(r.firstChild!=null)r.removeChild(r.firstChild);values.forEach{s->val i=d.createElementNS(MAIN,"si");val t=d.createElementNS(MAIN,"t");t.textContent=s;i.appendChild(t);r.appendChild(i)};r.setAttribute("count",values.size.toString());r.setAttribute("uniqueCount",values.size.toString());return bytes(d)}
    private fun custom(types:List<SalaryIncomeType>):ByteArray{val d=xml("""<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/custom-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes"/>""".toByteArray());val p=d.createElementNS("http://schemas.openxmlformats.org/officeDocument/2006/custom-properties","property");p.setAttribute("fmtid","{D5CDD505-2E9C-101B-9397-08002B2CF9AE}");p.setAttribute("pid","2");p.setAttribute("name","CaijiSalaryIncomeTypes");val v=d.createElementNS("http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes","vt:lpwstr");v.textContent=types.joinToString("\n"){it.id+"|"+Base64.getEncoder().encodeToString(it.name.toByteArray())+"|"+if(it.isCash)"1" else "0"};p.appendChild(v);d.documentElement.appendChild(p);return bytes(d)}
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
    private fun relationship(b:ByteArray):ByteArray{val d=xml(b);val r=d.documentElement;val n=(0 until r.childNodes.length).mapNotNull{r.childNodes.item(it) as? Element};if(n.none{it.getAttribute("Type").endsWith("/custom-properties")}){val e=d.createElementNS(r.namespaceURI,"Relationship");e.setAttribute("Id","rIdSalaryCustom");e.setAttribute("Type","http://schemas.openxmlformats.org/officeDocument/2006/relationships/custom-properties");e.setAttribute("Target","docProps/custom.xml");r.appendChild(e)};return bytes(d)}
    private fun contentType(b:ByteArray):ByteArray{val d=xml(b);val r=d.documentElement;val n=(0 until r.childNodes.length).mapNotNull{r.childNodes.item(it) as? Element};if(n.none{it.getAttribute("PartName")=="/docProps/custom.xml"}){val e=d.createElementNS(r.namespaceURI,"Override");e.setAttribute("PartName","/docProps/custom.xml");e.setAttribute("ContentType","application/vnd.openxmlformats-officedocument.custom-properties+xml");r.appendChild(e)};return bytes(d)}
}
