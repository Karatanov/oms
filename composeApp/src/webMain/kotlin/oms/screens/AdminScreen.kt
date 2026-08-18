package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
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
import oms.data.CreateUserRequest
import oms.components.RoleChip
import oms.components.TableActionIconButton
import oms.components.InlineOptionPicker
import oms.components.WasmSafeOverlay
import oms.localization.LocalizationManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

private enum class UserSort { Id, Username, FullName, Email, Role, Status, LastLogin, Region, Department, Language, Created }

@Composable
private fun UserStatusChip(status: String) {
    val color = when (status.lowercase()) {
        "active" -> Color(0xFF2E7D32)
        "pending" -> Color(0xFF757575)
        "disabled", "locked" -> Color(0xFFC62828)
        else -> Color(0xFF546E7A)
    }
    AssistChip(
        onClick = {},
        label = { Text(LocalizationManager.t("user_status_${status.lowercase()}").takeIf { it != "user_status_${status.lowercase()}" } ?: status) },
        colors = AssistChipDefaults.assistChipColors(containerColor = color, labelColor = Color.White)
    )
}

/** Uses vector drawing rather than flag emoji: emoji fonts are not consistently available in Wasm. */
@Composable
private fun LanguageFlag(language: String) {
    Canvas(
        modifier = Modifier
            .width(32.dp)
            .height(21.dp)
            .background(Color.White, RoundedCornerShape(3.dp))
    ) {
        if (language.equals("en", ignoreCase = true)) {
            // United Kingdom (Union Jack), simplified but recognisable at table scale.
            drawRect(Color(0xFF012169))
            drawLine(Color.White, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = size.height * .30f)
            drawLine(Color.White, start = androidx.compose.ui.geometry.Offset(size.width, 0f), end = androidx.compose.ui.geometry.Offset(0f, size.height), strokeWidth = size.height * .30f)
            drawLine(Color(0xFFC8102E), start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = size.height * .12f)
            drawLine(Color(0xFFC8102E), start = androidx.compose.ui.geometry.Offset(size.width, 0f), end = androidx.compose.ui.geometry.Offset(0f, size.height), strokeWidth = size.height * .12f)
            drawLine(Color.White, start = androidx.compose.ui.geometry.Offset(size.width / 2, 0f), end = androidx.compose.ui.geometry.Offset(size.width / 2, size.height), strokeWidth = size.height * .42f)
            drawLine(Color.White, start = androidx.compose.ui.geometry.Offset(0f, size.height / 2), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2), strokeWidth = size.height * .42f)
            drawLine(Color(0xFFC8102E), start = androidx.compose.ui.geometry.Offset(size.width / 2, 0f), end = androidx.compose.ui.geometry.Offset(size.width / 2, size.height), strokeWidth = size.height * .20f)
            drawLine(Color(0xFFC8102E), start = androidx.compose.ui.geometry.Offset(0f, size.height / 2), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2), strokeWidth = size.height * .20f)
        } else {
            // Ukraine.
            drawRect(Color(0xFF0057B7), size = androidx.compose.ui.geometry.Size(size.width, size.height / 2))
            drawRect(Color(0xFFFFDD00), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height / 2), size = androidx.compose.ui.geometry.Size(size.width, size.height / 2))
        }
        drawRect(Color(0x22000000), style = Stroke(width = 1.dp.toPx()))
    }
}

