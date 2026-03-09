package oms.usif.ua.ufsi.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
   Верхня панель системи (Header).

   У реальній OMS тут знаходяться:
   - назва системи
   - користувач
   - logout
   - мова
*/

@Composable
fun TopBar() {

    // Surface створює фон панелі
    Surface(
        shadowElevation = 4.dp
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),

            verticalAlignment = Alignment.CenterVertically
        ) {

            // Назва системи
            Text(
                text = "Operations Monitoring System",
                style = MaterialTheme.typography.titleLarge
            )

            // Spacer розсовує елементи
            Spacer(modifier = Modifier.weight(1f))

            // Кнопка користувача (поки заглушка)
            Button(onClick = { }) {
                Text("User")
            }
        }
    }
}