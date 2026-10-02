package oms.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import oms.components.PageHeading
import oms.localization.LocalizationManager

/** The guest session deliberately exposes programme context, never project data. */
@Composable
fun GuestInfoScreen() {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeading(LocalizationManager.t("guest_information"), Icons.Default.Info)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(LocalizationManager.t("guest_programme_title"), style = MaterialTheme.typography.headlineSmall)
                Text(LocalizationManager.t("guest_programme_description"), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(LocalizationManager.t("guest_access_notice"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LocalizationManager.t("guest_contacts_title"), style = MaterialTheme.typography.titleLarge)
                Text(LocalizationManager.t("guest_contacts_description"))
                TextButton(
                    onClick = { uriHandler.openUri("https://mindev.gov.ua/") },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
                ) {
                    Text(LocalizationManager.t("guest_contacts_link"))
                    androidx.compose.material3.Icon(Icons.Default.OpenInNew, null, Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
