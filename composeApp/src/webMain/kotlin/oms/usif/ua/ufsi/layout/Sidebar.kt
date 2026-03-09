package oms.usif.ua.ufsi.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.usif.ua.ufsi.Screen

@Composable
fun Sidebar(
    current: Screen,
    onNavigate: (Screen) -> Unit
) {

    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .padding(16.dp)
    ) {

        Text("OMS", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = { onNavigate(Screen.DASHBOARD) }) {
            Text("Dashboard")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { onNavigate(Screen.PROJECTS) }) {
            Text("Projects")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { onNavigate(Screen.MAP) }) {
            Text("Map")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { onNavigate(Screen.REPORTS) }) {
            Text("Inspection Reports")
        }
    }
}