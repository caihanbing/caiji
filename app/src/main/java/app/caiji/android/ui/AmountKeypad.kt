package app.caiji.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.caiji.android.ui.theme.CaijiPalette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun AmountKeypad(
    date: LocalDate,
    saving: Boolean,
    canSave: Boolean,
    tagPrefix: String,
    onDate: () -> Unit,
    onSave: () -> Unit,
    onKey: (String) -> Unit
) {
    val keys = listOf(listOf("7", "8", "9", "日期"), listOf("4", "5", "6", "+"),
        listOf("1", "2", "3", "−"), listOf(".", "0", "⌫", "保存"))
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth().height(43.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { key ->
                    val isDate = key == "日期"
                    val isSave = key == "保存"
                    val keyEnabled = !saving && (!isSave || canSave)
                    val dateLabel = if (date == LocalDate.now()) "今天" else date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
                    val fullDate = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (isSave && keyEnabled) CaijiPalette.Primary
                            else if (isSave) CaijiPalette.SurfaceAlt
                            else if (isDate) CaijiPalette.PrimarySoft else CaijiPalette.Surface,
                        modifier = Modifier.weight(1f).fillMaxSize()
                            .clickable(enabled = keyEnabled) {
                                when {
                                    isDate -> onDate()
                                    isSave -> onSave()
                                    else -> onKey(key)
                                }
                            }
                            .semantics { if (isDate) contentDescription = "日期，$dateLabel，$fullDate" }
                            .testTag("$tagPrefix-key-${if (isSave) "save" else if (isDate) "date" else key}"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (isDate) dateLabel else if (isSave && saving) "保存中" else key,
                                modifier = if (isDate) Modifier.testTag("$tagPrefix-key-date-label") else Modifier,
                                color = if (isSave && !keyEnabled) CaijiPalette.SecondaryText else CaijiPalette.Ink,
                                style = if (rowIndex == 3 && isSave || isDate) MaterialTheme.typography.labelLarge
                                else MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSave || isDate) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = TextAlign.Center, maxLines = if (isDate) 2 else 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
