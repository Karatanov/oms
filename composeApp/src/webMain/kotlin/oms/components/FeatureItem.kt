package oms.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
   🔹 Один пункт списку переваг

   🔹 Використовується у login-екрані для підсилення UX
*/
@Composable
fun FeatureItem(text: String) {

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // 🔹 Маленький "індикатор"
        Text(
            text = "•",
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp)
        )

        Text(
            text = text,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp)
        )
    }
}
