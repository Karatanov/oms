package oms.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import oms.data.OmsApiClient
import oms.data.ApiFeedbackRequest
import oms.localization.LocalizationManager
import oms.navigation.Screen
import oms.urp3.FeedbackOptions
import kotlin.random.Random

private fun feedbackPage(screen: Screen): String = when (screen) {
    Screen.Dashboard -> "Dashboard"
    Screen.Projects, Screen.CreateProject, Screen.EditProject, Screen.ProjectDetail -> "Projects"
    Screen.Map -> "Map"
    Screen.Inspections, Screen.CreateInspection -> "Inspection Reports / SIR"
    Screen.Financial -> "Financial Monitoring"
    Screen.Documents -> "Documents"
    Screen.Admin -> "Administration"
    Screen.Login -> "Authentication / Users"
    Screen.Procurement -> "Procurement"
}

private val pageKeys = mapOf(
    "Dashboard" to "dashboard", "Projects" to "projects", "Map" to "map",
    "Inspection Reports / SIR" to "inspection_reports", "Financial Monitoring" to "financial_monitoring",
    "Documents" to "documents", "Administration" to "admin_title", "Procurement" to "procurement_title",
    "Authentication / Users" to "feedback_users", "Database" to "feedback_database", "General / System" to "feedback_general"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackDialog(currentScreen: Screen, onDismiss: () -> Unit) {
    fun t(key: String) = LocalizationManager.t(key)
    var page by remember { mutableStateOf(feedbackPage(currentScreen)) }
    var type by remember { mutableStateOf("Correction") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var configured by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var receipt by remember { mutableStateOf<Int?>(null) }
    var attempt by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    // Retain this payload after an uncertain network result; retry the same operation.
    var submitted by remember { mutableStateOf<ApiFeedbackRequest?>(null) }
    val requestId = remember { List(4) { Random.nextInt().toUInt().toString(16).padStart(8, '0') }.joinToString("") }
    LaunchedEffect(attempt) {
        loading = true
        error = null
        try {
            val availability = OmsApiClient.feedbackAvailability()
            date = availability.date
            configured = availability.configured
            if (!configured) error = "feedback_unconfigured"
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "feedback_failed" }
        finally { loading = false }
    }
    var confirmDiscard by remember { mutableStateOf(false) }
    val dismiss: () -> Unit = {
        when {
            sending -> Unit
            confirmDiscard -> confirmDiscard = false
            receipt == null && description.isNotBlank() -> confirmDiscard = true
            else -> onDismiss()
        }
    }
    WasmSafeOverlay(onDismiss = if (sending) null else dismiss) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().semantics { testTag = "feedback-dialog" },
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 400.dp
                Column(Modifier.padding(if (compact) 16.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Feedback, null, tint = MaterialTheme.colorScheme.primary)
                        Text(t("feedback_title"), style = MaterialTheme.typography.headlineSmall)
                    }
                    if (confirmDiscard) {
                        Text(t("feedback_discard_prompt"), style = MaterialTheme.typography.bodyLarge)
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(onClick = onDismiss) { Text(t("feedback_discard")) }
                            Button(onClick = { confirmDiscard = false }) { Text(t("feedback_return")) }
                        }
                    } else if (receipt != null) {
                        ContentState("${t("feedback_success")} #$receipt")
                        Button(onClick = dismiss, modifier = Modifier.align(Alignment.End)) { Text(t("close")) }
                    } else {
                        Text(t("feedback_hint"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(t("feedback_page"), style = MaterialTheme.typography.labelLarge)
                        InlineOptionPicker(FeedbackOptions.pages, page, t("feedback_page"), { page = it },
                            itemLabel = { pageKeys[it]?.let(LocalizationManager::t) ?: it }, enabled = !sending && submitted == null)
                        Text(t("feedback_type"), style = MaterialTheme.typography.labelLarge)
                        InlineOptionPicker(FeedbackOptions.types, type, t("feedback_type"), { type = it },
                            itemLabel = { t(if (it == "Correction") "feedback_correction" else "feedback_improvement") }, enabled = !sending && submitted == null)
                        OutlinedTextField(description, { description = it.take(FeedbackOptions.maxDescriptionLength) },
                            modifier = Modifier.fillMaxWidth().semantics { testTag = "feedback-description" },
                            enabled = !sending && submitted == null, label = { Text(t("feedback_description")) },
                            placeholder = { Text(t("feedback_description_example")) },
                            minLines = if (compact) 3 else 4, maxLines = 8,
                            supportingText = {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (description.length == FeedbackOptions.maxDescriptionLength) {
                                        Text(t("feedback_text_limit"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("${description.length} / ${FeedbackOptions.maxDescriptionLength}", modifier = Modifier.align(Alignment.End))
                                }
                            })
                        if (date.isNotEmpty()) Text("${t("feedback_date")}: ${date.split('-').reversed().joinToString(".")}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        error?.let { ContentState(t(it), error = true, onRetry = if (!configured && !loading) ({ attempt++ }) else null) }
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(onClick = dismiss, enabled = !sending) { Text(t("cancel")) }
                            Button(enabled = configured && !loading && !sending && description.isNotBlank(), onClick = {
                                val payload = submitted ?: ApiFeedbackRequest(requestId, page, type, description.trim()).also { submitted = it }
                                sending = true
                                error = null
                                scope.launch {
                                    try {
                                        val result = OmsApiClient.submitFeedback(payload)
                                        receipt = result.number
                                        date = result.date
                                    } catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { error = "feedback_retry" }
                                    finally { sending = false }
                                }
                            }, modifier = Modifier.semantics { testTag = "feedback-submit" }) {
                                if (sending) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(t(if (sending) "feedback_sending" else if (submitted != null) "retry" else "feedback_send"))
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
