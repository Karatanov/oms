package oms.layout

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import oms.navigation.*
import oms.screens.DashboardScreen
import oms.screens.MapScreen
import oms.screens.ProjectsScreen

//import oms.screens.inspections.*
//import oms.screens.financial.*
//import oms.screens.documents.*
//import oms.screens.admin.*

// 🔹 Головний layout після логіну
// 🔹 Відповідає spec 7.2 (Sidebar layout) :contentReference[oaicite:0]{index=0}
@Composable
fun AppLayout(appState: AppState) {

    Row(modifier = Modifier.fillMaxSize()) {

        // ---------------- SIDEBAR ----------------
        Sidebar(
            currentScreen = appState.currentScreen,
            onNavigate = { appState.navigate(it) },
            onLogout = { appState.logout() }
        )

        // ---------------- CONTENT ----------------
        Box(modifier = Modifier.weight(1f)) {

            when (val screen = appState.currentScreen) {

                is Screen.Dashboard -> DashboardScreen()

                is Screen.Projects -> ProjectsScreen()

                is Screen.Map -> MapScreen()

//                is Screen.Inspections -> InspectionsScreen()
//
//                is Screen.Financial -> FinancialScreen()
//
//                is Screen.Documents -> DocumentsScreen()
//
//                is Screen.Admin -> AdminScreen()

                else -> {}
            }
        }
    }
}