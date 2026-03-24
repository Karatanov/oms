package oms.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.navigation.Screen

// 🔹 Sidebar згідно spec 7.2 :contentReference[oaicite:1]{index=1}
@Composable
fun Sidebar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onLogout: () -> Unit
) {

    Column(
        modifier = Modifier
            .width(240.dp)
            .fillMaxHeight()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Text("OMS", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(16.dp))

        SidebarItem("Dashboard", Icons.Default.Dashboard, Screen.Dashboard, currentScreen, onNavigate)
        SidebarItem("Projects", Icons.AutoMirrored.Filled.ListAlt, Screen.Projects, currentScreen, onNavigate)
        SidebarItem("Map", Icons.Default.Map, Screen.Map, currentScreen, onNavigate)
        SidebarItem("Inspection Reports", Icons.Default.Description, Screen.Inspections, currentScreen, onNavigate)
        SidebarItem("Financial Monitoring", Icons.Default.AccountBalance, Screen.Financial, currentScreen, onNavigate)
        SidebarItem("Documents", Icons.Default.Description, Screen.Documents, currentScreen, onNavigate)

        Spacer(Modifier.weight(1f))

        Button(onClick = onLogout) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                Text("Logout")
            }
        }
    }
}