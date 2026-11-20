package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager

/** Shared Dashboard/Financial Monitoring display currency; never edits records. */
@Composable
fun CurrencySelector(currency: String, onSelect: (String) -> Unit) {
    val description = LocalizationManager.t("currency")
    Row(
        Modifier.selectableGroup().semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf("EUR", "UAH").forEach { code ->
            val selected = currency == code
            val label = LocalizationManager.currencyLabel(code)
            OmsTooltipBox(tooltip = { Text("$description: $label") }) {
                Box(
                    Modifier.size(width = 36.dp, height = 24.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(code) })
                        .buttonHandCursor().semantics { contentDescription = label },
                    contentAlignment = Alignment.Center
                ) {
                    Text(code, style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
