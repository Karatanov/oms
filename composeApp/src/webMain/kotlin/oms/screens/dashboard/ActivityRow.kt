package oms.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ActivityRow(title: String, userLogin: String?, time: String) {
    val metadataColor = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        @Composable
        fun EventDetails(modifier: Modifier) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(userLogin ?: "—", style = MaterialTheme.typography.bodyMedium, color = metadataColor)
            }
        }
        if (maxWidth < 520.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EventDetails(Modifier.fillMaxWidth())
                Text(time, style = MaterialTheme.typography.bodySmall, color = metadataColor)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EventDetails(Modifier.weight(1f))
                Text(time, style = MaterialTheme.typography.bodySmall, color = metadataColor)
            }
        }
    }
}
