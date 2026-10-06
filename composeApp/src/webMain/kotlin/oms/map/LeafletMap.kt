package oms.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import oms.localization.LocalizationManager
import oms.model.Project
import oms.model.ProjectStatus
import oms.model.localizedName
import oms.model.localizedBeneficiary
import oms.components.localizedUkraineRegion
import kotlin.js.JsName
import kotlin.math.abs
import kotlin.math.roundToLong
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import oms.components.NativePaneAnchor

@JsName("showLeafletMapPane")
external fun showLeafletMapPane()

@JsName("hideLeafletMapPane")
external fun hideLeafletMapPane()

@JsName("setLeafletProjects")
external fun setLeafletProjects(projectsJson: String)

@JsName("setLeafletProjectClickHandler")
external fun setLeafletProjectClickHandler(handler: (String) -> Unit)

@Composable
fun LeafletMapView(
    projects: List<Project>,
    allProjects: List<Project> = projects,
    onProjectClick: (String) -> Unit = {}
) {
    NativePaneAnchor("map-pane", Modifier.fillMaxSize())
    if (!oms.navigation.LocalRouteVisible.current) return
    val language = LocalizationManager.currentLanguage
    DisposableEffect(projects, allProjects, language) {
        showLeafletMapPane()
        setLeafletProjectClickHandler(onProjectClick)
        setLeafletProjects(projects.toLeafletJson(allProjects))

        onDispose {
            hideLeafletMapPane()
        }
    }
}

/*
   Перетворює список проєктів у JSON-рядок,
   який далі обробляє JavaScript-код Leaflet.
*/
private fun List<Project>.toLeafletJson(allProjects: List<Project>): String {
    val projectsById = allProjects.associateBy { it.id }
    fun Project.subprojectCode(): String? = generateSequence(this) { current ->
        current.parentProjectUuid?.let(projectsById::get)
    }.firstOrNull { it.projectType.equals("subproject", ignoreCase = true) }
        ?.siteNumber?.takeIf(String::isNotBlank)

    return buildString {
        append("[")

        this@toLeafletJson.forEachIndexed { index, project ->
            if (index > 0) append(",")

            val (statusText, statusColorHex) = project.status.toMapPresentation()

            append(
                """
                {
                  "id": "${project.id.escapeJson()}",
                  "name": "${project.localizedName().escapeJson()}",
                  "region": "${localizedUkraineRegion(project.region).escapeJson()}",
                  "regionLabel": "${LocalizationManager.t("region").escapeJson()}",
                  "subprojectCode": "${project.subprojectCode().orEmpty().escapeJson()}",
                  "subprojectCodeLabel": "${LocalizationManager.t("subproject_code").escapeJson()}",
                  "beneficiary": "${project.localizedBeneficiary().escapeJson()}",
                  "beneficiaryLabel": "${LocalizationManager.t("beneficiary").escapeJson()}",
                  "projectCost": "${project.mapProjectCost().escapeJson()}",
                  "projectCostLabel": "${LocalizationManager.t("map_project_cost").escapeJson()}",
                  "financingAmount": "${project.mapFinancingAmount().escapeJson()}",
                  "financingAmountLabel": "${LocalizationManager.t("map_financing_amount").escapeJson()}",
                  "financingDisbursed": "${project.mapFinancingDisbursed().escapeJson()}",
                  "financingDisbursedLabel": "${LocalizationManager.t("map_financing_disbursed").escapeJson()}",
                  "status": "${project.status.name}",
                  "statusText": "${statusText.escapeJson()}",
                  "statusLabel": "${LocalizationManager.t("status").escapeJson()}",
                  "statusColor": "$statusColorHex",
                  "latitude": ${project.latitude},
                  "longitude": ${project.longitude}
                }
                """.trimIndent()
            )
        }

        append("]")
    }
}

private fun Project.mapProjectCost(): String =
    formatMapMoney(budgetDisplayAmount ?: budgetPlanned.toString(), budgetCurrency)

private fun Project.mapFinancingAmount(): String =
    financingAmount?.let { formatMapMoney(it, financingCurrency ?: "UAH") } ?: "—"

private fun Project.mapFinancingDisbursed(): String =
    formatMapMoney((financingDisbursedEurCents / 100.0).toString(), "EUR")

private fun formatMapMoney(raw: String, currency: String): String {
    val amount = raw.toDoubleOrNull() ?: return "$raw $currency"
    val roundedCents = (amount * 100).roundToLong()
    val sign = if (roundedCents < 0) "-" else ""
    val absolute = abs(roundedCents)
    val whole = (absolute / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
    val cents = absolute % 100
    return "$sign$whole${if (cents == 0L) "" else ".${cents.toString().padStart(2, '0')}"} $currency"
}

/*
   Презентаційна модель статусу для карти.
   Тут ми використовуємо ті самі кольори, що і в таблиці.
*/
private fun ProjectStatus.toMapPresentation(): Pair<String, String> {
    return when (this) {
        ProjectStatus.PLANNED -> LocalizationManager.t("project_status_planned") to "#F9A825"
        ProjectStatus.ACTIVE -> LocalizationManager.t("project_status_active") to "#2E7D32"
        ProjectStatus.SUSPENDED -> LocalizationManager.t("project_status_suspended") to "#EF6C00"
        ProjectStatus.COMPLETED -> LocalizationManager.t("project_status_completed") to "#1565C0"
        ProjectStatus.ARCHIVED -> LocalizationManager.t("project_status_archived") to "#607D8B"
        ProjectStatus.DLP -> LocalizationManager.t("project_status_dlp") to "#6A1B9A"
    }
}

/*
   Мінімальне екранування спецсимволів для JSON.
*/
private fun String.escapeJson(): String {
    return this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\b", "\\b")
        .replace("\u000C", "\\f")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
