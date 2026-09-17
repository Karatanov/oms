package oms.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    OmsTooltipBox(
        modifier = modifier,
        tooltip = { Text(text, style = MaterialTheme.typography.bodySmall) },
        openOnPress = true
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = style,
            color = color
        )
    }
}
