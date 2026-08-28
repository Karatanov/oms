package oms.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oms.localization.LocalizationManager

/** One viewport and a draggable, keyboard-accessible scrollbar outside the rows. */
@Composable
fun ScrollableTable(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().horizontalScroll(scroll), content = content)
        TableScrollControls(scroll)
    }
}

@Composable
fun TableScrollControls(scroll: ScrollState) {
    if (scroll.maxValue <= 0) return
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TableActionIconButton(LocalizationManager.t("scroll_table_left"), Icons.Default.KeyboardArrowLeft) {
            scope.launch { scroll.animateScrollTo((scroll.value - 500).coerceAtLeast(0)) }
        }
        Slider(value = scroll.value.toFloat(), onValueChange = { value -> scope.launch { scroll.scrollTo(value.toInt()) } },
            valueRange = 0f..scroll.maxValue.toFloat(),
            modifier = Modifier.weight(1f).semantics { contentDescription = LocalizationManager.t("table_scroll") })
        TableActionIconButton(LocalizationManager.t("scroll_table_right"), Icons.Default.KeyboardArrowRight) {
            scope.launch { scroll.animateScrollTo((scroll.value + 500).coerceAtMost(scroll.maxValue)) }
        }
    }
}
