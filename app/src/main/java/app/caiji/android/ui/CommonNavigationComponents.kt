package app.caiji.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.lerp
import app.caiji.android.ui.theme.CaijiPalette
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun CaijiPageTopBar(
    title: String,
    onBack: () -> Unit,
    backTag: String,
    actionTag: String,
    backEnabled: Boolean = true,
    action: (@Composable () -> Unit)? = null
) {
    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.align(Alignment.CenterStart).size(48.dp)
                .clickable(enabled = backEnabled, onClick = onBack)
                .semantics { contentDescription = "返回" }
                .testTag(backTag),
            contentAlignment = Alignment.Center
        ) {
            CaijiIcon(CaijiIconName.BACK, contentDescription = null)
        }
        Text(
            title,
            Modifier.align(Alignment.Center).padding(horizontal = 64.dp).testTag("page-title"),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            Modifier.align(Alignment.CenterEnd).defaultMinSize(minWidth = 48.dp).height(48.dp)
                .testTag(actionTag),
            contentAlignment = Alignment.Center
        ) {
            action?.invoke()
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun PagerSegmentedTabs(
    state: PagerState,
    labels: List<String>,
    tags: List<String>,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerTag: String,
    enabled: Boolean = true
) {
    require(labels.size == tags.size && labels.size == state.pageCount)
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).height(48.dp)
            .clip(RoundedCornerShape(12.dp)).background(CaijiPalette.SurfaceAlt)
            .padding(3.dp).testTag(containerTag)
    ) {
        val width = maxWidth / labels.size
        val progress = (state.currentPage + state.currentPageOffsetFraction)
            .coerceIn(0f, (labels.size - 1).toFloat())
        Surface(
            modifier = Modifier.offset(x = width * progress).width(width).fillMaxHeight()
                .padding(1.dp),
            color = CaijiPalette.Primary,
            shape = RoundedCornerShape(9.dp)
        ) {}
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val selected = progress.roundToInt() == index
                val selectedFraction = (1f - abs(progress - index)).coerceIn(0f, 1f)
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .selectable(selected = selected, enabled = enabled, role = Role.Tab) { onSelectPage(index) }
                        .testTag(tags[index]),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = lerp(CaijiPalette.SecondaryText, CaijiPalette.OnPrimary, selectedFraction),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
