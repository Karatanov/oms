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
        lastLoginAt = lastLoginAt?.toString(),
        failedLoginCount = failedLoginCount,
        lockedUntil = lockedUntil?.toString(),
        createdAt = createdAt?.toString(),
        updatedAt = updatedAt?.toString(),
        role = role.toResponse()
    )
}

/**
 * Перетворює доменну модель проєкту
 * у DTO-відповідь REST API.
 */
fun Project.toResponse(parentProjectUuid: String? = null): ProjectResponse {

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

        siteName =
            siteName,

        siteNumber =
            siteNumber,

        description =
            description,

        address =
            address,

        region =
            region,

        city =
            city,

        latitude =
            latitude,

        longitude =
            longitude,

        status =
            status.name.lowercase(),

        sector =
            sector,

        constructionType =
            constructionType,

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

        contractorName =
            contractorName
    )
}

/**
 * Перетворює доменну модель
 * інспекції у DTO.
 */
fun InspectionReport.toResponse():
        InspectionReportResponse {

    return InspectionReportResponse(

        uuid =
            uuid.toString(),

        inspectionDate =
            inspectionDate.toString(),

        summary =
            summary,

        status =
            status.name.lowercase(),

        rejectionReason =
            rejectionReason
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

fun FinancialRecord.toResponse() = FinancialRecordResponse(uuid.toString(), recordType.name.lowercase(), referenceNumber, amount, currency, recordDate.toString(), paymentDate?.toString(), description, milestone)

fun ProjectDocument.toResponse() = ProjectDocumentResponse(uuid.toString(), docType, originalName, contentType, fileSizeBytes, relatedEntity, relatedId, description)

fun InspectionPhoto.toResponse(reportUuid: String) = InspectionPhotoResponse(
    uuid.toString(), originalName, contentType, fileSizeBytes, isMain,
    "/api/v1/inspection-reports/$reportUuid/photos/$uuid/original",
    "/api/v1/inspection-reports/$reportUuid/photos/$uuid/thumbnail"
)
