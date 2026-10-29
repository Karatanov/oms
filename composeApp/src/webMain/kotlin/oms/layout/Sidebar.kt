package oms.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import oms.components.LanguageSwitcher
import oms.components.TableActionIconButton
import oms.localization.LocalizationManager
import oms.navigation.Screen

@Composable
fun Sidebar(currentScreen: Screen, onNavigate: (Screen) -> Unit, onLogout: () -> Unit,
    username: String, isAdmin: Boolean, isGuest: Boolean, canAccessFinancials: Boolean,
    compact: Boolean, onToggle: () -> Unit, toggleLabel: String? = null, onFeedback: () -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        // Keep a usable content lane on phone-sized browser windows.  The
        // compact rail retains all navigation through icons and tooltips.
        Column(Modifier.width(if (compact) 72.dp else 248.dp).fillMaxHeight().padding(if (compact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().height(if (compact) 52.dp else 76.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!compact) {
                    Icon(Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.primary)
                    Column(
                        Modifier.weight(1f).padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "UR III",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "TA to the implementation\nof UR III Programme",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
                TableActionIconButton(toggleLabel ?: LocalizationManager.t(if (compact) "expand_navigation" else "collapse_navigation"),
                    if (compact) Icons.Default.Menu else Icons.Default.MenuOpen, onToggle)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isGuest) {
                    SidebarItem(LocalizationManager.t("dashboard"), Icons.Default.Dashboard, Screen.Dashboard, currentScreen, onNavigate, compact)
                    SidebarItem(LocalizationManager.t("map"), Icons.Default.Map, Screen.Map, currentScreen, onNavigate, compact)
                } else {
                    SidebarItem(LocalizationManager.t("dashboard"), Icons.Default.Dashboard, Screen.Dashboard, currentScreen, onNavigate, compact)
                    SidebarItem(LocalizationManager.t("projects"), Icons.AutoMirrored.Filled.ListAlt, Screen.Projects, currentScreen, onNavigate, compact)
                    SidebarItem(LocalizationManager.t("map"), Icons.Default.Map, Screen.Map, currentScreen, onNavigate, compact)
                }
                if (!isGuest) {
                    SidebarItem(LocalizationManager.t("inspection_reports"), Icons.Default.FactCheck, Screen.Inspections, currentScreen, onNavigate, compact)
                    if (canAccessFinancials) SidebarItem(LocalizationManager.t("financial_monitoring"), Icons.Default.AccountBalance, Screen.Financial, currentScreen, onNavigate, compact, iconText = "€")
                    SidebarItem(LocalizationManager.t("procurement_title"), Icons.Default.ShoppingCart, Screen.Procurement, currentScreen, onNavigate, compact)
                    SidebarItem(LocalizationManager.t("documents"), Icons.Default.FolderOpen, Screen.Documents, currentScreen, onNavigate, compact)
                }
                if (isAdmin) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    SidebarItem(LocalizationManager.t("admin_title"), Icons.Default.AdminPanelSettings, Screen.Admin, currentScreen, onNavigate, compact)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (compact) TableActionIconButton(LocalizationManager.t("language"), Icons.Default.Language, LocalizationManager::switchLanguage)
            else LanguageSwitcher()
            Row(Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!compact) Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(username, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(LocalizationManager.t(if (isGuest) "guest_access" else "signed_in"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TableActionIconButton(LocalizationManager.t("logout"), Icons.AutoMirrored.Filled.Logout, onLogout)
            }
            if (compact) TableActionIconButton(LocalizationManager.t("feedback_title"), Icons.Default.Feedback, onFeedback)
            else OutlinedButton(onClick = onFeedback, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                Icon(Icons.Default.Feedback, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(LocalizationManager.t("feedback_title"))
            }
        }
    }
}
