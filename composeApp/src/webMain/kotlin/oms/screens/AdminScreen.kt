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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import oms.components.SortableTableHeader
import oms.data.ApiRole
import oms.data.ApiUser
import oms.data.ApiActivity
import oms.data.OmsApiClient
import oms.data.UpdateUserRequest
import oms.data.CreateUserRequest
import oms.components.RoleChip
import oms.components.TableActionIconButton
import oms.components.toOmsDateTime
import oms.components.InlineOptionPicker
import oms.components.WasmSafeOverlay
import oms.localization.LocalizationManager
import oms.screens.dashboard.ActivitySection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

private enum class UserSort { Id, Username, FullName, Email, Role, Status, LastLogin, Region, Department, Language, Created, FailedAttempts, LockedUntil, Updated }

@Composable
private fun UserStatusChip(status: String) {
    val color = when (status.lowercase()) {
        "active" -> Color(0xFF2E7D32)
        "pending" -> Color(0xFF757575)
        "disabled", "locked" -> Color(0xFFC62828)
        else -> Color(0xFF546E7A)
    }
    oms.components.OmsBadge(LocalizationManager.t("user_status_${status.lowercase()}").takeIf { it != "user_status_${status.lowercase()}" } ?: status, color)
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
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var users by remember { mutableStateOf<List<ApiUser>>(emptyList()) }
    var archiveFilter by remember { mutableStateOf("active") }
    var roles by remember { mutableStateOf<List<ApiRole>>(emptyList()) }
    var activities by remember { mutableStateOf<List<ApiActivity>>(emptyList()) }
    var selectedUser by remember { mutableStateOf<ApiUser?>(null) }
    var userPendingDeletion by remember { mutableStateOf<ApiUser?>(null) }
    var userPendingPermanentDeletion by remember { mutableStateOf<ApiUser?>(null) }
    var permanentDeleteDependencies by remember { mutableStateOf<Map<String, Long>?>(null) }
    var createUser by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var editUserError by remember { mutableStateOf<String?>(null) }
    var savingUser by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(UserSort.Username) }
    var ascending by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val pageScrollState = rememberScrollState()
    fun refreshActivities() {
        scope.launch {
            activities = runCatching { OmsApiClient.dashboard().activities }.getOrDefault(activities)
        }
    }
    LaunchedEffect(archiveFilter) {
        val (loadedUsers, loadedRoles, loadedActivities) = coroutineScope {
            val usersRequest = async { runCatching { OmsApiClient.users(archiveFilter) } }
            val rolesRequest = async { runCatching { OmsApiClient.roles() } }
            val activitiesRequest = async { runCatching { OmsApiClient.dashboard().activities } }
            Triple(usersRequest.await(), rolesRequest.await(), activitiesRequest.await())
        }
        loadedUsers.onSuccess { users = it }.onFailure { errorMessage = LocalizationManager.t("error_load_users") }
        roles = loadedRoles.getOrDefault(emptyList())
        activities = loadedActivities.getOrDefault(emptyList())
        loading = false
    }
    val sortedUsers = users.filter { search.isBlank() || listOf(it.username, it.email, it.firstName, it.lastName).any { value -> value.contains(search, true) } }.sortedWith(compareBy<ApiUser> {
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
            UserSort.FailedAttempts -> it.failedLoginCount.toString().padStart(12, '0')
            UserSort.LockedUntil -> it.lockedUntil.orEmpty()
            UserSort.Updated -> it.updatedAt.orEmpty()
        }
    }.let { if (ascending) it else it.reversed() })
    fun changeSort(column: UserSort) { if (sort == column) ascending = !ascending else { sort = column; ascending = true } }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(pageScrollState).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        oms.components.PageHeading(LocalizationManager.t("admin_title"), Icons.Default.AdminPanelSettings) {
            Button(onClick = { errorMessage = null; createUser = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(LocalizationManager.t("create_user"))
            }
        }
        OutlinedTextField(search, { search = it }, singleLine = true, label = { Text(LocalizationManager.t("admin_search")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("active", "archived", "all").forEach { filter ->
                oms.screens.FilterChip(archiveFilter == filter, { archiveFilter = filter }, { Text(LocalizationManager.t("archive_filter_$filter")) })
            }
        }
        if (loading) oms.components.ContentState(LocalizationManager.t("loading_records"), loading = true)
        if (!loading && sortedUsers.isEmpty()) oms.components.ContentState(LocalizationManager.t("no_search_results"))
        // Unlike Card, a background does not clip the sticky table header.
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            oms.components.ScrollableTable(
                Modifier.padding(16.dp),
                pageScrollState = pageScrollState,
                header = {
                    Row(Modifier.width(2046.dp).padding(vertical = 6.dp)) {
                        SortableTableHeader(LocalizationManager.t("username"), sort == UserSort.Username, ascending, { changeSort(UserSort.Username) }, Modifier.width(130.dp))
                        AdminStaticHeader(LocalizationManager.t("actions"), 96.dp)
                        SortableTableHeader(LocalizationManager.t("full_name"), sort == UserSort.FullName, ascending, { changeSort(UserSort.FullName) }, Modifier.width(180.dp))
                        SortableTableHeader(LocalizationManager.t("email"), sort == UserSort.Email, ascending, { changeSort(UserSort.Email) }, Modifier.width(220.dp))
                        SortableTableHeader(LocalizationManager.t("role"), sort == UserSort.Role, ascending, { changeSort(UserSort.Role) }, Modifier.width(150.dp))
                        SortableTableHeader(LocalizationManager.t("status"), sort == UserSort.Status, ascending, { changeSort(UserSort.Status) }, Modifier.width(110.dp))
                        SortableTableHeader(LocalizationManager.t("region"), sort == UserSort.Region, ascending, { changeSort(UserSort.Region) }, Modifier.width(130.dp))
                        SortableTableHeader(LocalizationManager.t("department"), sort == UserSort.Department, ascending, { changeSort(UserSort.Department) }, Modifier.width(150.dp))
                        SortableTableHeader(LocalizationManager.t("language"), sort == UserSort.Language, ascending, { changeSort(UserSort.Language) }, Modifier.width(80.dp))
                        SortableTableHeader(LocalizationManager.t("last_login"), sort == UserSort.LastLogin, ascending, { changeSort(UserSort.LastLogin) }, Modifier.width(170.dp))
                        SortableTableHeader(LocalizationManager.t("failed_login_attempts"), sort == UserSort.FailedAttempts, ascending, { changeSort(UserSort.FailedAttempts) }, Modifier.width(120.dp))
                        SortableTableHeader(LocalizationManager.t("locked_until"), sort == UserSort.LockedUntil, ascending, { changeSort(UserSort.LockedUntil) }, Modifier.width(170.dp))
                        SortableTableHeader(LocalizationManager.t("created_at"), sort == UserSort.Created, ascending, { changeSort(UserSort.Created) }, Modifier.width(170.dp))
                        SortableTableHeader(LocalizationManager.t("updated_at"), sort == UserSort.Updated, ascending, { changeSort(UserSort.Updated) }, Modifier.width(170.dp))
                    }
                    HorizontalDivider()
                }
            ) {
                sortedUsers.forEach { user ->
                    Row(Modifier.width(2046.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(user.username, Modifier.width(130.dp))
                        TableActionIconButton(LocalizationManager.t("edit_user"), Icons.Default.Edit) {
                            editUserError = null
                            savingUser = false
                            selectedUser = user
                        }
                        if (user.isArchived) TableActionIconButton(LocalizationManager.t("restore"), Icons.Default.Unarchive) {
                            scope.launch { if (OmsApiClient.restoreUser(user.id)) { users = users.filterNot { it.id == user.id }; refreshActivities() } }
                        } else TableActionIconButton(LocalizationManager.t("archive"), Icons.Default.Delete) { userPendingDeletion = user }
                        if (user.isArchived) TableActionIconButton(LocalizationManager.t("permanently_delete"), Icons.Default.DeleteForever) {
                            userPendingPermanentDeletion = user
                            permanentDeleteDependencies = null
                            scope.launch {
                                permanentDeleteDependencies = runCatching { OmsApiClient.userPermanentDeleteDependencies(user.id) }
                                    .getOrDefault(mapOf("unable to verify dependencies" to 1L))
                            }
                        }
                        Text(listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "—" }, Modifier.width(180.dp))
                        Text(user.email, Modifier.width(220.dp))
                        Box(Modifier.width(150.dp)) { RoleChip(user.role.code) }
                        Box(Modifier.width(110.dp)) { UserStatusChip(user.status) }
                        Text(user.region ?: "—", Modifier.width(130.dp))
                        Text(user.department ?: "—", Modifier.width(150.dp))
                        Box(Modifier.width(80.dp), contentAlignment = Alignment.CenterStart) {
                            LanguageFlag(user.preferredLang)
                        }
                        Text(user.lastLoginAt.toOmsDateTime().ifBlank { "—" }, Modifier.width(170.dp))
                        Text(user.failedLoginCount.toString(), Modifier.width(120.dp))
                        Text(user.lockedUntil.toOmsDateTime().ifBlank { "—" }, Modifier.width(170.dp))
                        Text(user.createdAt.toOmsDateTime().ifBlank { "—" }, Modifier.width(170.dp))
                        Text(user.updatedAt.toOmsDateTime().ifBlank { "—" }, Modifier.width(170.dp))
                    }
                    HorizontalDivider()
                }
            }
        }
        ActivitySection(activities, users)
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    selectedUser?.let { user -> WasmSafeOverlay(onDismiss = {
            if (!savingUser) {
                selectedUser = null
                editUserError = null
            }
        }, errorMessage = null) {
            EditUserDialog(user, roles, editUserError, savingUser, onDismiss = {
                if (!savingUser) {
                    editUserError = null
                    selectedUser = null
                }
            }) { updated ->
                savingUser = true
                editUserError = null
                scope.launch {
                    runCatching { OmsApiClient.updateUser(user.id, updated) }
                        .onSuccess { saved ->
                            users = users.map { if (it.id == saved.id) saved else it }
                            refreshActivities()
                            editUserError = null
                            selectedUser = null
                        }
                        .onFailure {
                            editUserError = LocalizationManager.t("error_save_user")
                                .replace("{message}", it.message ?: LocalizationManager.t("unknown_error"))
                        }
                    savingUser = false
                }
            }
        }
        }
    if (createUser) { WasmSafeOverlay(onDismiss = { createUser = false }, errorMessage = errorMessage) {
            CreateUserDialog(roles, onDismiss = { createUser = false }) { request ->
                scope.launch {
                    runCatching { OmsApiClient.createUser(request) }
                        .onSuccess { created -> users = users + created; createUser = false; refreshActivities() }
                        .onFailure { errorMessage = LocalizationManager.t("error_create_user").replace("{message}", it.message ?: LocalizationManager.t("unknown_error")) }
                }
            }
        }
        }
    userPendingDeletion?.let { user -> WasmSafeOverlay(onDismiss = { userPendingDeletion = null }, errorMessage = errorMessage) {
            Card(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(LocalizationManager.t("archive"), style = MaterialTheme.typography.titleLarge)
                    Text(LocalizationManager.t("archive_confirmation"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    OutlinedButton(onClick = { userPendingDeletion = null }) { Text(LocalizationManager.t("cancel")) }
                    Button(
                        onClick = {
                            scope.launch {
                                if (OmsApiClient.archiveUser(user.id)) {
                                    users = users.filterNot { it.id == user.id }
                                    userPendingDeletion = null
                                    refreshActivities()
                                } else {
                                    errorMessage = LocalizationManager.t("error_delete_current_user")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text(LocalizationManager.t("archive")) }
                    }
                }
            }
        }
        }
    userPendingPermanentDeletion?.let { user -> WasmSafeOverlay(onDismiss = { userPendingPermanentDeletion = null }, errorMessage = errorMessage) {
            Card(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(LocalizationManager.t("permanently_delete"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                    Text(LocalizationManager.t("permanent_delete_warning"))
                    when (val dependencies = permanentDeleteDependencies) {
                        null -> CircularProgressIndicator()
                        else -> if (dependencies.isEmpty()) Text(LocalizationManager.t("no_delete_dependencies"))
                        else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(LocalizationManager.t("delete_dependencies_found"), fontWeight = FontWeight.SemiBold)
                            dependencies.forEach { (name, count) -> Text("• $count $name") }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        OutlinedButton(onClick = { userPendingPermanentDeletion = null }) { Text(LocalizationManager.t("cancel")) }
                        Button(
                            enabled = permanentDeleteDependencies?.isEmpty() == true,
                            onClick = {
                                scope.launch {
                                    if (OmsApiClient.permanentlyDeleteUser(user.id)) {
                                        users = users.filterNot { it.id == user.id }
                                        userPendingPermanentDeletion = null
                                        refreshActivities()
                                    } else errorMessage = LocalizationManager.t("permanent_delete_failed")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text(LocalizationManager.t("permanently_delete")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminStaticHeader(text: String, width: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.width(width).height(oms.theme.OmsDimensions.TableHeaderHeight).padding(start = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}

@Composable
private fun CreateUserDialog(roles: List<ApiRole>, onDismiss: () -> Unit, onSave: (CreateUserRequest) -> Unit) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var roleCode by remember { mutableStateOf(roles.firstOrNull()?.code ?: "") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var preferredLang by remember { mutableStateOf("uk") }
    val role = roles.firstOrNull { it.code == roleCode }
    Card(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(LocalizationManager.t("language"), style = MaterialTheme.typography.bodyMedium)
                listOf("uk", "en").forEach { value -> FilterChip(preferredLang == value, { preferredLang = value }, label = { Text(value.uppercase()) }) }
            }
            InlineOptionPicker(options = roles, selected = role, prompt = LocalizationManager.t("select_role"), onSelect = { roleCode = it.code }, itemLabel = { LocalizationManager.t("role_${it.code.lowercase()}") })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
            Button(
                onClick = {
                    onSave(
                        CreateUserRequest(
                            username = username.trim(),
                            email = email.trim(),
                            roleCode = roleCode,
                            firstName = firstName.trim(),
                            lastName = lastName.trim(),
                            status = "pending",
                            region = region.trim().ifBlank { null },
                            department = department.trim().ifBlank { null },
                            preferredLang = preferredLang
                        )
                    )
                },
                enabled = username.isNotBlank() && email.contains('@') && role != null
            ) { Text(LocalizationManager.t("create")) }
            }
        }
    }
}

@Composable
private fun EditUserDialog(
    user: ApiUser,
    roles: List<ApiRole>,
    saveError: String?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (UpdateUserRequest) -> Unit
) {
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
    Card(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
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
            InlineOptionPicker(options = roles, selected = role, prompt = roleCode, onSelect = { roleCode = it.code }, itemLabel = { LocalizationManager.t("role_${it.code.lowercase()}") })
            OutlinedTextField(password, { password = it }, label = { Text(LocalizationManager.t("new_password_optional")) }, modifier = Modifier.fillMaxWidth())
            Text(LocalizationManager.t("password_requirements"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss, enabled = !saving) { Text(LocalizationManager.t("cancel")) }
            Button(
                onClick = { onSave(UpdateUserRequest(username, email, roleCode, password.ifBlank { null }, firstName, lastName, status, region, department, preferredLang)) },
                enabled = !saving && username.isNotBlank() && email.contains('@')
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(LocalizationManager.t("save"))
                }
            }
            }
        }
    }
}
