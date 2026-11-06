package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/** Keeps table rows compact while allowing the complete name to be opened in place. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ExpandableTableText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified
) {
    var truncated by remember(text) { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    OmsTooltipBox(
        modifier = modifier
            .onFocusChanged { focused = it.hasFocus }
            .background(
                if (focused && truncated) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                MaterialTheme.shapes.extraSmall
            )
            .then(if (truncated) Modifier.buttonHandCursor() else Modifier),
        tooltip = { Text(text, style = MaterialTheme.typography.bodySmall) },
        openOnPress = truncated,
        enabled = truncated
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = style,
            color = if (focused && truncated) MaterialTheme.colorScheme.onPrimaryContainer else color,
            onTextLayout = { layout -> truncated = layout.hasVisualOverflow }
        )
    }
}
