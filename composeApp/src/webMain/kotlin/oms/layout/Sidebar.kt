package oms.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

        SidebarItem("Dashboard", Screen.Dashboard, currentScreen, onNavigate)
        SidebarItem("Projects", Screen.Projects, currentScreen, onNavigate)
        SidebarItem("Map", Screen.Map, currentScreen, onNavigate)
        SidebarItem("Inspection Reports", Screen.Inspections, currentScreen, onNavigate)
        SidebarItem("Financial Monitoring", Screen.Financial, currentScreen, onNavigate)
        SidebarItem("Documents", Screen.Documents, currentScreen, onNavigate)

        Spacer(Modifier.weight(1f))

        Button(onClick = onLogout) {
            Text("Logout")
        }
    }
}

//
//import androidx.compose.foundation.layout.*
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.automirrored.filled.List
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.unit.dp
//import oms.usif.ua.ufsi.Screen
//
///*
//   Sidebar реалізований через Material NavigationRail.
//
//   NavigationRail — це вертикальна навігаційна панель, яка зазвичай
//   використовується у dashboard-подібних інтерфейсах. Вона підходить
//   для планшетів, десктопів та широких екранів.
//
//   У нашій реалізації Sidebar включає основні секції системи OMS:
//   - Dashboard
//   - Projects
//   - Map
//   - Inspection Reports
//*/
//
//@Composable
//fun Sidebar(
//    current: Screen,                     // Поточний екран, щоб підсвітити активний пункт
//    onNavigate: (Screen) -> Unit         // Callback для зміни екрану при натисканні
//) {
//
//    NavigationRail(
//        // Sidebar займає всю висоту екрану й фіксовану ширину
//        modifier = Modifier
//            .fillMaxHeight()
//            .width(90.dp)
//    ) {
//
//        /*
//           Заголовок системи.
//           У реальному OMS тут можна додати логотип або брендований текст.
//           Spacer додає відступ зверху.
//        */
//        Spacer(modifier = Modifier.height(12.dp))
//
//        Text(
//            text = "OMS",
//            style = MaterialTheme.typography.titleMedium
//        )
//
//        Spacer(modifier = Modifier.height(24.dp))
//
//        /*
//           Пункт меню: Dashboard
//           selected визначає, чи підсвітити пункт як активний.
//        */
//        NavigationRailItem(
//            selected = current == Screen.DASHBOARD,
//            onClick = { onNavigate(Screen.DASHBOARD) },
//            icon = {
//                Icon(
//                    Icons.Default.Dashboard,
//                    contentDescription = "Dashboard" // Для accessibility
//                )
//            },
//            label = { Text("Dashboard") }
//        )
//
//        /*
//           Пункт меню: Projects
//        */
//        NavigationRailItem(
//            selected = current == Screen.PROJECTS,
//            onClick = { onNavigate(Screen.PROJECTS) },
//            icon = {
//                Icon(
//                    Icons.AutoMirrored.Filled.List,
//                    contentDescription = "Projects"
//                )
//            },
//            label = { Text("Projects") }
//        )
//
//        /*
//           Пункт меню: Map
//           Використовується для переходу до карти проєктів або локацій.
//        */
//        NavigationRailItem(
//            selected = current == Screen.MAP,
//            onClick = { onNavigate(Screen.MAP) },
//            icon = {
//                Icon(
//                    Icons.Default.Map,
//                    contentDescription = "Map"
//                )
//            },
//            label = { Text("Map") }
//        )
//
//        /*
//           Пункт меню: Inspection Reports
//           Іконка Description використовується для документів/звіту.
//           Підсвічування залежить від поточного екрану.
//        */
//        NavigationRailItem(
//            selected = current == Screen.REPORTS,
//            onClick = { onNavigate(Screen.REPORTS) },
//            icon = {
//                Icon(
//                    Icons.Default.Description,
//                    contentDescription = "Reports"
//                )
//            },
//            label = { Text("Reports") }
//        )
//    }
//}

/*
   Пояснення ключових моментів:

   1. NavigationRailItem:
      - selected: підсвічує активний пункт
      - onClick: callback для зміни екрану
      - icon: графічне представлення пункту (Material Icons)
      - label: текстовий опис для користувача

   2. AutoMirrored іконки:
      - Використовуються для правильного відображення при RTL локалізаціях.
      - Рекомендовано для сучасних UI, щоб уникнути deprecated warning.

   3. Spacer:
      - Використовується для контролю вертикальних відступів без використання hard-coded padding у Text/Item.

   4. MaterialTheme.typography:
      - Забезпечує консистентний стиль тексту відповідно до теми (світла/темна).

   5. Modifier:
      - fillMaxHeight(): Sidebar займає всю висоту контейнера
      - width(90.dp): фіксована ширина для вертикальної навігації

   У цілому, Sidebar реалізує **чітку та масштабовану навігацію**, сумісну з Material3 і Compose.
*/