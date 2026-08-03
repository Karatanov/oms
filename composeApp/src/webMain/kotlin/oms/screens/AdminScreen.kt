package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oms.components.SortableTableHeader
import oms.data.ApiRole
import oms.data.ApiUser
import oms.data.OmsApiClient
import oms.data.UpdateUserRequest
import oms.components.RoleChip
import oms.components.TableActionIconButton

private enum class UserSort { Id, Username, Email, Role }

@Composable
fun AdminScreen() {
    var users by remember { mutableStateOf<List<ApiUser>>(emptyList()) }
    var roles by remember { mutableStateOf<List<ApiRole>>(emptyList()) }
    var selectedUser by remember { mutableStateOf<ApiUser?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(UserSort.Username) }
    var ascending by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { OmsApiClient.users() }.onSuccess { users = it }.onFailure { errorMessage = "Could not load users." }
        roles = runCatching { OmsApiClient.roles() }.getOrDefault(emptyList())
    }
    val sortedUsers = users.sortedWith(compareBy<ApiUser> {
        when (sort) { UserSort.Id -> it.id.toString().padStart(12, '0'); UserSort.Username -> it.username; UserSort.Email -> it.email; UserSort.Role -> it.role.name }
    }.let { if (ascending) it else it.reversed() })
    fun changeSort(column: UserSort) { if (sort == column) ascending = !ascending else { sort = column; ascending = true } }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Адміністрування", style = MaterialTheme.typography.headlineMedium)
        Text("Користувачі системи", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    SortableTableHeader("ID", sort == UserSort.Id, ascending, { changeSort(UserSort.Id) }, Modifier.width(85.dp))
                    SortableTableHeader("Логін", sort == UserSort.Username, ascending, { changeSort(UserSort.Username) }, Modifier.weight(1f))
                    SortableTableHeader("Email", sort == UserSort.Email, ascending, { changeSort(UserSort.Email) }, Modifier.weight(1.4f))
                    SortableTableHeader("Роль", sort == UserSort.Role, ascending, { changeSort(UserSort.Role) }, Modifier.width(150.dp))
                    Text("Дії", Modifier.width(48.dp), style = MaterialTheme.typography.labelLarge)
                }
                HorizontalDivider()
                if (sortedUsers.isEmpty()) Text("Користувачів не знайдено.")
                sortedUsers.forEach { user ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(user.id.toString(), Modifier.width(85.dp))
                        Text(user.username, Modifier.weight(1f))
                        Text(user.email, Modifier.weight(1.4f))
                        Box(Modifier.width(150.dp)) { RoleChip(user.role.code) }
                        TableActionIconButton("Редагувати користувача", Icons.Default.Edit) { selectedUser = user }
                    }
                    HorizontalDivider()
                }
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        selectedUser?.let { user ->
            EditUserDialog(user, roles, onDismiss = { selectedUser = null }) { updated ->
                scope.launch {
                    runCatching { OmsApiClient.updateUser(user.id, updated) }
                        .onSuccess { saved -> users = users.map { if (it.id == saved.id) saved else it }; selectedUser = null }
                        .onFailure { errorMessage = "Could not save user: ${it.message ?: "unknown error"}" }
                }
            }
        }
    }
}

@Composable
private fun EditUserDialog(user: ApiUser, roles: List<ApiRole>, onDismiss: () -> Unit, onSave: (UpdateUserRequest) -> Unit) {
    var username by remember(user.id) { mutableStateOf(user.username) }
    var email by remember(user.id) { mutableStateOf(user.email) }
    var roleCode by remember(user.id) { mutableStateOf(user.role.code) }
    var password by remember(user.id) { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val role = roles.firstOrNull { it.code == roleCode }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Редагувати користувача", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(username, { username = it }, label = { Text("Логін") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(role?.name ?: roleCode) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    roles.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { roleCode = item.code; expanded = false }) }
                }
            }
            OutlinedTextField(password, { password = it }, label = { Text("Новий пароль (необов'язково)") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = { expanded = false; onDismiss() }) { Text("Скасувати") }
                Button(onClick = { onSave(UpdateUserRequest(username, email, roleCode, password.ifBlank { null })) }, enabled = username.isNotBlank() && email.contains('@') && role != null) { Text("Зберегти") }
            }
        }
    }
}
