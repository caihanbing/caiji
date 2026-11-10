package app.caiji.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.caiji.android.ui.theme.CaijiPalette

internal data class PeriodAmountTableRow(
    val label: String,
    val amounts: List<String>,
    val tag: String,
    val onClick: () -> Unit
)

@Composable
internal fun PeriodAmountTable(
    heading: String,
    amountHeadings: List<String>,
    rows: List<PeriodAmountTableRow>,
    scrollTag: String,
    headerTags: List<String>,
    horizontalScrollState: androidx.compose.foundation.ScrollState? = null
) {
    require(amountHeadings.size == 3 && headerTags.size == 3)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val maxNumberLength = rows.maxOfOrNull { it.amounts.maxOfOrNull(String::length) ?: 0 } ?: 8
        val firstWidth = 48.dp
        val minimumNumericWidth = maxOf(74.dp, (maxNumberLength * 6.3f * fontScale + 12f).dp)
        val rowStartPadding = 6.dp
        val rowEndPadding = 2.dp
        val tableWidth = maxOf(maxWidth, firstWidth + rowStartPadding + rowEndPadding + minimumNumericWidth * 3)
        val numericWidth = (tableWidth - firstWidth - rowStartPadding - rowEndPadding) / 3
        val tableScroll = horizontalScrollState ?: rememberScrollState()
        Column(Modifier.horizontalScroll(tableScroll, enabled = tableScroll.maxValue > 0)
            .testTag(scrollTag)) {
            Column(Modifier.width(tableWidth)) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = rowStartPadding, end = rowEndPadding, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(heading, Modifier.width(firstWidth), color = CaijiPalette.SecondaryText,
                            style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        amountHeadings.forEachIndexed { index, text ->
                            Text(text, Modifier.width(numericWidth).testTag(headerTags[index]),
                                color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.End, maxLines = 1)
                        }
                    }
                    HorizontalDivider(color = CaijiPalette.Divider)
                    rows.forEach { item ->
                        require(item.amounts.size == 3)
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = item.onClick).testTag(item.tag)
                                .padding(start = rowStartPadding, end = rowEndPadding, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item.label, Modifier.width(firstWidth), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            item.amounts.forEach { amount ->
                                Box(Modifier.width(numericWidth), contentAlignment = Alignment.CenterEnd) {
                                    Text(amount, color = CaijiPalette.Ink,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                        maxLines = 1, softWrap = false)
                                }
                            }
                        }
                        HorizontalDivider(color = CaijiPalette.Divider)
                    }
            }
        }
    }
}
