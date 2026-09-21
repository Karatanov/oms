package oms.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import oms.components.toOmsDateTime
import oms.components.buttonHandCursor
import oms.localization.LocalizationManager
import oms.data.ApiActivity
import oms.data.ApiUser


@Composable
fun ActivitySection(activities: List<ApiActivity>, users: List<ApiUser>) {
    var userSearch by remember { mutableStateOf("") }
    var userSortAscending by remember { mutableStateOf<Boolean?>(null) }
    val usersByLogin = users.associateBy { it.username.lowercase() }

    fun userLabel(activity: ApiActivity): String {
        val login = activity.userLogin.orEmpty()
        val user = usersByLogin[login.lowercase()]
        val fullName = user?.let {
            listOf(it.firstName, it.lastName).filter(String::isNotBlank).joinToString(" ")
        }.orEmpty()
        return when {
            fullName.isNotBlank() && login.isNotBlank() -> "$fullName · $login"
            fullName.isNotBlank() -> fullName
            login.isNotBlank() -> login
            else -> "—"
        }
    }

    val matchingActivities = activities.filter { activity ->
        userSearch.isBlank() || userLabel(activity).contains(userSearch.trim(), ignoreCase = true)
    }
    val visibleActivities = when (userSortAscending) {
        true -> matchingActivities.sortedBy { userLabel(it).lowercase() }
        false -> matchingActivities.sortedByDescending { userLabel(it).lowercase() }
        null -> matchingActivities
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = LocalizationManager.t("recent_activity"),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = userSearch,
                    onValueChange = { userSearch = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(LocalizationManager.t("activity_user_search")) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                OutlinedButton(
                    onClick = { userSortAscending = userSortAscending?.not() ?: true },
                    modifier = Modifier.height(56.dp).buttonHandCursor()
                ) {
                    Icon(Icons.Default.SortByAlpha, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        LocalizationManager.t(
                            when (userSortAscending) {
                                null -> "activity_user_sort"
                                true -> "activity_user_sort_ascending"
                                false -> "activity_user_sort_descending"
                            }
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (activities.isEmpty()) {
                Text(LocalizationManager.t("no_recent_activity"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (visibleActivities.isEmpty()) {
                Text(LocalizationManager.t("no_activity_search_results"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                visibleActivities.forEach { activity ->
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    ActivityRow(
                        activity.action.toActivityLabel(),
                        userLabel(activity),
                        activity.createdAt.toOmsDateTime().ifBlank { activity.createdAt.replace('T', ' ') }
                    )
                }
            }
        }
    }
}

private fun String.toActivityLabel(): String = when (this) {
    "project_created" -> LocalizationManager.t("activity_project_created")
    "project_deleted" -> LocalizationManager.t("activity_project_deleted")
    "inspection_imported" -> LocalizationManager.t("activity_inspection_imported")
    "inspection_manual_created" -> LocalizationManager.t("activity_inspection_manual_created")
    "inspection_manual_updated" -> LocalizationManager.t("activity_inspection_manual_updated")
    "inspection_status_updated" -> LocalizationManager.t("activity_inspection_status_updated")
    "inspection_source_file_replaced" -> LocalizationManager.t("activity_inspection_source_file_replaced")
    "financial_record_created" -> LocalizationManager.t("activity_financial_created")
    "financial_records_imported" -> LocalizationManager.t("activity_financial_imported")
    "financial_record_moved" -> LocalizationManager.t("activity_financial_moved")
    "projects_bulk_status_updated" -> LocalizationManager.t("activity_projects_bulk_status_updated")
    "projects_bulk_reassigned" -> LocalizationManager.t("activity_projects_bulk_reassigned")
    "user_created" -> LocalizationManager.t("activity_user_created")
    "user_updated" -> LocalizationManager.t("activity_user_updated")
    "user_status_changed" -> LocalizationManager.t("activity_user_status_changed")
    "user_role_changed" -> LocalizationManager.t("activity_user_role_changed")
    "user_password_reset" -> LocalizationManager.t("activity_user_password_reset")
    "user_deleted" -> LocalizationManager.t("activity_user_deleted")
    "user_activated" -> LocalizationManager.t("activity_user_activated")
    else -> this
}
