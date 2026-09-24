package oms.screens

import androidx.compose.runtime.*
import oms.components.ProjectMoneyDraft
import oms.components.currentIsoDate
import oms.data.*
import oms.localization.LocalizationManager as L
import kotlin.js.JsName
import kotlin.math.roundToInt

@JsName("daysBetweenIsoDates")
private external fun browserDaysBetweenIsoDates(start: String, end: String): Int?

internal class ProjectFormState(details: ApiProjectDetailsData? = null, private val today: String = if (details == null) currentIsoDate() else "") {
    private fun initialDate(details: ApiProjectDetailsData?, value: String?) = if (details == null) today else value.orEmpty()
    var projectType by mutableStateOf(details?.projectType ?: "project")
    var parentUuid by mutableStateOf(details?.parentProjectUuid)
    var fields by mutableStateOf(mapOf(
        "name" to details?.name.orEmpty(), "code" to details?.siteName.orEmpty(),
        "tranche" to if (details?.trancheNumber == 2) "B" else "A", "description" to details?.description.orEmpty(),
        "status" to (details?.status ?: "planned"), "sector" to (details?.sector ?: "education").lowercase(),
        "constructionType" to (details?.constructionType ?: "reconstruction"),
        "address" to details?.address.orEmpty(), "region" to details?.region.orEmpty(), "city" to details?.city.orEmpty(),
        "latitude" to details?.latitude?.toString().orEmpty(), "longitude" to details?.longitude?.toString().orEmpty(),
        "designerName" to details?.designerName.orEmpty(), "designContractNumber" to details?.designContractNumber.orEmpty(),
        "designContractTerm" to details?.designContractTerm.orEmpty(), "contractorName" to details?.contractorName.orEmpty(),
        "constructionContractNumber" to details?.constructionContractNumber.orEmpty(),
        "technicalSupervisionName" to details?.technicalSupervisionName.orEmpty(),
        "technicalSupervisionContractNumber" to details?.technicalSupervisionContractNumber.orEmpty(),
        "engineerConsultantName" to details?.engineerConsultantName.orEmpty(),
        "engineerConsultantContractNumber" to details?.engineerConsultantContractNumber.orEmpty(),
        "startDate" to initialDate(details, details?.startDate), "endDate" to initialDate(details, details?.endDate),
        "contractSignedDate" to initialDate(details, details?.contractSignedDate), "plannedEndDate" to initialDate(details, details?.plannedEndDate),
        "designContractSigningDate" to initialDate(details, details?.designContractSigningDate),
        "designStartDate" to initialDate(details, details?.designStartDate),
        "designPlannedEndDate" to initialDate(details, details?.designPlannedEndDate),
        "technicalSupervisionContractDate" to initialDate(details, details?.technicalSupervisionContractDate),
        "technicalSupervisionStartDate" to initialDate(details, details?.technicalSupervisionStartDate),
        "technicalSupervisionPlannedEndDate" to initialDate(details, details?.technicalSupervisionPlannedEndDate),
        "engineerConsultantContractDate" to initialDate(details, details?.engineerConsultantContractDate),
        "engineerConsultantStartDate" to initialDate(details, details?.engineerConsultantStartDate),
        "engineerConsultantPlannedEndDate" to initialDate(details, details?.engineerConsultantPlannedEndDate),
        "constructionContractSigningDate" to initialDate(details, details?.constructionContractSigningDate ?: details?.contractSignedDate),
        "constructionStartDate" to initialDate(details, details?.constructionStartDate ?: details?.startDate),
        "projectedCompletionTime" to initialDate(details, details?.projectedCompletionTime ?: details?.plannedEndDate)
    ))
    var money by mutableStateOf(mapOf(
        "budget" to initialMoney(details, "budget", details?.budgetPlanned),
        "engineer" to initialMoney(details, "engineer", details?.engineerConsultantContractAmount),
        "supervision" to initialMoney(details, "supervision", details?.technicalSupervisionAmount),
        "construction" to initialMoney(details, "construction", details?.subprojectContractAmount),
        "eib_financing" to initialMoney(details, "eib_financing", null),
        "local_financing" to initialMoney(details, "local_financing", null)
    ))
    var currentRate by mutableStateOf<ProjectExchangeRate?>(null)
    var validationAttempt by mutableStateOf(0)
    var editingForm by mutableStateOf(false)
    fun errors(): Map<String, String> = buildMap {
        listOf("name", "code").forEach { if (this@ProjectFormState[it].isBlank()) put(it, L.t("field_required")) }
        if (!editingForm && projectType != "project" && parentUuid.isNullOrBlank()) put("parent", L.t("select_parent_project"))
        if (trancheNumber() == null) put("tranche", L.t("tranche_invalid"))
        val limits = mapOf("code" to 50, "address" to 500, "region" to 100, "city" to 100,
            "designerName" to 255, "contractorName" to 255, "technicalSupervisionName" to 255, "engineerConsultantName" to 255,
            "designContractNumber" to 100, "constructionContractNumber" to 100,
            "technicalSupervisionContractNumber" to 100, "engineerConsultantContractNumber" to 100)
        limits.forEach { (key, limit) -> if (this@ProjectFormState[key].trim().length > limit)
            put(key, L.t("field_max_length").replace("{max}", limit.toString())) }
        money.forEach { (key, draft) ->
            if (key == "budget" && draft.amount.isBlank()) put(key, L.t("field_required"))
            else if (draft.amount.isNotBlank() && !draft.valid()) put(key, L.t("project_money_invalid"))
            else if (key == "budget" && draft.valid() && draft.legacyUah() <= 0) put(key, L.t("error_positive_budget"))
        }
        if (this@ProjectFormState["latitude"].isNotBlank() && coordinate("latitude")?.let { it in -90.0..90.0 } != true)
            put("latitude", L.t("error_latitude_range"))
        if (this@ProjectFormState["longitude"].isNotBlank() && coordinate("longitude")?.let { it in -180.0..180.0 } != true)
            put("longitude", L.t("error_longitude_range"))
        fields.filterKeys { it.endsWith("Date") || it == "projectedCompletionTime" || it.endsWith("SigningDate") }
            .forEach { (key, value) -> if (value.isNotBlank() && !oms.components.isValidIsoDate(value)) put(key, L.t("date_invalid")) }
        listOf("constructionContractSigningDate" to "projectedCompletionTime", "constructionStartDate" to "projectedCompletionTime",
            "designStartDate" to "designPlannedEndDate", "technicalSupervisionStartDate" to "technicalSupervisionPlannedEndDate",
            "engineerConsultantStartDate" to "engineerConsultantPlannedEndDate").forEach { (start, end) ->
            if (oms.components.isValidIsoDate(this@ProjectFormState[start]) && oms.components.isValidIsoDate(this@ProjectFormState[end]) &&
                this@ProjectFormState[end] < this@ProjectFormState[start]) put(end, L.t("project_dates_invalid"))
        }
    }
    fun validate(editing: Boolean): String? {
        editingForm = editing
        validationAttempt++
        return if (errors().isEmpty()) null else L.t("project_validation_summary")
    }
    operator fun get(key: String) = fields[key].orEmpty()
    operator fun set(key: String, value: String) { fields = fields + (key to value) }
    fun applyRate(rate: ProjectExchangeRate) {
        currentRate = rate
        money = money.mapValues { (_, value) -> if (value.rate.isBlank() && !value.edited) value.withRate(rate) else value }
    }
    fun designDurationLabel(): String = browserDaysBetweenIsoDates(this["designStartDate"], this["designPlannedEndDate"])
        ?.let(::durationMonthsLabel)
        .orEmpty()
    fun durationLabel(startKey: String, endKey: String): String = browserDaysBetweenIsoDates(this[startKey], this[endKey])
        ?.let(::durationMonthsLabel)
        .orEmpty()
    private fun coordinate(key: String) = this[key].replace(',', '.').toDoubleOrNull()
    fun trancheNumber(): Int? = when (this["tranche"].trim().uppercase()) {
        "A", "А", "1" -> 1
        "B", "В", "2" -> 2
        else -> null
    }
    private fun amounts() = money.filterValues { it.amount.isNotBlank() }.mapValues { it.value.toDto() }
    fun updateRequest() = UpdateProjectRequest(
        name = this["name"].trim(), siteName = this["code"].trim(), siteNumber = this["code"].trim(),
        description = this["description"].trim(), status = this["status"], address = this["address"].trim(),
        region = this["region"].trim(), city = this["city"].trim(), latitude = coordinate("latitude") ?: 0.0, longitude = coordinate("longitude") ?: 0.0,
        sector = this["sector"], constructionType = this["constructionType"],
        startDate = this["constructionStartDate"], endDate = this["endDate"], contractSignedDate = this["constructionContractSigningDate"], plannedEndDate = this["projectedCompletionTime"],
        designContractSigningDate = this["designContractSigningDate"], constructionContractSigningDate = this["constructionContractSigningDate"],
        designStartDate = this["designStartDate"], designPlannedEndDate = this["designPlannedEndDate"],
        constructionStartDate = this["constructionStartDate"], projectedCompletionTime = this["projectedCompletionTime"],
        contractorName = this["contractorName"].trim(), designerName = this["designerName"].trim(),
        designContractNumber = this["designContractNumber"].trim(), designContractTerm = null,
        constructionContractNumber = this["constructionContractNumber"].trim(),
        technicalSupervisionName = this["technicalSupervisionName"].trim(), technicalSupervisionContractNumber = this["technicalSupervisionContractNumber"].trim(),
        technicalSupervisionContractDate = this["technicalSupervisionContractDate"], technicalSupervisionStartDate = this["technicalSupervisionStartDate"], technicalSupervisionPlannedEndDate = this["technicalSupervisionPlannedEndDate"],
        engineerConsultantName = this["engineerConsultantName"].trim(), engineerConsultantContractNumber = this["engineerConsultantContractNumber"].trim(),
        engineerConsultantContractDate = this["engineerConsultantContractDate"], engineerConsultantStartDate = this["engineerConsultantStartDate"], engineerConsultantPlannedEndDate = this["engineerConsultantPlannedEndDate"],
        amounts = amounts(),
        trancheNumber = trancheNumber()
    )
    fun createRequest(): CreateProjectRequest {
        val p = updateRequest()
        return CreateProjectRequest(name = p.name!!, siteName = p.siteName!!, siteNumber = p.siteNumber!!, status = p.status,
            description = p.description, address = p.address!!, region = p.region!!, city = p.city!!,
            latitude = p.latitude!!, longitude = p.longitude!!, sector = p.sector!!, constructionType = p.constructionType!!,
            budgetPlanned = money.getValue("budget").legacyUah(), projectType = projectType, parentProjectUuid = parentUuid, trancheNumber = p.trancheNumber ?: 1,
            startDate = p.startDate, endDate = p.endDate, contractSignedDate = p.contractSignedDate, plannedEndDate = p.plannedEndDate,
            designContractSigningDate = p.designContractSigningDate, constructionContractSigningDate = p.constructionContractSigningDate,
            designStartDate = p.designStartDate, designPlannedEndDate = p.designPlannedEndDate,
            constructionStartDate = p.constructionStartDate, projectedCompletionTime = p.projectedCompletionTime,
            contractorName = p.contractorName, designerName = p.designerName, designContractNumber = p.designContractNumber,
            designContractTerm = p.designContractTerm, constructionContractNumber = p.constructionContractNumber,
            technicalSupervisionName = p.technicalSupervisionName, technicalSupervisionContractNumber = p.technicalSupervisionContractNumber,
            technicalSupervisionContractDate = p.technicalSupervisionContractDate, technicalSupervisionStartDate = p.technicalSupervisionStartDate, technicalSupervisionPlannedEndDate = p.technicalSupervisionPlannedEndDate,
            engineerConsultantName = p.engineerConsultantName, engineerConsultantContractNumber = p.engineerConsultantContractNumber,
            engineerConsultantContractDate = p.engineerConsultantContractDate, engineerConsultantStartDate = p.engineerConsultantStartDate, engineerConsultantPlannedEndDate = p.engineerConsultantPlannedEndDate,
            amounts = p.amounts)
    }
}

/** User-facing contract duration is shown in rounded months, not days. */
internal fun durationMonthsLabel(days: Long): String = L.t("duration_months")
    .replace("{months}", (days / 30.4375).roundToInt().toString())

private fun durationMonthsLabel(days: Int): String = durationMonthsLabel(days.toLong())

private fun initialMoney(details: ApiProjectDetailsData?, key: String, legacy: Long?): ProjectMoneyDraft {
    val saved = details?.amounts?.get(key)
    return if (saved != null) ProjectMoneyDraft(saved.amount, saved.currency, saved.convertedAmount, saved.uahPerEur, saved.rateDate, saved.conversionEdited)
    else if (legacy != null) ProjectMoneyDraft(amount = legacy.toString(), currency = "UAH")
    else ProjectMoneyDraft()
}
