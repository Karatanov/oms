package oms.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

/** Highlights the whole category without changing its size or chart scale. */
internal fun Modifier.chartItemHighlight(): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    this.hoverable(interactionSource)
        .onFocusChanged { focused = it.isFocused }
        .then(if (hovered || focused) Modifier.background(
            MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.extraSmall
        ) else Modifier)
        .then(if (focused) Modifier.border(
            2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall
        ) else Modifier)
}
