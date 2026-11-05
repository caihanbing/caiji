package app.caiji.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class SalaryXlsxCodecTest {
    @Test fun parsesSampleColumnsAndComputesNetUsingNegativeTax() {
        val codec = SalaryXlsxCodec { javaClass.classLoader!!.getResourceAsStream("salary-template.xlsx")!! }
        val result = codec.importPreview(ByteArrayInputStream(fixture()), SalaryIncomeTypes.defaults, LocalDate.of(2026, 11, 4))
        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.entries.size)
        assertEquals("builtin-17", result.entries.single().incomeTypeId)
        assertEquals(11_000L, result.entries.single().netIncomeFen)
    }

    @Test fun exportThenImportPreservesIncomeTypesAndRejectsRepeatedDuplicates() {
        val entry = SalaryEntry("one", LocalDate.of(2026, 11, 1), "builtin-1", 100_00, taxFen = 0,
            isCash = true, createdAtMillis = 1, updatedAtMillis = 1)
        val codec = SalaryXlsxCodec { javaClass.classLoader!!.getResourceAsStream("salary-template.xlsx")!! }
        val file = ByteArrayOutputStream()
        codec.export(file, listOf(entry), SalaryIncomeTypes.defaults)
        val bytes = file.toByteArray()
        val sheetXml = java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var found = ""
            while (true) { val item = zip.nextEntry ?: break; if (item.name == "xl/worksheets/sheet1.xml") found = zip.readBytes().toString(Charsets.UTF_8) }
            found
        }
        assertTrue(sheetXml.contains("C2-SUM(D2:J2)"))
        assertTrue(sheetXml.contains("SUMIF(N2:N2,\"1\",C2:C2)"))
        assertTrue(sheetXml.contains("ref=\"A3:B3\""))
        assertTrue(sheetXml.contains("ref=\"A4:B4\""))
        assertTrue(sheetXml.contains("ref=\"A5:B5\""))
        val result = codec.importPreview(ByteArrayInputStream(bytes), SalaryIncomeTypes.defaults, LocalDate.of(2026, 11, 4))
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(listOf(entry.duplicateKey()), result.entries.map { it.duplicateKey() })
        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.rowsRead)
    }

    @Test fun nonCashTotalsSubtractTheCashTotalFromTheSameColumn() {
        val cashType = SalaryIncomeTypes.defaults.first { it.isCash }
        val nonCashType = SalaryIncomeTypes.defaults.first { !it.isCash }
        val date = LocalDate.of(2026, 11, 1)
        val entries = listOf(
            SalaryEntry("cash", date, cashType.id, 10_000, unionFen = 100, pensionFen = 200, medicalFen = 50,
                unemploymentFen = 0, housingFen = 300, annuityFen = 150, taxFen = 100, isCash = true,
                createdAtMillis = 1, updatedAtMillis = 1),
            SalaryEntry("noncash", date, nonCashType.id, 20_000, unionFen = 200, pensionFen = 300, medicalFen = 100,
                unemploymentFen = 50, housingFen = 400, annuityFen = 250, taxFen = -100, isCash = false,
                createdAtMillis = 2, updatedAtMillis = 2)
        )
        val codec = SalaryXlsxCodec { javaClass.classLoader!!.getResourceAsStream("salary-template.xlsx")!! }
        val output = ByteArrayOutputStream()
        codec.export(output, entries, SalaryIncomeTypes.defaults)
        val sheetXml = java.util.zip.ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            var sheet = byteArrayOf()
            while (true) {
                val part = zip.nextEntry ?: break
                if (part.name == "xl/worksheets/sheet1.xml") sheet = zip.readBytes()
            }
            sheet.toString(Charsets.UTF_8)
        }
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(ByteArrayInputStream(sheetXml.toByteArray()))
        val cells = doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "c")
        val byReference = (0 until cells.length).map { cells.item(it) as Element }.associateBy { it.getAttribute("r") }
        val expectedFen = mapOf(
            "C" to listOf(30_000L, 10_000L, 20_000L),
            "D" to listOf(300L, 100L, 200L),
            "E" to listOf(500L, 200L, 300L),
            "F" to listOf(150L, 50L, 100L),
            "G" to listOf(50L, 0L, 50L),
            "H" to listOf(700L, 300L, 400L),
            "I" to listOf(400L, 150L, 250L),
            "J" to listOf(0L, 100L, -100L),
            "K" to listOf(27_900L, 9_100L, 18_800L)
        )
        expectedFen.forEach { (column, fenValues) ->
            val rowFormulas = listOf(
                "SUM(" + column + "2:" + column + "3)",
                "SUMIF(N2:N3,\"1\"," + column + "2:" + column + "3)",
                column + "4-" + column + "5"
            )
            (4..6).forEachIndexed { index, row ->
                val cell = byReference.getValue(column + row)
                assertEquals(rowFormulas[index], cell.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "f").item(0).textContent)
                assertEquals(BigDecimal.valueOf(fenValues[index], 2).toPlainString(), cell.getElementsByTagNameNS("http://schemas.openxmlformats.org/spreadsheetml/2006/main", "v").item(0).textContent)
            }
        }
    }

    @Test fun customIncomeTypesRoundTripAndRemainInTheHiddenDropdownSource() {
        val custom = SalaryIncomeType("custom-01", "远程*工作?补贴", true)
        val entry = SalaryEntry("one", LocalDate.of(2026, 11, 1), custom.id, 12500, isCash = true, createdAtMillis = 1, updatedAtMillis = 1)
        val codec = SalaryXlsxCodec { javaClass.classLoader!!.getResourceAsStream("salary-template.xlsx")!! }
        val output = ByteArrayOutputStream()
        codec.export(output, listOf(entry), SalaryIncomeTypes.defaults + custom)
        val parts = mutableMapOf<String,String>()
        java.util.zip.ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) { val item=zip.nextEntry?:break; if(item.name in setOf("xl/workbook.xml","xl/worksheets/sheet1.xml")) parts[item.name]=zip.readBytes().toString(Charsets.UTF_8) }
        }
        assertTrue(parts.getValue("xl/workbook.xml").contains("SalaryIncomeItems"))
        assertTrue(parts.getValue("xl/worksheets/sheet1.xml").contains("SalaryIncomeItems"))
        assertTrue(parts.getValue("xl/worksheets/sheet1.xml").contains("SUMIF(N2:N2,\"1\",C2:C2)"))
        assertTrue(parts.getValue("xl/worksheets/sheet1.xml").contains("hidden=\"1\""))
        val preview=codec.importPreview(ByteArrayInputStream(output.toByteArray()),SalaryIncomeTypes.defaults,LocalDate.of(2026,11,4))
        assertTrue(preview.errors.toString(),preview.errors.isEmpty())
        val importedType=preview.incomeTypesToCreate.single()
        assertEquals(custom.id,importedType.id)
        assertEquals(custom.name,importedType.name)
        assertEquals(custom.isCash,importedType.isCash)
        assertEquals(custom.id,preview.entries.single().incomeTypeId)
    }

    @Test fun reportsEachBadCellWithoutProducingDependentNetErrors() {
        val invalidDateAndGross = replaceSheet(fixture()) { it.replace("<v>46327</v>", "<v>yesterday</v>").replace("<v>100</v>", "<v>12x</v>") }
        val codec = SalaryXlsxCodec { javaClass.classLoader!!.getResourceAsStream("salary-template.xlsx")!! }
        val result = codec.importPreview(ByteArrayInputStream(invalidDateAndGross), SalaryIncomeTypes.defaults, LocalDate.of(2026, 11, 4))
        assertEquals(setOf("A2", "C2"), result.errors.mapNotNull { it.cell }.toSet())
        assertTrue(result.errors.none { it.cell == "K2" })
        assertTrue(result.entries.isEmpty())
    }

    @Test fun pointsPureIncomeMismatchToTheNetCellWithExpectedAmount() {
        val wrongNet = replaceSheet(fixture()) { it.replace("<v>110</v>", "<v>90</v>") }
        val codec = SalaryXlsxCodec { javaClass.classLoader!!.getResourceAsStream("salary-template.xlsx")!! }
        val result = codec.importPreview(ByteArrayInputStream(wrongNet), SalaryIncomeTypes.defaults, LocalDate.of(2026, 11, 4))
        val issue = result.errors.single()
        assertEquals("K2", issue.cell)
        assertTrue(issue.suggestion.contains("110.00"))
    }

    @Test fun rejectsDoctypeDeclarationsWithAFileLevelAction() {
        val dtd = replaceSheet(fixture()) { it.replace("<worksheet", "<!DOCTYPE worksheet [<!ENTITY xxe SYSTEM \"file:///secret\">]><worksheet") }
        val codec = SalaryXlsxCodec { javaClass.classLoader!!.getResourceAsStream("salary-template.xlsx")!! }
        val result = codec.importPreview(ByteArrayInputStream(dtd), SalaryIncomeTypes.defaults, LocalDate.of(2026, 11, 4))
        assertTrue(result.entries.isEmpty())
        assertEquals(SalaryImportErrorKind.FILE, result.errors.single().kind)
        assertEquals("IMP-XML-SEC-001", result.errors.single().code)
        assertTrue(result.errors.single().suggestion.isNotBlank())
    }

    @Test fun acceptsUtf16XmlParts() {
        val utf16 = transformXmlParts(fixture()) { xml -> "<?xml version=\"1.0\" encoding=\"UTF-16\"?>" + xml }
        val codec = SalaryXlsxCodec { javaClass.classLoader!!.getResourceAsStream("salary-template.xlsx")!! }
        val result = codec.importPreview(ByteArrayInputStream(utf16), SalaryIncomeTypes.defaults, LocalDate.of(2026, 11, 4))
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(1, result.entries.size)
    }

    @Test fun rejectsExternalPackageRelationships() {
        val bytes = fixture()
        val modified = ByteArrayOutputStream()
        java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
            java.util.zip.ZipOutputStream(modified).use { output ->
                while (true) {
                    val item = input.nextEntry ?: break
                    output.putNextEntry(java.util.zip.ZipEntry(item.name))
                    val part = input.readBytes()
                    val content = if (item.name == "xl/_rels/workbook.xml.rels") {
                        part.toString(Charsets.UTF_8).replace("Target=\"worksheets/sheet1.xml\"", "Target=\"https://example.invalid/book.xlsx\" TargetMode=\"External\"").toByteArray()
                    } else part
                    output.write(content); output.closeEntry()
                }
            }
        }
        val codec = SalaryXlsxCodec { javaClass.classLoader!!.getResourceAsStream("salary-template.xlsx")!! }
        val result = codec.importPreview(ByteArrayInputStream(modified.toByteArray()), SalaryIncomeTypes.defaults, LocalDate.of(2026, 11, 4))
        assertTrue(result.entries.isEmpty())
        assertTrue(result.errors.any { it.reason.contains("外部链接") })
    }

    @Test fun rejectsMalformedWorkbookWithoutPartialRecords() {
        val codec = SalaryXlsxCodec { javaClass.classLoader!!.getResourceAsStream("salary-template.xlsx")!! }
        val result = codec.importPreview(ByteArrayInputStream(byteArrayOf(1, 2, 3)), SalaryIncomeTypes.defaults, LocalDate.of(2026, 11, 4))
        assertTrue(result.entries.isEmpty())
        assertTrue(result.errors.isNotEmpty())
        assertEquals(SalaryImportErrorKind.FILE, result.errors.single().kind)
        assertEquals(null, result.errors.single().row)
        assertEquals("IMP-ZIP-001", result.errors.single().code)
    }

    @Test fun resolvesSheet1ThroughItsWorkbookRelationshipInsteadOfAssumingPartName() {
        val renamedSheet = renameWorksheetPart(fixture(), "xl/worksheets/annual-salary.xml")
        val codec = SalaryXlsxCodec { javaClass.classLoader!!.getResourceAsStream("salary-template.xlsx")!! }
        val result = codec.importPreview(ByteArrayInputStream(renamedSheet), SalaryIncomeTypes.defaults, LocalDate.of(2026, 11, 4))
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(1, result.entries.size)
        assertEquals(11_000L, result.entries.single().netIncomeFen)
    }

    @Test fun rejectsExcessiveXmlStructureBeforeBuildingDom() {
        val tooManyElements = replaceSheet(fixture()) { it.replace("</sheetData>", "<x/>".repeat(100_001) + "</sheetData>") }
        val codec = SalaryXlsxCodec { javaClass.classLoader!!.getResourceAsStream("salary-template.xlsx")!! }
        val result = codec.importPreview(ByteArrayInputStream(tooManyElements), SalaryIncomeTypes.defaults, LocalDate.of(2026, 11, 4))
        assertTrue(result.entries.isEmpty())
        assertEquals("IMP-XML-SIZE-002", result.errors.single().code)
        assertEquals(null, result.errors.single().row)
    }

    @Test fun reportsTheExactCellAndRepairForAnInvalidAmount() {
        val malformed = replaceSheet(fixture()) { it.replace("<v>100</v>", "<v>12x</v>") }
        val codec = SalaryXlsxCodec { javaClass.classLoader!!.getResourceAsStream("salary-template.xlsx")!! }
        val result = codec.importPreview(ByteArrayInputStream(malformed), SalaryIncomeTypes.defaults, LocalDate.of(2026, 11, 4))
        val issue = result.errors.single()
        assertEquals(SalaryImportErrorKind.CELL, issue.kind)
        assertEquals(2, issue.row)
        assertEquals("C2", issue.cell)
        assertEquals("毛收入", issue.field)
        assertEquals("12x", issue.currentValue)
        assertTrue(issue.reason.isNotBlank())
        assertTrue(issue.suggestion.isNotBlank())
        assertTrue(result.entries.isEmpty())
    }

    private fun fixture(): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            fun part(name: String, xml: String) { zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(xml.toByteArray()); zip.closeEntry() }
            part("xl/workbook.xml", """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            part("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val labels = listOf("时间", "收入项", "毛收入", "工会费", "养老保险", "医疗保险", "失业保险", "公积金", "企业年金", "税", "纯收入", "退税（中行 到账时间）")
            part("xl/sharedStrings.xml", "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" + labels.joinToString("") { "<si><t>" + it + "</t></si>" } + "</sst>")
            val headerCells = labels.take(11).mapIndexed { i, _ -> "<c r=\"" + ('A' + i) + "1\" t=\"s\"><v>" + i + "</v></c>" }.joinToString("")
            part("xl/worksheets/sheet1.xml", "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\">" + headerCells + "</row><row r=\"2\"><c r=\"A2\"><v>46327</v></c><c r=\"B2\" t=\"s\"><v>11</v></c><c r=\"C2\"><v>100</v></c><c r=\"J2\"><v>-10</v></c><c r=\"K2\"><v>110</v></c></row><row r=\"3\"><c r=\"K3\"><f>C3-SUM(D3:J3)</f><v>0</v></c></row></sheetData></worksheet>")
        }
        return out.toByteArray()
    }

    private fun transformXmlParts(source: ByteArray, transform: (String) -> String): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipInputStream(ByteArrayInputStream(source)).use { input ->
            java.util.zip.ZipOutputStream(out).use { output ->
                while (true) {
                    val item=input.nextEntry?:break
                    output.putNextEntry(java.util.zip.ZipEntry(item.name))
                    val bytes=input.readBytes()
                    val data=if(item.name.endsWith(".xml"))transform(bytes.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_16) else bytes
                    output.write(data);output.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }

    private fun replaceSheet(source: ByteArray, transform: (String) -> String): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipInputStream(ByteArrayInputStream(source)).use { input ->
            java.util.zip.ZipOutputStream(out).use { output ->
                while (true) {
                    val item = input.nextEntry ?: break
                    output.putNextEntry(java.util.zip.ZipEntry(item.name))
                    val bytes = input.readBytes()
                    output.write(if (item.name == "xl/worksheets/sheet1.xml") transform(bytes.toString(Charsets.UTF_8)).toByteArray() else bytes)
                    output.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }

    private fun renameWorksheetPart(source: ByteArray, newPath: String): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipInputStream(ByteArrayInputStream(source)).use { input ->
            java.util.zip.ZipOutputStream(out).use { output ->
                while (true) {
                    val item = input.nextEntry ?: break
                    val bytes = input.readBytes()
                    val renamed = if (item.name == "xl/worksheets/sheet1.xml") newPath else item.name
                    val content = if (item.name == "xl/_rels/workbook.xml.rels") {
                        bytes.toString(Charsets.UTF_8).replace("Target=\"worksheets/sheet1.xml\"", "Target=\"worksheets/annual-salary.xml\"").toByteArray()
                    } else bytes
                    output.putNextEntry(java.util.zip.ZipEntry(renamed))
                    output.write(content)
                    output.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }
}
