package oms.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import oms.navigation.Screen

// 🔹 Один пункт sidebar
// 🔹 Підсвічується, якщо активний
@Composable
fun SidebarItem(
    title: String,
    screen: Screen,
    current: Screen,
    onNavigate: (Screen) -> Unit
) {

    val isSelected = current::class == screen::class

    Button(
        onClick = { onNavigate(screen) },
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelected)
            ButtonDefaults.buttonColors()
        else
            ButtonDefaults.outlinedButtonColors()
    ) {
        Text(title)
    }
}