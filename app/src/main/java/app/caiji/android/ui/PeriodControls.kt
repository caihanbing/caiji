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
import androidx.compose.material3.IconButton
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
import java.math.BigInteger

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
    modifier: Modifier = Modifier,
    amountsHidden: Boolean = false,
    onToggleAmounts: (() -> Unit)? = null
) {
    FinancialSummaryCard(
        primaryLabel = title,
        primaryFen = totals.balanceFen,
        leftLabel = incomeLabel,
        leftFen = totals.incomeFen,
        leftColor = CaijiPalette.Income,
        rightLabel = expenseLabel,
        rightFen = totals.expenseFen,
        modifier = modifier,
        primaryTag = "report-balance",
        maskPrimaryAmount = amountsHidden,
        maskLeftAmount = amountsHidden,
        onToggleAmounts = onToggleAmounts
    )
}

@Composable
internal fun FinancialSummaryCard(
    primaryLabel: String,
    primaryFen: BigInteger,
    leftLabel: String,
    leftFen: BigInteger,
    leftColor: androidx.compose.ui.graphics.Color,
    rightLabel: String,
    rightFen: BigInteger,
    modifier: Modifier = Modifier,
    primaryTag: String? = null,
    leftTag: String? = null,
    rightTag: String? = null,
    maskPrimaryAmount: Boolean = false,
    maskLeftAmount: Boolean = false,
    onToggleAmounts: (() -> Unit)? = null
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(primaryLabel, Modifier.weight(1f), color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
                if (onToggleAmounts != null) {
                    IconButton(onClick = onToggleAmounts, modifier = Modifier.size(48.dp).testTag("summary-privacy-toggle")) {
                        CaijiIcon(if (maskPrimaryAmount) CaijiIconName.VISIBILITY_OFF else CaijiIconName.VISIBILITY,
                            if (maskPrimaryAmount) "显示金额" else "隐藏金额")
                    }
                }
            }
            Box(Modifier.fillMaxWidth().horizontalScroll(balanceScroll, enabled = balanceScroll.maxValue > 0)
                .testTag("summary-balance-scroll-area")) {
                Text(
                    if (maskPrimaryAmount) maskThousands("${if (primaryFen.signum() < 0) "−" else ""}¥${formatYuan(primaryFen.abs())}")
                    else "${if (primaryFen.signum() < 0) "−" else ""}¥${formatYuan(primaryFen.abs())}",
                    if (primaryTag == null) Modifier else Modifier.testTag(primaryTag),
                    style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SummaryMetric(leftLabel, leftFen, leftColor, Modifier.weight(1f), leftTag, maskLeftAmount)
                SummaryMetric(rightLabel, rightFen, CaijiPalette.Ink, Modifier.weight(1f), rightTag)
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, amountFen: java.math.BigInteger, color: androidx.compose.ui.graphics.Color,
    modifier: Modifier, valueTag: String? = null, hidden: Boolean = false) {
    val amountScroll = rememberScrollState()
    Column(modifier) {
        Text(label, color = CaijiPalette.SecondaryText, style = MaterialTheme.typography.bodySmall)
        Box(Modifier.fillMaxWidth().horizontalScroll(amountScroll, enabled = amountScroll.maxValue > 0)) {
            Text(
                if (hidden) maskThousands("¥${formatYuan(amountFen)}") else "¥${formatYuan(amountFen)}",
                modifier = if (valueTag == null) Modifier else Modifier.testTag(valueTag),
                color = color,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
