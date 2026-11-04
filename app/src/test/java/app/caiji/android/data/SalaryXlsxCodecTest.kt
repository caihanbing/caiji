package app.caiji.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

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
        val result = codec.importPreview(ByteArrayInputStream(bytes), SalaryIncomeTypes.defaults, LocalDate.of(2026, 11, 4))
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(listOf(entry.duplicateKey()), result.entries.map { it.duplicateKey() })
        assertTrue(result.errors.isEmpty())
        assertEquals(1, result.rowsRead)
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
        assertTrue(result.errors.any { it.message.contains("外部链接") })
    }

    @Test fun rejectsMalformedWorkbookWithoutPartialRecords() {
        val codec = SalaryXlsxCodec { javaClass.classLoader!!.getResourceAsStream("salary-template.xlsx")!! }
        val result = codec.importPreview(ByteArrayInputStream(byteArrayOf(1, 2, 3)), SalaryIncomeTypes.defaults, LocalDate.of(2026, 11, 4))
        assertTrue(result.entries.isEmpty())
        assertTrue(result.errors.isNotEmpty())
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
}
