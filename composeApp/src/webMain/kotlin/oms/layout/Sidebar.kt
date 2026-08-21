package oms.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.components.LanguageSwitcher
import oms.localization.LocalizationManager
import oms.navigation.Screen

// 🔹 Sidebar згідно spec 7.2 :contentReference[oaicite:1]{index=1}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sidebar(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onLogout: () -> Unit,
    username: String,
    isAdmin: Boolean,
    isGuest: Boolean
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

        if (!isGuest) SidebarItem(
            LocalizationManager.t("dashboard"),
            Icons.Default.Dashboard,
            Screen.Dashboard,
            currentScreen,
            onNavigate
        )
        SidebarItem(
            LocalizationManager.t("projects"),
            Icons.AutoMirrored.Filled.ListAlt,
            Screen.Projects,
            currentScreen,
            onNavigate
        )
        SidebarItem(LocalizationManager.t("map"), Icons.Default.Map, Screen.Map, currentScreen, onNavigate)
        if (!isGuest) SidebarItem(
            LocalizationManager.t("inspection_reports"),
            Icons.Default.Description,
            Screen.Inspections,
            currentScreen,
            onNavigate
        )
        if (!isGuest) SidebarItem(
            LocalizationManager.t("financial_monitoring"),
            Icons.Default.AccountBalance,
            Screen.Financial,
            currentScreen,
            onNavigate
        )
        if (!isGuest) SidebarItem(LocalizationManager.t("procurement_title"), Icons.Default.ShoppingCart, Screen.Procurement, currentScreen, onNavigate)
        if (!isGuest) SidebarItem(
            LocalizationManager.t("documents"),
            Icons.Default.Description,
            Screen.Documents,
            currentScreen,
            onNavigate
        )
        if (isAdmin) SidebarItem(
            LocalizationManager.t("admin_title"),
            Icons.Default.AdminPanelSettings,
            Screen.Admin,
            currentScreen,
            onNavigate
        )
        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start
        ) {

            LanguageSwitcher()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = LocalizationManager.t("user")
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = username,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onLogout
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = LocalizationManager.t("logout")
                    )
                }
            }
        }
    }
}
