package oms.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A normal step on click and continuous smooth scrolling while held. */
@Composable
fun HoldToScrollButton(
    tooltip: String,
    icon: ImageVector,
    scrollState: ScrollableState,
    direction: Int,
    modifier: Modifier = Modifier,
    clickDistance: Float = 420f,
    continuousPixelsPerFrame: Float = 9f
) {
    val scope = rememberCoroutineScope()
    var suppressClick by remember { mutableStateOf(false) }
    val sign = if (direction < 0) -1f else 1f

    OmsTooltipBox(tooltip = { Text(tooltip) }) {
        FilledIconButton(
            modifier = modifier.pointerInput(scrollState, sign) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    suppressClick = false
                    val continuousJob = launch {
                        delay(viewConfiguration.longPressTimeoutMillis)
                        suppressClick = true
                        while (isActive) {
                            scrollState.scrollBy(sign * continuousPixelsPerFrame)
                            delay(16)
                        }
                    }
                    waitForUpOrCancellation()
                    continuousJob.cancel()
                    if (suppressClick) {
                        // FilledIconButton normally emits its click immediately
                        // after release. Reset as a fallback if it was cancelled.
                        launch { delay(120); suppressClick = false }
                    }
                }
            },
            onClick = {
                if (suppressClick) {
                    suppressClick = false
                } else {
                    scope.launch { scrollState.animateScrollBy(sign * clickDistance) }
                }
            }
        ) {
            Icon(icon, tooltip)
        }
    }
}
