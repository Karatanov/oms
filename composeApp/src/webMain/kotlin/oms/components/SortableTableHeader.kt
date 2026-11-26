package oms.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager
import oms.theme.OmsDimensions

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SortableTableHeader(
    title: String,
    selected: Boolean,
    ascending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    numeric: Boolean = false,
    height: Dp = OmsDimensions.TableHeaderHeight,
    maxLines: Int = 2
) {
    var focused by remember { mutableStateOf(false) }
    val visibilityRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) {
            withFrameNanos { }
            visibilityRequester.bringIntoView()
        }
    }
    val sortDescription = LocalizationManager.t(
        if (!selected) "not_sorted" else if (ascending) "sorted_ascending_state" else "sorted_descending_state"
    )
    OmsTooltipBox(
        modifier = modifier,
        tooltip = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(sortDescription, style = MaterialTheme.typography.bodySmall)
            }
        }
    ) {
    oms.components.WorkspaceTextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(height).buttonHandCursor()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .bringIntoViewRequester(visibilityRequester)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .semantics { stateDescription = sortDescription },
        shape = androidx.compose.ui.graphics.RectangleShape,
        contentPadding = PaddingValues(horizontal = if (numeric) 8.dp else 0.dp, vertical = 4.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(title, Modifier.fillMaxWidth().padding(start = 8.dp, end = 24.dp), style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Icon(
                when {
                    !selected -> Icons.Default.UnfoldMore
                    ascending -> Icons.Default.ArrowUpward
                    else -> Icons.Default.ArrowDownward
                },
                null,
                Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(14.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f)
            )
        }
    }
}
}
