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
        role = role.toResponse()
    )
}

/**
 * Перетворює доменну модель проєкту
 * у DTO-відповідь REST API.
 */
fun Project.toResponse(): ProjectResponse {

    return ProjectResponse(

        uuid =
            uuid.toString(),

        projectType =
            projectType.name.lowercase(),

        parentProjectId =
            parentProjectId,

        name =
            name,

        siteName =
            siteName,

        siteNumber =
            siteNumber,

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

        completionPct =
            completionPct,

        summary =
            summary
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