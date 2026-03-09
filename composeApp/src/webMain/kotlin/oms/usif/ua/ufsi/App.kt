package oms.usif.ua.ufsi

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import oms.usif.ua.ufsi.layout.MainLayout
import oms.usif.ua.ufsi.screens.DashboardScreen
import oms.usif.ua.ufsi.screens.MapScreen
import oms.usif.ua.ufsi.screens.ProjectsScreen
import oms.usif.ua.ufsi.screens.ReportsScreen
import org.jetbrains.compose.resources.painterResource

import ufsi.composeapp.generated.resources.Res
import ufsi.composeapp.generated.resources.compose_multiplatform

/*
   Enum описує всі сторінки системи.
   Це спрощений router для нашого UI.
*/
enum class Screen {
    DASHBOARD,
    PROJECTS,
    MAP,
    REPORTS
}


@Composable
fun App() {
    MaterialTheme {

        // Змінна стану Compose.
        // Вона визначає яка сторінка зараз відображається.
        var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

        /*
           MainLayout — це загальний каркас системи:
           Sidebar + TopBar + Content area
        */
        MainLayout(
            currentScreen = currentScreen,

            // callback для навігації
            onNavigate = { screen ->
                currentScreen = screen
            }

        ) {

            // Відображаємо потрібний екран
            when (currentScreen) {

                Screen.DASHBOARD -> DashboardScreen()

                Screen.PROJECTS -> ProjectsScreen()

                Screen.MAP -> MapScreen()

                Screen.REPORTS -> ReportsScreen()
            }
        }
    }
}