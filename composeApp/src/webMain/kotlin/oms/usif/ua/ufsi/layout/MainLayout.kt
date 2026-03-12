package oms.usif.ua.ufsi.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.usif.ua.ufsi.Screen

@Composable
fun MainLayout(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    content: @Composable () -> Unit
) {

    Row(
        modifier = Modifier.fillMaxSize()
    ) {

        Sidebar(currentScreen, onNavigate)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            TopBar(
                currentScreen = currentScreen,
                userName = "Administrator",
                onLogout = { println("Logout clicked") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            content()
        }
    }
}