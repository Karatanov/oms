package oms.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Actions wrap as whole controls instead of competing for a single narrow row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WrappingActions(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp, Alignment.End),
    content: @Composable () -> Unit
) {
    FlowRow(modifier, horizontalArrangement = horizontalArrangement,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically) { content() }
}

/** Forms preserve full-width fields on phones, including inside narrow dialogs. */
@Composable
fun ResponsiveFormRow(content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 900.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                content(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content(Modifier.fillMaxWidth())
            }
        }
    }
}

/** Record details stack labels above values when a label column would crowd them. */
@Composable
fun ResponsiveDetailRow(label: @Composable (Modifier) -> Unit, value: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                label(Modifier.width(210.dp))
                value(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                label(Modifier.fillMaxWidth())
                value(Modifier.fillMaxWidth())
            }
        }
    }
}
