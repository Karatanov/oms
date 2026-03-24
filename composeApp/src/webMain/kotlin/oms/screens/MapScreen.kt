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
    val projects = ProjectRepository.projects

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Projects Map",
            style = MaterialTheme.typography.headlineMedium
        )

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

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                LeafletMapView(projects = projects)
            }
        }
    }
}