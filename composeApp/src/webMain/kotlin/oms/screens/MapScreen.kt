package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.data.ProjectRepository
import oms.map.LeafletMapView

@Composable
fun MapScreen() {
    // Беремо список проєктів для відображення на карті.
    val projects = ProjectRepository.projects

    // Показуємо карту та передаємо в неї маркери.
    LeafletMapView(projects = projects)

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "Projects Map",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Map controls",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "На карті відображаються всі проєкти з локального репозиторію."
                )

                Text(
                    text = "Колір маркера відповідає статусу проєкту."
                )

                Text(
                    text = "Кількість маркерів: ${projects.size}"
                )
            }
        }
    }
}