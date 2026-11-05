package app.caiji.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalaryXlsxCodecTest {
    @Test fun AndroidXmlRuntimeExportsAndReimportsSampleFormat() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val types = SalaryIncomeTypes.defaults
        val records = (0 until 180).map { i ->
            val type = types[i % types.size]
            val gross = 100_000L + i * 100L
            SalaryEntry("sample-" + i, LocalDate.of(2025, 1, 1).plusDays(i.toLong()), type.id,
                gross, unionFen = 100, pensionFen = 2_000, taxFen = if (i % 40 == 0) -200 else 500,
                isCash = type.isCash, createdAtMillis = i.toLong(), updatedAtMillis = i.toLong())
        }
        val codec = SalaryXlsxCodec { context.assets.open("salary-template.xlsx") }
        val output = ByteArrayOutputStream()
        codec.export(output, records, types)
        val preview = codec.importPreview(ByteArrayInputStream(output.toByteArray()), types, LocalDate.now())
        assertTrue(preview.errors.toString(), preview.errors.isEmpty())
        assertEquals(180, preview.entries.size)
        assertEquals(SalaryReports.summarize(records).grossFen, SalaryReports.summarize(preview.entries).grossFen)
        assertEquals(SalaryReports.summarize(records).netFen, SalaryReports.summarize(preview.entries).netFen)
    }
}
