package oms.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager

/** Shared Dashboard/Financial Monitoring display currency; never edits records. */
@Composable
fun CurrencySelector(currency: String, onSelect: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(LocalizationManager.t("currency"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        listOf("EUR", "UAH").forEach { code ->
            FilterChip(selected = currency == code, onClick = { onSelect(code) }, label = { Text(code) }, modifier = Modifier.buttonHandCursor())
        }
    }
}
