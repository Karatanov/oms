package oms.usif.ua.ufsi.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.usif.ua.ufsi.Screen

/*
   Верхня панель системи (Admin TopBar)

   Складається з трьох частин:

   1) Breadcrumb navigation
   2) User info
   3) Logout button

   Така структура використовується майже у всіх
   корпоративних dashboard системах.
*/

@Composable
fun TopBar(

    currentScreen: Screen,

    userName: String = "Admin",

    onLogout: () -> Unit = {}

) {

    Surface(
        shadowElevation = 4.dp
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),

            verticalAlignment = Alignment.CenterVertically
        ) {

            /*
               -------- Breadcrumb --------

               Відображає поточну сторінку.
               Наприклад:
               Dashboard
               Projects
               Projects / Details
            */

            Breadcrumb(currentScreen)

            Spacer(modifier = Modifier.weight(1f))

            /*
               -------- User info --------
            */

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    Icons.Default.Person,
                    contentDescription = "User"
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = userName,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.width(16.dp))

                /*
                   -------- Logout --------
                */

                IconButton(
                    onClick = onLogout
                ) {

                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout"
                    )
                }
            }
        }
    }
}

/*
   Компонент Breadcrumb.

   Визначає назву сторінки
   на основі Screen enum.
*/

@Composable
fun Breadcrumb(

    screen: Screen

) {

    val title = when (screen) {

        Screen.DASHBOARD -> "Dashboard"

        Screen.PROJECTS -> "Projects"

        Screen.MAP -> "Projects Map"

        Screen.REPORTS -> "Inspection Reports"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = "OMS",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "/",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
    }
}


//package oms.usif.ua.ufsi.layout
//
//import androidx.compose.foundation.layout.*
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Person
//import androidx.compose.material3.*
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.unit.dp
//
///*
//   Верхня панель системи (Header).
//
//   У реальній OMS тут знаходяться:
//   - назва системи
//   - користувач
//   - logout
//   - мова
//*/
//
//@Composable
//fun TopBar() {
//
//    // Surface створює фон панелі
//    Surface(
//        shadowElevation = 4.dp
//    ) {
//
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(12.dp),
//
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//
//            // Назва системи
//            Text(
//                text = "UFSI. Operations Monitoring System",
//                style = MaterialTheme.typography.titleLarge
//            )
//
//            // Spacer розсовує елементи
//            Spacer(modifier = Modifier.weight(1f))
//
//            // Кнопка користувача (поки заглушка)
//            Button(
//                onClick = { /* TODO: обробка натискання користувача */ },
//                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
//            ) {
//                Row(
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    // Іконка користувача
//                    Icon(
//                        imageVector = Icons.Filled.Person,
//                        contentDescription = "User Icon"
//                    )
//
//                    // Текст кнопки
//                    Text("User")
//                }
//            }
//        }
//    }
//}