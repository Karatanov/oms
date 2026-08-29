package oms.screens

import androidx.compose.runtime.*
import oms.components.ProjectMoneyDraft
import oms.components.currentIsoDate
import oms.data.*
import oms.localization.LocalizationManager as L

internal class ProjectFormState(details: ApiProjectDetailsData? = null) {
    var projectType by mutableStateOf(details?.projectType ?: "project")
    var parentUuid by mutableStateOf(details?.parentProjectUuid)
    var fields by mutableStateOf(mapOf(
        "name" to details?.name.orEmpty(), "code" to details?.siteName.orEmpty(), "description" to details?.description.orEmpty(),
        "status" to (details?.status ?: "planned"), "sector" to (details?.sector ?: "Education").replaceFirstChar(Char::uppercase),
        "constructionType" to (details?.constructionType ?: "reconstruction"),
        "address" to details?.address.orEmpty(), "region" to details?.region.orEmpty(), "city" to details?.city.orEmpty(),
        "latitude" to details?.latitude?.toString().orEmpty(), "longitude" to details?.longitude?.toString().orEmpty(),
        "designerName" to details?.designerName.orEmpty(), "designContractNumber" to details?.designContractNumber.orEmpty(),
        "designContractTerm" to details?.designContractTerm.orEmpty(), "contractorName" to details?.contractorName.orEmpty(),
        "constructionContractNumber" to details?.constructionContractNumber.orEmpty(),
        "startDate" to initialDate(details, details?.startDate), "endDate" to initialDate(details, details?.endDate),
        "contractSignedDate" to initialDate(details, details?.contractSignedDate), "plannedEndDate" to initialDate(details, details?.plannedEndDate),
        "designContractSigningDate" to initialDate(details, details?.designContractSigningDate),
        "constructionContractSigningDate" to initialDate(details, details?.constructionContractSigningDate ?: details?.contractSignedDate),
        "constructionStartDate" to initialDate(details, details?.constructionStartDate ?: details?.startDate),
        "projectedCompletionTime" to initialDate(details, details?.projectedCompletionTime ?: details?.plannedEndDate)
    ))
    var money by mutableStateOf(mapOf(
        "budget" to initialMoney(details, "budget", details?.budgetPlanned),
        "engineer" to initialMoney(details, "engineer", details?.engineerConsultantContractAmount),
        "supervision" to initialMoney(details, "supervision", details?.technicalSupervisionAmount),
        "construction" to initialMoney(details, "construction", details?.subprojectContractAmount)
    ))
    var currentRate by mutableStateOf<ProjectExchangeRate?>(null)
    operator fun get(key: String) = fields[key].orEmpty()
    operator fun set(key: String, value: String) { fields = fields + (key to value) }
    fun applyRate(rate: ProjectExchangeRate) {
        currentRate = rate
        money = money.mapValues { (_, value) -> if (value.rate.isBlank() && !value.edited) value.withRate(rate) else value }
    }
    fun validationError(editing: Boolean): String? = when {
        this["name"].isBlank() || this["code"].isBlank() -> L.t("error_required_fields")
        !editing && projectType != "project" && parentUuid == null -> L.t("select_parent_project")
        money.getValue("budget").amount.isBlank() || money.values.any { it.amount.isNotBlank() && !it.valid() } -> L.t("project_money_invalid")
        money.getValue("budget").legacyUah() <= 0 -> L.t("error_positive_budget")
        this["latitude"].isNotBlank() && coordinate("latitude")?.let { it in -90.0..90.0 } != true -> L.t("error_latitude_range")
        this["longitude"].isNotBlank() && coordinate("longitude")?.let { it in -180.0..180.0 } != true -> L.t("error_longitude_range")
        this["plannedEndDate"].isNotBlank() && this["contractSignedDate"].isNotBlank() && this["plannedEndDate"] < this["contractSignedDate"] -> L.t("project_dates_invalid")
        this["projectedCompletionTime"].isNotBlank() && this["constructionStartDate"].isNotBlank() && this["projectedCompletionTime"] < this["constructionStartDate"] -> L.t("project_dates_invalid")
        else -> null
    }
    private fun coordinate(key: String) = this[key].replace(',', '.').toDoubleOrNull()
    private fun amounts() = money.filterValues { it.amount.isNotBlank() }.mapValues { it.value.toDto() }
    fun updateRequest() = UpdateProjectRequest(
        name = this["name"].trim(), siteName = this["code"].trim(), siteNumber = this["code"].trim(),
        description = this["description"].trim(), status = this["status"], address = this["address"].trim(),
        region = this["region"].trim(), city = this["city"].trim(), latitude = coordinate("latitude") ?: 0.0, longitude = coordinate("longitude") ?: 0.0,
        sector = this["sector"], constructionType = this["constructionType"],
        startDate = this["constructionStartDate"], endDate = this["endDate"], contractSignedDate = this["constructionContractSigningDate"], plannedEndDate = this["projectedCompletionTime"],
        designContractSigningDate = this["designContractSigningDate"], constructionContractSigningDate = this["constructionContractSigningDate"],
        constructionStartDate = this["constructionStartDate"], projectedCompletionTime = this["projectedCompletionTime"],
        contractorName = this["contractorName"].trim(), designerName = this["designerName"].trim(),
        designContractNumber = this["designContractNumber"].trim(), designContractTerm = this["designContractTerm"].trim(),
        constructionContractNumber = this["constructionContractNumber"].trim(), amounts = amounts()
    )
    fun createRequest(): CreateProjectRequest {
        val p = updateRequest()
        return CreateProjectRequest(name = p.name!!, siteName = p.siteName!!, siteNumber = p.siteNumber!!, status = p.status,
            description = p.description, address = p.address!!, region = p.region!!, city = p.city!!,
            latitude = p.latitude!!, longitude = p.longitude!!, sector = p.sector!!, constructionType = p.constructionType!!,
            budgetPlanned = money.getValue("budget").legacyUah(), projectType = projectType, parentProjectUuid = parentUuid,
            startDate = p.startDate, endDate = p.endDate, contractSignedDate = p.contractSignedDate, plannedEndDate = p.plannedEndDate,
            designContractSigningDate = p.designContractSigningDate, constructionContractSigningDate = p.constructionContractSigningDate,
            constructionStartDate = p.constructionStartDate, projectedCompletionTime = p.projectedCompletionTime,
            contractorName = p.contractorName, designerName = p.designerName, designContractNumber = p.designContractNumber,
            designContractTerm = p.designContractTerm, constructionContractNumber = p.constructionContractNumber, amounts = p.amounts)
    }
}

private fun initialDate(details: ApiProjectDetailsData?, value: String?) = if (details == null) currentIsoDate() else value.orEmpty()
private fun initialMoney(details: ApiProjectDetailsData?, key: String, legacy: Long?): ProjectMoneyDraft {
    val saved = details?.amounts?.get(key)
    return if (saved != null) ProjectMoneyDraft(saved.amount, saved.currency, saved.convertedAmount, saved.uahPerEur, saved.rateDate, saved.conversionEdited)
    else if (legacy != null) ProjectMoneyDraft(amount = legacy.toString(), currency = "UAH")
    else ProjectMoneyDraft()
}
