package oms.ufsi.dto

/**
 * Архітектурне правило проєкту:
 *
 * Domain-моделі не повинні залежати від HTTP,
 * JSON або механізмів серіалізації.
 *
 * Саме DTO визначають зовнішній контракт API.
 */
import oms.ufsi.domain.*
import java.time.LocalDateTime
import java.time.ZoneOffset

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
        status = status,
        region = region,
        department = department,
        preferredLang = preferredLang,
        lastLoginAt = lastLoginAt?.toUtcIsoString(),
        failedLoginCount = failedLoginCount,
        lockedUntil = lockedUntil?.toUtcIsoString(),
        createdAt = createdAt?.toUtcIsoString(),
        updatedAt = updatedAt?.toUtcIsoString(),
        role = role.toResponse()
    )
}

/**
 * Перетворює доменну модель проєкту
 * у DTO-відповідь REST API.
 */
fun Project.toResponse(parentProjectUuid: String? = null, monitoring: oms.ufsi.domain.ProjectMonitoringDetails? = null): ProjectResponse {

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

        designContractSigningDate =
            designContractSigningDate?.toString(),

        designStartDate = designStartDate?.toString(),

        designPlannedEndDate = designPlannedEndDate?.toString(),

        constructionContractSigningDate =
            constructionContractSigningDate?.toString(),

        constructionStartDate =
            constructionStartDate?.toString(),

        projectedCompletionTime =
            projectedCompletionTime?.toString(),

        contractDurationDays =
            contractDurationDays,

        designDurationDays = designDurationDays,

        currency =
            currency,

        contractorName = contractorName,
        designerName = designerName,
        designContractNumber = designContractNumber,
        designContractTerm = designContractTerm,
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
        ) }
    )
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
