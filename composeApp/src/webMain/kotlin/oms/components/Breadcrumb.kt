package oms.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.navigation.Screen

/*
   🔹 Компонент Breadcrumb

   🔹 Відображає поточний шлях:
      OMS / Screen Title

   🔹 Використовує title з Screen (без дублювання логіки)
*/
@Composable
fun Breadcrumb(screen: Screen) {

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {

        // 🔹 Назва системи (root)
        Text(
            text = "UMITAF",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "/",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 🔹 Назва поточного екрану
        Text(
            text = screen.title,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