@Composable
fun AdminScreen() {
    var users by remember { mutableStateOf<List<ApiUser>>(emptyList()) }
    var roles by remember { mutableStateOf<List<ApiRole>>(emptyList()) }
    var selectedUser by remember { mutableStateOf<ApiUser?>(null) }
    var userPendingDeletion by remember { mutableStateOf<ApiUser?>(null) }
    var createUser by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(UserSort.Username) }
    var ascending by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { OmsApiClient.users() }.onSuccess { users = it }.onFailure { errorMessage = LocalizationManager.t("error_load_users") }
        roles = runCatching { OmsApiClient.roles() }.getOrDefault(emptyList())
    }
    val sortedUsers = users.sortedWith(compareBy<ApiUser> {
        when (sort) {
            UserSort.Id -> it.id.toString().padStart(12, '0')
            UserSort.Username -> it.username
            UserSort.FullName -> "${it.firstName} ${it.lastName}"
            UserSort.Email -> it.email
            UserSort.Role -> it.role.name
            UserSort.Status -> it.status
            UserSort.LastLogin -> it.lastLoginAt.orEmpty()
            UserSort.Region -> it.region.orEmpty()
            UserSort.Department -> it.department.orEmpty()
            UserSort.Language -> it.preferredLang
            UserSort.Created -> it.createdAt.orEmpty()
        }
    }.let { if (ascending) it else it.reversed() })
    fun changeSort(column: UserSort) { if (sort == column) ascending = !ascending else { sort = column; ascending = true } }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(LocalizationManager.t("admin_title"), style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { createUser = true }) { Text(LocalizationManager.t("create_user")) }
        }
        Text(LocalizationManager.t("system_users"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp).horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LocalizationManager.t("user_full_data"), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.width(2046.dp).padding(vertical = 6.dp)) {
                    SortableTableHeader(LocalizationManager.t("username"), sort == UserSort.Username, ascending, { changeSort(UserSort.Username) }, Modifier.width(130.dp))
                    SortableTableHeader(LocalizationManager.t("full_name"), sort == UserSort.FullName, ascending, { changeSort(UserSort.FullName) }, Modifier.width(180.dp))
                    SortableTableHeader(LocalizationManager.t("email"), sort == UserSort.Email, ascending, { changeSort(UserSort.Email) }, Modifier.width(220.dp))
                    SortableTableHeader(LocalizationManager.t("role"), sort == UserSort.Role, ascending, { changeSort(UserSort.Role) }, Modifier.width(150.dp))
                    SortableTableHeader(LocalizationManager.t("status"), sort == UserSort.Status, ascending, { changeSort(UserSort.Status) }, Modifier.width(110.dp))
                    SortableTableHeader(LocalizationManager.t("region"), sort == UserSort.Region, ascending, { changeSort(UserSort.Region) }, Modifier.width(130.dp))
                    SortableTableHeader(LocalizationManager.t("department"), sort == UserSort.Department, ascending, { changeSort(UserSort.Department) }, Modifier.width(150.dp))
                    SortableTableHeader(LocalizationManager.t("language"), sort == UserSort.Language, ascending, { changeSort(UserSort.Language) }, Modifier.width(80.dp))
                    SortableTableHeader(LocalizationManager.t("last_login"), sort == UserSort.LastLogin, ascending, { changeSort(UserSort.LastLogin) }, Modifier.width(170.dp))
                    SortableTableHeader(LocalizationManager.t("failed_login_attempts"), sort == UserSort.LastLogin, ascending, { changeSort(UserSort.LastLogin) }, Modifier.width(120.dp))
                    AdminStaticHeader(LocalizationManager.t("locked_until"), 170.dp)
                    SortableTableHeader(LocalizationManager.t("created_at"), sort == UserSort.Created, ascending, { changeSort(UserSort.Created) }, Modifier.width(170.dp))
                    AdminStaticHeader(LocalizationManager.t("updated_at"), 170.dp)
                    AdminStaticHeader(LocalizationManager.t("actions"), 96.dp)
                }
                HorizontalDivider()
                sortedUsers.forEach { user ->
                    Row(Modifier.width(2046.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(user.username, Modifier.width(130.dp))
                        Text(listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "—" }, Modifier.width(180.dp))
                        Text(user.email, Modifier.width(220.dp))
                        Box(Modifier.width(150.dp)) { RoleChip(user.role.code) }
                        Box(Modifier.width(110.dp)) { UserStatusChip(user.status) }
                        Text(user.region ?: "—", Modifier.width(130.dp))
                        Text(user.department ?: "—", Modifier.width(150.dp))
                        Box(Modifier.width(80.dp), contentAlignment = Alignment.CenterStart) {
                            LanguageFlag(user.preferredLang)
                        }
                        Text(user.lastLoginAt ?: "—", Modifier.width(170.dp))
                        Text(user.failedLoginCount.toString(), Modifier.width(120.dp))
                        Text(user.lockedUntil ?: "—", Modifier.width(170.dp))
                        Text(user.createdAt ?: "—", Modifier.width(170.dp))
                        Text(user.updatedAt ?: "—", Modifier.width(170.dp))
                        TableActionIconButton(LocalizationManager.t("edit_user"), Icons.Default.Edit) { selectedUser = user }
                        TableActionIconButton(LocalizationManager.t("delete_user"), Icons.Default.Delete) { userPendingDeletion = user }
                    }
                    HorizontalDivider()
                }
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
        selectedUser?.let { user -> WasmSafeOverlay {
            EditUserDialog(user, roles, errorMessage, onDismiss = { selectedUser = null }) { updated ->
                scope.launch {
                    runCatching { OmsApiClient.updateUser(user.id, updated) }
                        .onSuccess { saved -> users = users.map { if (it.id == saved.id) saved else it }; selectedUser = null }
                        .onFailure { errorMessage = LocalizationManager.t("error_save_user").replace("{message}", it.message ?: LocalizationManager.t("unknown_error")) }
                }
            }
        }
        }
        if (createUser) { WasmSafeOverlay {
            CreateUserDialog(roles, onDismiss = { createUser = false }) { request ->
                scope.launch {
                    runCatching { OmsApiClient.createUser(request) }
                        .onSuccess { created -> users = users + created; createUser = false }
                        .onFailure { errorMessage = LocalizationManager.t("error_create_user").replace("{message}", it.message ?: LocalizationManager.t("unknown_error")) }
                }
            }
        }
        }
        userPendingDeletion?.let { user -> WasmSafeOverlay {
            Card(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(LocalizationManager.t("delete_user_title"), style = MaterialTheme.typography.titleLarge)
                    Text(LocalizationManager.t("delete_user_confirmation").replace("{username}", user.username))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    OutlinedButton(onClick = { userPendingDeletion = null }) { Text(LocalizationManager.t("cancel")) }
                    Button(
                        onClick = {
                            scope.launch {
                                if (OmsApiClient.deleteUser(user.id)) {
                                    users = users.filterNot { it.id == user.id }
                                    userPendingDeletion = null
                                } else {
                                    errorMessage = LocalizationManager.t("error_delete_current_user")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text(LocalizationManager.t("delete")) }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun AdminStaticHeader(text: String, width: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.width(width).height(48.dp).padding(start = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) { Text(text) }
}

@Composable
private fun CreateUserDialog(roles: List<ApiRole>, onDismiss: () -> Unit, onSave: (CreateUserRequest) -> Unit) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var roleCode by remember { mutableStateOf(roles.firstOrNull()?.code ?: "") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("active") }
    var region by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var preferredLang by remember { mutableStateOf("uk") }
    val role = roles.firstOrNull { it.code == roleCode }
    Card(Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(LocalizationManager.t("create_user"), style = MaterialTheme.typography.titleLarge)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 510.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            OutlinedTextField(username, { username = it }, label = { Text(LocalizationManager.t("username")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text(LocalizationManager.t("email")) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(firstName, { firstName = it }, label = { Text(LocalizationManager.t("first_name")) }, modifier = Modifier.weight(1f))
                OutlinedTextField(lastName, { lastName = it }, label = { Text(LocalizationManager.t("last_name")) }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(region, { region = it }, label = { Text(LocalizationManager.t("region")) }, modifier = Modifier.weight(1f))
                OutlinedTextField(department, { department = it }, label = { Text(LocalizationManager.t("department")) }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("active", "pending", "disabled").forEach { value -> FilterChip(status == value, { status = value }, label = { Text(LocalizationManager.t("user_status_$value")) }) }
                listOf("uk", "en").forEach { value -> FilterChip(preferredLang == value, { preferredLang = value }, label = { Text(value.uppercase()) }) }
            }
            OutlinedTextField(password, { password = it }, label = { Text(LocalizationManager.t("password")) }, modifier = Modifier.fillMaxWidth())
            InlineOptionPicker(options = roles, selected = role, prompt = LocalizationManager.t("select_role"), onSelect = { roleCode = it.code }, itemLabel = { it.name })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
            Button(
                onClick = { onSave(CreateUserRequest(username.trim(), email.trim(), password, roleCode, firstName.trim(), lastName.trim(), status, region.trim().ifBlank { null }, department.trim().ifBlank { null }, preferredLang)) },
                enabled = username.isNotBlank() && email.contains('@') && password.isNotBlank() && role != null
            ) { Text(LocalizationManager.t("create")) }
            }
        }
    }
}

@Composable
private fun EditUserDialog(user: ApiUser, roles: List<ApiRole>, saveError: String?, onDismiss: () -> Unit, onSave: (UpdateUserRequest) -> Unit) {
    var username by remember(user.id) { mutableStateOf(user.username) }
    var email by remember(user.id) { mutableStateOf(user.email) }
    var roleCode by remember(user.id) { mutableStateOf(user.role.code) }
    var password by remember(user.id) { mutableStateOf("") }
    var firstName by remember(user.id) { mutableStateOf(user.firstName) }
    var lastName by remember(user.id) { mutableStateOf(user.lastName) }
    var status by remember(user.id) { mutableStateOf(user.status) }
    var region by remember(user.id) { mutableStateOf(user.region.orEmpty()) }
    var department by remember(user.id) { mutableStateOf(user.department.orEmpty()) }
    var preferredLang by remember(user.id) { mutableStateOf(user.preferredLang) }
    val role = roles.firstOrNull { it.code == roleCode }
    Card(Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(LocalizationManager.t("edit_user"), style = MaterialTheme.typography.titleLarge)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 510.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            OutlinedTextField(username, { username = it }, label = { Text(LocalizationManager.t("username")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text(LocalizationManager.t("email")) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(firstName, { firstName = it }, label = { Text(LocalizationManager.t("first_name")) }, modifier = Modifier.weight(1f))
                OutlinedTextField(lastName, { lastName = it }, label = { Text(LocalizationManager.t("last_name")) }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(region, { region = it }, label = { Text(LocalizationManager.t("region")) }, modifier = Modifier.weight(1f))
                OutlinedTextField(department, { department = it }, label = { Text(LocalizationManager.t("department")) }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("active", "pending", "disabled").forEach { value -> FilterChip(status == value, { status = value }, label = { Text(LocalizationManager.t("user_status_$value")) }) }
                listOf("uk", "en").forEach { value -> FilterChip(preferredLang == value, { preferredLang = value }, label = { Text(value.uppercase()) }) }
            }
            InlineOptionPicker(options = roles, selected = role, prompt = roleCode, onSelect = { roleCode = it.code }, itemLabel = { it.name })
            OutlinedTextField(password, { password = it }, label = { Text(LocalizationManager.t("new_password_optional")) }, modifier = Modifier.fillMaxWidth())
            Text(LocalizationManager.t("password_requirements"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
            Button(
                onClick = { onSave(UpdateUserRequest(username, email, roleCode, password.ifBlank { null }, firstName, lastName, status, region, department, preferredLang)) },
                enabled = username.isNotBlank() && email.contains('@') && role != null
            ) { Text(LocalizationManager.t("save")) }
            }
        }
    }
}
