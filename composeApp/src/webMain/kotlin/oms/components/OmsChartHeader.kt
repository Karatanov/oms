package oms.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Responsive title and action area shared by OMS chart cards.
 *
 * Wide cards reserve space for both title and controls. Narrow cards place
 * controls below the title; long toolbars wrap without squeezing the title.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OmsChartHeader(
    title: String,
    modifier: Modifier = Modifier,
    toolbar: @Composable () -> Unit = {}
) {
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth >= 420.dp && !oms.layout.LocalMobileLayout.current) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    OmsTooltipBox(tooltip = { Text(title) }) {
                        Text(
                            title,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Start,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FlowRow(
                    modifier = Modifier.widthIn(max = minOf(320.dp, maxWidth * .55f)),
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    // Toolbar controls have intentionally different compact hit areas.
                    // Centre each item so download, sort and collapse actions share one axis.
                    itemVerticalAlignment = Alignment.CenterVertically
                ) { toolbar() }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OmsTooltipBox(tooltip = { Text(title) }) {
                    Text(
                        title,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) { toolbar() }
            }
        }
    }
}
}
