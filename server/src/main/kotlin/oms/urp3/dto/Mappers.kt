package oms.urp3.dto

/**
 * Архітектурне правило проєкту:
 *
 * Domain-моделі не повинні залежати від HTTP,
 * JSON або механізмів серіалізації.
 *
 * Саме DTO визначають зовнішній контракт API.
 */
import oms.urp3.domain.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.math.BigDecimal

private fun LocalDateTime.toUtcIsoString(): String = atOffset(ZoneOffset.UTC).toString()

/**
 * Перетворення доменних моделей у DTO.
 *
 * Виділення маперів в окремий файл дозволяє
 * не змішувати бізнес-логіку та форматування
 * HTTP-відповідей.
 */

/**
 * Перетворює доменну модель ролі у DTO.
 */
fun Role.toResponse(): RoleResponse {

    return RoleResponse(
        id = id,
        code = code,
        name = name
    )
}

/**
 * Перетворює доменну модель користувача у DTO.
 */
fun User.toResponse(): UserResponse {

    return UserResponse(
        id = id,
        username = username,
        email = email,
        firstName = firstName,
        lastName = lastName,
        // Legacy values must never reintroduce a third state to the public UI.
        // Any non-active historical status is safely presented as disabled.
        status = if (status.equals("active", ignoreCase = true)) "active" else "disabled",
        region = region,
        department = department,
        preferredLang = preferredLang,
        lastLoginAt = lastLoginAt?.toUtcIsoString(),
        failedLoginCount = failedLoginCount,
        lockedUntil = lockedUntil?.toUtcIsoString(),
        createdAt = createdAt?.toUtcIsoString(),
        updatedAt = updatedAt?.toUtcIsoString(),
        isArchived = isArchived,
        archivedAt = archivedAt?.toUtcIsoString(),
        archivedBy = archivedBy,
        role = role.toResponse()
    )
}

/**
 * Перетворює доменну модель проєкту
 * у DTO-відповідь REST API.
 */
fun Project.toResponse(
    parentProjectUuid: String? = null,
    monitoring: oms.urp3.domain.ProjectMonitoringDetails? = null,
    financingDisbursedEurCents: Long = 0L,
    financingDisbursedUah: BigDecimal = BigDecimal.ZERO
): ProjectResponse {

    return ProjectResponse(

        uuid =
            uuid.toString(),

        projectType =
            projectType.name.lowercase(),

        trancheNumber =
            trancheNumber,

        parentProjectId =
            parentProjectId,

        parentProjectUuid =
            parentProjectUuid,

        name =
            name,

        nameEn = monitoring?.nameEn,

        beneficiaryNameUk = monitoring?.beneficiaryNameUk,

        beneficiaryNameEn = monitoring?.beneficiaryNameEn,

        siteName =
            siteName,

        siteNumber =
            siteNumber,

        description =
            description,

        address =
            address.orEmpty(),

        region =
            region.orEmpty(),

        city =
            city.orEmpty(),

        cityEn = monitoring?.settlementNameEn,

        latitude =
            latitude ?: 0.0,

        longitude =
            longitude ?: 0.0,

        status =
            status.name.lowercase(),

        isArchived = isArchived,
        archivedAt = archivedAt?.toUtcIsoString(),
        archivedBy = archivedBy,

        sector =
            sector.orEmpty(),

        constructionType =
            constructionType.orEmpty(),

        budgetPlanned =
            budgetPlanned,

        engineerConsultantContractAmount =
            engineerConsultantContractAmount,

        technicalSupervisionAmount =
            technicalSupervisionAmount,

        subprojectContractAmount =
            subprojectContractAmount,

        startDate =
            startDate?.toString(),

        endDate =
            endDate?.toString(),

        contractSignedDate =
            contractSignedDate?.toString(),

        plannedEndDate =
            plannedEndDate?.toString(),

        constructionContractSigningDate =
            constructionContractSigningDate?.toString(),

        constructionStartDate =
            constructionStartDate?.toString(),

        projectedCompletionTime =
            projectedCompletionTime?.toString(),

        contractDurationDays =
            contractDurationDays,

        currency =
            currency,

        contractorName = contractorName,
        constructionContractNumber = constructionContractNumber,
        technicalSupervisionName = technicalSupervisionName,
        technicalSupervisionContractNumber = technicalSupervisionContractNumber,
        technicalSupervisionContractDate = technicalSupervisionContractDate?.toString(),
        technicalSupervisionStartDate = technicalSupervisionStartDate?.toString(),
        technicalSupervisionPlannedEndDate = technicalSupervisionPlannedEndDate?.toString(),
        technicalSupervisionDurationDays = technicalSupervisionDurationDays,
        engineerConsultantName = engineerConsultantName,
        engineerConsultantContractNumber = engineerConsultantContractNumber,
        engineerConsultantContractDate = engineerConsultantContractDate?.toString(),
        engineerConsultantStartDate = engineerConsultantStartDate?.toString(),
        engineerConsultantPlannedEndDate = engineerConsultantPlannedEndDate?.toString(),
        engineerConsultantDurationDays = engineerConsultantDurationDays,
        amounts = amounts.mapValues { (_, value) -> ProjectAmountDto(
            value.amount.toPlainString(), value.currency, value.convertedAmount.toPlainString(),
            value.uahPerEur.stripTrailingZeros().toPlainString(), value.rateDate.toString(), value.conversionEdited
        ) },

        financingDisbursedEurCents = financingDisbursedEurCents,
        registry = registryValues(monitoring, financingDisbursedUah)
    )
}

