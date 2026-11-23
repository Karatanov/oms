package oms.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * A Material button contains selectable text on Web/Wasm.  Without an
 * explicit override that text can replace the button cursor with an I-beam.
 */
fun Modifier.buttonHandCursor(): Modifier =
    pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
