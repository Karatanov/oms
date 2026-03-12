package oms.usif.ua.ufsi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
   Екран карти.

   У реальній системі тут буде:
   - інтеграція Leaflet / Mapbox
   - відображення проєктів
   - кластеризація точок
*/

@Composable
fun MapScreen() {

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Text(
            text = "Projects Map",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Заглушка карти
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {

                Text("Map will be integrated here")
            }
        }
    }
}