/**
 * A stable, string-only table projection for the subproject registry.  It is
 * deliberately derived while the list is assembled, so opening the registry
 * never triggers an N+1 request pattern in the browser.
 */
private fun Project.registryValues(
    monitoring: oms.urp3.domain.ProjectMonitoringDetails?,
    financingDisbursedUah: BigDecimal
): Map<String, String> {
    fun amount(kind: String) = amounts[kind]?.amount?.stripTrailingZeros()?.toPlainString().orEmpty()
    fun decimal(value: Long?) = value?.toString().orEmpty()
    fun value(item: String?) = item?.trim().orEmpty()
    val contracted = subprojectContractAmount ?: 0L
    val approved = budgetPlanned
    val approvedShare = if (approved > 0) {
        BigDecimal(contracted).multiply(BigDecimal(100)).divide(BigDecimal(approved), 2, java.math.RoundingMode.HALF_UP).toPlainString()
    } else ""
    return buildMap {
        // Spreadsheet formula errors are not customer-facing values.  Keep
        // their cells empty until the authoritative workbook provides data.
        putAll(monitoring?.registryValues.orEmpty().filterValues { value ->
            value.isNotBlank() && !value.trim().startsWith("#")
        })
        putIfAbsent("pool", monitoring?.sourceBatchId?.toString() ?: trancheNumber.toString())
        putIfAbsent("dreamUrl", value(monitoring?.dreamProjectUrl))
        putIfAbsent("community", value(monitoring?.municipalityNameUk))
        putIfAbsent("communityEn", value(monitoring?.municipalityNameEn))
        putIfAbsent("priority", value(monitoring?.priorityAreaSource))
        putIfAbsent("implementer", value(monitoring?.projectManagerNameUk))
        putIfAbsent("implementerEn", value(monitoring?.projectManagerNameEn))
        putIfAbsent("fundingTotal", amount("budget").ifBlank { budgetPlanned.toString() })
        putIfAbsent("fundingWorks", amount("planned_works"))
        putIfAbsent("fundingTs", amount("planned_supervision"))
        putIfAbsent("fundingCsc", amount("planned_engineer"))
        putIfAbsent("eibCost", amount("eib_financing"))
        putIfAbsent("localCost", amount("local_financing"))
        putIfAbsent("contractedTotal", decimal(subprojectContractAmount))
        putIfAbsent("contractedEib", amount("contracted_eib"))
        putIfAbsent("contractedLocal", amount("contracted_local"))
        putIfAbsent("approvedFundingPct", approvedShare)
        putIfAbsent("contractor", value(contractorName))
        putIfAbsent("contractorEn", value(monitoring?.contractorNameEn))
        putIfAbsent("contractDate", constructionContractSigningDate?.toString().orEmpty())
        putIfAbsent("contractEndDate", plannedEndDate?.toString().orEmpty())
        putIfAbsent("contractDuration", contractDurationDays?.let { kotlin.math.ceil(it / 30.44).toInt().toString() }.orEmpty())
        putIfAbsent("constructionAmount", decimal(subprojectContractAmount))
        putIfAbsent("constructionEib", amount("construction_eib"))
        putIfAbsent("constructionLocal", amount("construction_local"))
        putIfAbsent("paidConstruction", financingDisbursedUah.stripTrailingZeros().toPlainString())
        putIfAbsent("dedUrl", value(monitoring?.dedUrl))
        putIfAbsent("estimateUrl", value(monitoring?.estimateUrl))
        putIfAbsent("expertiseUrl", value(monitoring?.expertiseUrl))
        putIfAbsent("permitUrl", value(monitoring?.permitUrl))
        putIfAbsent("workStatus", value(monitoring?.constructionWorkStatus))
        putIfAbsent("actNumber", value(monitoring?.constructionActNumber))
        putIfAbsent("actDate", value(monitoring?.constructionActDate))
        putIfAbsent("completedWorkPct", value(monitoring?.completedWorkPct))
        putIfAbsent("financingUsedPct", value(monitoring?.financingUsedPct))
        putIfAbsent("paidFinancingPct", value(monitoring?.paidFinancingPct))
        putIfAbsent("workSummary", value(monitoring?.constructionWorkSummary))
        putIfAbsent("workComments", value(monitoring?.constructionWorkComments))
        // Excel's HYPERLINK cells expose the visible DREAM identifier in
        // data-only mode.  Convert that identifier back to the stable public
        // URL so the table always opens a real DREAM page.
        this["dreamUrl"] = this["dreamUrl"].orEmpty().let { source ->
            when {
                source.startsWith("https://", ignoreCase = true) -> source
                source.isNotBlank() -> "https://dream.gov.ua/ua/project/DREAM-UA-$source/profile"
                else -> ""
            }
        }
    }
}

