package oms.usif.ua.ufsi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.usif.ua.ufsi.map.LeafletMapView

@Composable
fun MapScreen() {
    LeafletMapView()

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
                .height(120.dp)
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
                    text = "The interactive map is rendered in a separate web layer for the WASM target."
                )

                Text(
                    text = "Next step: add project markers, popups, filters, and map synchronization."
                )
            }
        }
    }
}