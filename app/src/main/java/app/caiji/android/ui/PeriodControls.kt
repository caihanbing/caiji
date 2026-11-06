package app.caiji.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.caiji.android.data.LedgerPeriodTotals
import app.caiji.android.ui.theme.CaijiPalette

@Composable
internal fun PeriodArrowButton(
    label: String,
    tag: String,
    isNext: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        CaijiIcon(
            if (isNext) CaijiIconName.MONTH_NEXT else CaijiIconName.MONTH_PREVIOUS,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
internal fun CompactPeriodArrowButton(
    label: String,
    tag: String,
    isNext: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        CaijiIcon(
            if (isNext) CaijiIconName.MONTH_NEXT else CaijiIconName.MONTH_PREVIOUS,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun PeriodSummaryCard(
    title: String,
    totals: LedgerPeriodTotals,
    incomeLabel: String = "收入",
    expenseLabel: String = "支出",
    modifier: Modifier = Modifier
) {
    val balanceScroll = rememberScrollState()
    Surface(
        color = CaijiPalette.PrimarySoft,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("period-summary-card")
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(title, color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
            Box(Modifier.fillMaxWidth().horizontalScroll(balanceScroll, enabled = balanceScroll.maxValue > 0)
                .testTag("summary-balance-scroll-area")) {
                Text(
                    "${if (totals.balanceFen.signum() < 0) "−" else ""}¥${formatYuan(totals.balanceFen.abs())}",
                    Modifier.testTag("report-balance"),
                    style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SummaryMetric(incomeLabel, totals.incomeFen, CaijiPalette.Income, Modifier.weight(1f))
                SummaryMetric(expenseLabel, totals.expenseFen, CaijiPalette.Ink, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, amountFen: java.math.BigInteger, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val amountScroll = rememberScrollState()
    Column(modifier) {
        Text(label, color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
        Box(Modifier.fillMaxWidth().horizontalScroll(amountScroll, enabled = amountScroll.maxValue > 0)) {
            Text(
                "¥${formatYuan(amountFen)}",
                color = color,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
