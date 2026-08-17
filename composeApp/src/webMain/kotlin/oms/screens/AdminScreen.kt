package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
        label = { Text(status.replaceFirstChar(Char::uppercase)) },
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
    var createUser by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(UserSort.Username) }
    var ascending by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { OmsApiClient.users() }.onSuccess { users = it }.onFailure { errorMessage = "Could not load users." }
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

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Адміністрування", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { createUser = true }) { Text("Створити користувача") }
        }
        Text("Користувачі системи", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp).horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Повні дані користувачів", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.width(2046.dp).padding(vertical = 6.dp)) {
                    SortableTableHeader("Логін", sort == UserSort.Username, ascending, { changeSort(UserSort.Username) }, Modifier.width(130.dp))
                    SortableTableHeader("Ім'я", sort == UserSort.FullName, ascending, { changeSort(UserSort.FullName) }, Modifier.width(180.dp))
                    SortableTableHeader("Email", sort == UserSort.Email, ascending, { changeSort(UserSort.Email) }, Modifier.width(220.dp))
                    SortableTableHeader("Роль", sort == UserSort.Role, ascending, { changeSort(UserSort.Role) }, Modifier.width(150.dp))
                    SortableTableHeader("Статус", sort == UserSort.Status, ascending, { changeSort(UserSort.Status) }, Modifier.width(110.dp))
                    SortableTableHeader("Регіон", sort == UserSort.Region, ascending, { changeSort(UserSort.Region) }, Modifier.width(130.dp))
                    SortableTableHeader("Відділ", sort == UserSort.Department, ascending, { changeSort(UserSort.Department) }, Modifier.width(150.dp))
                    SortableTableHeader("Мова", sort == UserSort.Language, ascending, { changeSort(UserSort.Language) }, Modifier.width(80.dp))
                    SortableTableHeader("Останній вхід", sort == UserSort.LastLogin, ascending, { changeSort(UserSort.LastLogin) }, Modifier.width(170.dp))
                    SortableTableHeader("Невдалі входи", sort == UserSort.LastLogin, ascending, { changeSort(UserSort.LastLogin) }, Modifier.width(120.dp))
                    Text("Блокування до", Modifier.width(170.dp), style = MaterialTheme.typography.labelLarge)
                    SortableTableHeader("Створено", sort == UserSort.Created, ascending, { changeSort(UserSort.Created) }, Modifier.width(170.dp))
                    Text("Оновлено", Modifier.width(170.dp), style = MaterialTheme.typography.labelLarge)
                    Text("Дії", Modifier.width(96.dp), style = MaterialTheme.typography.labelLarge)
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
                        TableActionIconButton("Редагувати користувача", Icons.Default.Edit) { selectedUser = user }
                        TableActionIconButton("Видалити користувача", Icons.Default.Delete) {
                            scope.launch {
                                if (OmsApiClient.deleteUser(user.id)) users = users.filterNot { it.id == user.id }
                                else errorMessage = "Не вдалося видалити користувача. Неможливо видалити поточний обліковий запис."
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        selectedUser?.let { user ->
            EditUserDialog(user, roles, errorMessage, onDismiss = { selectedUser = null }) { updated ->
                scope.launch {
                    runCatching { OmsApiClient.updateUser(user.id, updated) }
                        .onSuccess { saved -> users = users.map { if (it.id == saved.id) saved else it }; selectedUser = null }
                        .onFailure { errorMessage = "Could not save user: ${it.message ?: "unknown error"}" }
                }
            }
        }
        if (createUser) {
            CreateUserDialog(roles, onDismiss = { createUser = false }) { request ->
                scope.launch {
                    runCatching { OmsApiClient.createUser(request) }
                        .onSuccess { created -> users = users + created; createUser = false }
                        .onFailure { errorMessage = "Could not create user: ${it.message ?: "unknown error"}" }
                }
            }
        }
    }
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
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Створити користувача", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(username, { username = it }, label = { Text("Логін") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(firstName, { firstName = it }, label = { Text("Ім'я") }, modifier = Modifier.weight(1f))
                OutlinedTextField(lastName, { lastName = it }, label = { Text("Прізвище") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(region, { region = it }, label = { Text("Регіон") }, modifier = Modifier.weight(1f))
                OutlinedTextField(department, { department = it }, label = { Text("Відділ") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("active", "pending", "disabled").forEach { value -> FilterChip(status == value, { status = value }, label = { Text(value) }) }
                listOf("uk", "en").forEach { value -> FilterChip(preferredLang == value, { preferredLang = value }, label = { Text(value.uppercase()) }) }
            }
            OutlinedTextField(password, { password = it }, label = { Text("Пароль") }, modifier = Modifier.fillMaxWidth())
            InlineOptionPicker(options = roles, selected = role, prompt = "Оберіть роль", onSelect = { roleCode = it.code }, itemLabel = { it.name })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text("Скасувати") }
                Button(onClick = { onSave(CreateUserRequest(username.trim(), email.trim(), password, roleCode, firstName.trim(), lastName.trim(), status, region.trim().ifBlank { null }, department.trim().ifBlank { null }, preferredLang)) }, enabled = username.isNotBlank() && email.contains('@') && password.isNotBlank() && role != null) { Text("Створити") }
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
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Редагувати користувача", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(username, { username = it }, label = { Text("Логін") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(firstName, { firstName = it }, label = { Text("Ім'я") }, modifier = Modifier.weight(1f))
                OutlinedTextField(lastName, { lastName = it }, label = { Text("Прізвище") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(region, { region = it }, label = { Text("Регіон") }, modifier = Modifier.weight(1f))
                OutlinedTextField(department, { department = it }, label = { Text("Відділ") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("active", "pending", "disabled").forEach { value -> FilterChip(status == value, { status = value }, label = { Text(value) }) }
                listOf("uk", "en").forEach { value -> FilterChip(preferredLang == value, { preferredLang = value }, label = { Text(value.uppercase()) }) }
            }
            InlineOptionPicker(options = roles, selected = role, prompt = roleCode, onSelect = { roleCode = it.code }, itemLabel = { it.name })
            OutlinedTextField(password, { password = it }, label = { Text("Новий пароль (необов'язково)") }, modifier = Modifier.fillMaxWidth())
            Text(LocalizationManager.t("password_requirements"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text("Скасувати") }
                Button(onClick = { onSave(UpdateUserRequest(username, email, roleCode, password.ifBlank { null }, firstName, lastName, status, region.ifBlank { null }, department.ifBlank { null }, preferredLang)) }, enabled = username.isNotBlank() && email.contains('@') && role != null) { Text("Зберегти") }
            }
        }
    }
}
