package oms.usif.ua.ufsi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
   Екран проектів.

   У реальній OMS тут знаходиться:
   - таблиця проектів
   - фільтри
   - кнопка створення проекту
*/

@Composable
fun ProjectsScreen() {

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Text(
            text = "Projects",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Заглушка таблиці проектів
        ProjectRow("1", "School reconstruction", "Kyiv", "Active")

        ProjectRow("2", "Hospital modernization", "Lviv", "Planning")

        ProjectRow("3", "Road repair", "Kharkiv", "Completed")
    }
}

/*
   Один рядок "таблиці" проектів.

   Поки що використовуємо Card,
   пізніше можна зробити справжню DataTable.
*/

@Composable
fun ProjectRow(
    id: String,
    name: String,
    region: String,
    status: String
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {

            Text(id, modifier = Modifier.width(50.dp))

            Text(name, modifier = Modifier.weight(1f))

            Text(region, modifier = Modifier.width(120.dp))

            Text(status, modifier = Modifier.width(120.dp))
        }
    }
}