/**
 * Перетворює доменну модель
 * інспекції у DTO.
 */
fun InspectionReport.toResponse(subprojectCode: String? = null):
        InspectionReportResponse {

    return InspectionReportResponse(

        uuid =
            uuid.toString(),

        // A UUID fragment is not a meaningful SIR code.  Only retain a code
        // that actually came from the source report or was entered explicitly.
        inspectionCode = reportCode.orEmpty(),

        reportCode = reportCode,

        inspectionType = inspectionType,

        inspectionDate =
            inspectionDate.toString(),

        summary =
            summary,

        status =
            status.name.lowercase(),

        rejectionReason =
            rejectionReason,

        latitude = latitude,

        longitude = longitude,
        authorUsername = authorUsername,
        subprojectCode = subprojectCode
    )
}

/**
 * Перетворює доменну модель
 * зауваження у DTO.
 */
fun InspectionFinding.toResponse():
        InspectionFindingResponse {

    return InspectionFindingResponse(

        uuid =
            uuid.toString(),

        category =
            category,

        severity =
            severity.name.lowercase(),

        description =
            description,

        recommendation =
            recommendation,

        isResolved =
            isResolved
    )
}

fun FinancialRecord.toResponse() = FinancialRecordResponse(uuid.toString(), recordType.name.lowercase(), referenceNumber, amount, currency, recordDate.toString(), paymentDate?.toString(), description, milestone, paymentPurpose, eurExchangeRate, eurExchangeDate?.toString(), amountEurCents)

fun ProjectDocument.toResponse() = ProjectDocumentResponse(uuid.toString(), docType, originalName, contentType, fileSizeBytes, relatedEntity, relatedId, description)

fun InspectionPhoto.toResponse(reportUuid: String) = InspectionPhotoResponse(
    uuid.toString(), originalName, contentType, fileSizeBytes, isMain,
    "/api/v1/inspection-reports/$reportUuid/photos/$uuid/original",
    "/api/v1/inspection-reports/$reportUuid/photos/$uuid/thumbnail"
)
