package oms.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Responsive title and action area shared by OMS chart cards.
 *
 * Titles remain optically centred within the entire chart card. On wide cards
 * a compact toolbar is overlaid in a protected trailing lane; narrow cards
 * put the toolbar below the title so controls never overlap its text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OmsChartHeader(
    title: String,
    modifier: Modifier = Modifier,
    toolbar: @Composable () -> Unit = {}
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth >= 640.dp && !oms.layout.LocalMobileLayout.current) {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    title,
                    // Symmetric padding protects the title from the trailing
                    // controls while preserving the card's visual centre.
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 118.dp),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 3
                )
                FlowRow(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    // Toolbar controls have intentionally different compact hit areas.
                    // Centre each item so download, sort and collapse actions share one axis.
                    itemVerticalAlignment = Alignment.CenterVertically
                ) { toolbar() }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    title,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 3
                )
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) { toolbar() }
            }
        }
    }
}
