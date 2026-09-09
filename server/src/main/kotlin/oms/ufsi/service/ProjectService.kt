package oms.ufsi.service

import oms.ufsi.domain.Project
import oms.ufsi.domain.ProjectPatch
import oms.ufsi.domain.ProjectType
import oms.ufsi.domain.ProjectStatus
import oms.ufsi.repository.ProjectRepository
import java.time.LocalDate

/**
 * Бізнес-логіка роботи з проєктами.
 */
class ProjectService(
    private val projectRepository: ProjectRepository
) {
    fun isManagedBy(uuid: String, userId: Long): Boolean = projectRepository.managerIdForUuid(uuid) == userId
    fun monitoringDetailsByProjectIds(projectIds: Collection<Long>) = projectRepository.monitoringDetailsByProjectIds(projectIds)

    /**
     * Повертає перелік проєктів.
     *
     * На поточному етапі повертаються
     * всі записи без обмежень.
     */
    fun getAllProjects(): List<Project> {

        return projectRepository.findAll()
    }

    fun searchProjects(
        status: String?,
        region: String?,
        search: String?
    ): List<Project> = getAllProjects().filter { project ->
        (status.isNullOrBlank() || project.status.name.equals(status.trim(), true)) &&
            (region.isNullOrBlank() || project.region.equals(region.trim(), true)) &&
            (search.isNullOrBlank() || listOf(project.name, project.address.orEmpty(), project.city.orEmpty(), project.contractorName.orEmpty()).any { it.contains(search.trim(), true) })
    }

    /**
     * Створює новий проєкт.
     */
    fun create(
        name: String,
        siteName: String,
        siteNumber: String,
        address: String,
        region: String,
        city: String,
        latitude: Double,
        longitude: Double,
        sector: String,
        constructionType: String,
        budgetPlanned: Long,
        engineerConsultantContractAmount: Long?,
        technicalSupervisionAmount: Long?,
        projectType: ProjectType = ProjectType.PROJECT,
        parentProjectId: Long? = null,
        subprojectContractAmount: Long? = null,
        startDate: LocalDate? = null,
        contractSignedDate: LocalDate? = null,
        plannedEndDate: LocalDate? = null,
        managerId: Long
    ): Project {

        val normalizedConstructionType = normalizeConstructionTypeOrDefault(constructionType)

        validateProjectData(
            name = name,
            siteName = siteName,
            siteNumber = siteNumber,
            budgetPlanned = budgetPlanned,
            engineerConsultantContractAmount = engineerConsultantContractAmount,
            technicalSupervisionAmount = technicalSupervisionAmount,
            projectType = projectType,
            subprojectContractAmount = subprojectContractAmount,
            startDate = startDate,
            contractSignedDate = contractSignedDate,
            plannedEndDate = plannedEndDate,
        )
        /**
         * Координати повинні відповідати
         * системі WGS84.
         */
        if (latitude !in -90.0..90.0) {

            throw IllegalArgumentException(
                "Некоректне значення широти."
            )
        }

        if (longitude !in -180.0..180.0) {

            throw IllegalArgumentException(
                "Некоректне значення довготи."
            )
        }

        return projectRepository.create(

            name = name,

            siteName = siteName,

            siteNumber = siteNumber,

            address = address,

            region = normalizeRegion(region),

            city = city,

            latitude = latitude,

            longitude = longitude,

            sector = sector,

            constructionType = normalizedConstructionType,

            budgetPlanned = budgetPlanned,

            engineerConsultantContractAmount = engineerConsultantContractAmount,

            technicalSupervisionAmount = technicalSupervisionAmount,

            projectType = projectType,

            parentProjectId = parentProjectId,

            subprojectContractAmount = subprojectContractAmount,

            startDate = startDate,

            contractSignedDate = contractSignedDate,

            plannedEndDate = plannedEndDate,

            managerId = managerId
        )
    }

    /**
     * Перевіряє коректність даних проєкту.
     */
    private fun validateProjectData(
        name: String,
        siteName: String,
        siteNumber: String,
        budgetPlanned: Long,
        engineerConsultantContractAmount: Long? = null,
        technicalSupervisionAmount: Long? = null,
        projectType: ProjectType = ProjectType.PROJECT,
        subprojectContractAmount: Long? = null,
        startDate: LocalDate? = null,
        contractSignedDate: LocalDate? = null,
        plannedEndDate: LocalDate? = null
    ) {

        if (name.isBlank()) {

            throw IllegalArgumentException(
                "Назва проєкту не може бути порожньою."
            )
        }

        require(siteName.isNotBlank() || siteNumber.isNotBlank()) {
            "Project code must not be blank."
        }

        if (budgetPlanned <= 0) {

            throw IllegalArgumentException(
                "Бюджет повинен бути більше нуля."
            )
        }

        require(engineerConsultantContractAmount == null || engineerConsultantContractAmount >= 0) {
            "Engineer consultant contract amount must not be negative."
        }
        require(technicalSupervisionAmount == null || technicalSupervisionAmount >= 0) {
            "Technical supervision amount must not be negative."
        }

        if (contractSignedDate != null && plannedEndDate != null) {
            require(!plannedEndDate.isBefore(contractSignedDate)) {
                "Planned end date must not be before contract signing date."
            }
        }

    }

    fun createProject(
        name: String,
        address: String,
        region: String,
        city: String,
        sector: String,
        constructionType: String,
        budgetPlanned: Long,
        siteName: String,
        siteNumber: String,
        latitude: Double,
        longitude: Double,
        engineerConsultantContractAmount: Long?,
        technicalSupervisionAmount: Long?,
        projectType: String,
        parentProjectUuid: String?,
        subprojectContractAmount: Long?,
        startDate: String?,
        contractSignedDate: String?,
        plannedEndDate: String?,
        managerId: Long
    ): Project {

        val normalizedConstructionType = normalizeConstructionTypeOrDefault(constructionType)
        val normalizedType = when (projectType.trim().lowercase()) {
            "project" -> ProjectType.PROJECT
            "subproject" -> ProjectType.SUBPROJECT
            "subproject_part" -> ProjectType.SUBPROJECT_PART
            else -> throw IllegalArgumentException("Project type must be project, subproject, or subproject_part.")
        }
        val parent = parentProjectUuid?.trim()?.takeIf { it.isNotEmpty() }?.let { parentUuid ->
            getProjectByUuid(parentUuid) ?: throw IllegalArgumentException("Parent project was not found.")
        }
        if (normalizedType == ProjectType.SUBPROJECT && parent != null) {
            require(parent.projectType == ProjectType.PROJECT) { "A subproject must reference a parent project." }
        } else if (normalizedType == ProjectType.SUBPROJECT_PART && parent != null) {
            require(parent.projectType == ProjectType.SUBPROJECT) { "A subproject part must reference a parent subproject." }
        } else {
            require(parent == null) { "Only a subproject or subproject part may reference a parent." }
        }
        val generatedPartCode = if (normalizedType == ProjectType.SUBPROJECT_PART && parent != null) {
            nextSubprojectPartCode(parent)
        } else null
        val resolvedSiteName = siteName.trim().ifBlank { siteNumber.trim().ifBlank { generatedPartCode.orEmpty() } }
        val resolvedSiteNumber = siteNumber.trim().ifBlank { siteName.trim().ifBlank { generatedPartCode.orEmpty() } }
        val parsedStartDate = parseOptionalDate(startDate, "Start date")
        val parsedContractSignedDate = parseOptionalDate(contractSignedDate, "Contract signing date")
        val parsedPlannedEndDate = parseOptionalDate(plannedEndDate, "Planned end date")
        validateProjectData(
            name = name,
            siteName = resolvedSiteName,
            siteNumber = resolvedSiteNumber,
            budgetPlanned = budgetPlanned,
            engineerConsultantContractAmount = engineerConsultantContractAmount,
            technicalSupervisionAmount = technicalSupervisionAmount,
            projectType = normalizedType,
            subprojectContractAmount = subprojectContractAmount,
            startDate = parsedStartDate,
            contractSignedDate = parsedContractSignedDate,
            plannedEndDate = parsedPlannedEndDate,
        )

        /**
         * Координати повинні відповідати
         * системі WGS84.
         */
        if (latitude !in -90.0..90.0) {

            throw IllegalArgumentException(
                "Некоректне значення широти."
            )
        }

        if (longitude !in -180.0..180.0) {

            throw IllegalArgumentException(
                "Некоректне значення довготи."
            )
        }

        return projectRepository.create(

            name = name,

            siteName = resolvedSiteName,

            siteNumber = resolvedSiteNumber,

            address = address,

            region = normalizeRegion(region),

            city = city,

            latitude = latitude,

            longitude = longitude,

            sector = sector,

            constructionType = normalizedConstructionType,

            budgetPlanned = budgetPlanned,

            engineerConsultantContractAmount = engineerConsultantContractAmount,

            technicalSupervisionAmount = technicalSupervisionAmount,

            projectType = normalizedType,

            parentProjectId = parent?.id,

            subprojectContractAmount = subprojectContractAmount,

            startDate = parsedStartDate,

            contractSignedDate = parsedContractSignedDate,

            plannedEndDate = parsedPlannedEndDate,

            managerId = managerId
        )
    }

    /**
     * Повертає один проєкт
     * за його UUID.
     */
    fun getProjectByUuid(
        uuid: String
    ): Project? {

        return projectRepository.findByUuid(
            uuid.trim()
        )
    }

    fun deleteProject(uuid: String): Boolean = projectRepository.deleteByUuid(uuid.trim())

    fun bulkUpdateStatus(projectUuids: List<String>, status: String): Int {
        val uuids = projectUuids.map(String::trim).filter(String::isNotEmpty).distinct()
        require(uuids.isNotEmpty()) { "Select at least one project." }
        val projectStatus = runCatching { ProjectStatus.valueOf(status.trim().uppercase()) }
            .getOrElse { throw IllegalArgumentException("Unknown project status.") }
        return projectRepository.updateStatusByUuids(uuids, projectStatus)
    }

    fun bulkReassign(projectUuids: List<String>, managerId: Long): Int {
        val uuids = projectUuids.map(String::trim).filter(String::isNotEmpty).distinct()
        require(uuids.isNotEmpty()) { "Select at least one project." }
        require(managerId > 0) { "Project manager is required." }
        return projectRepository.updateManagerByUuids(uuids, managerId)
    }

    fun updateProject(uuid: String, request: oms.ufsi.dto.UpdateProjectRequest): Project? {
        val current = getProjectByUuid(uuid) ?: return null
        val legacyChanges = listOfNotNull(
            "budget".takeIf { request.budgetPlanned != null },
            "engineer".takeIf { request.engineerConsultantContractAmount != null },
            "supervision".takeIf { request.technicalSupervisionAmount != null },
            "construction".takeIf { request.subprojectContractAmount != null }
        )
        val amounts = request.amounts?.let(::validateProjectAmounts) ?: (current.amounts - legacyChanges.toSet())
        if (request.amounts != null) require("budget" in amounts) { "Budget amount is required." }
        fun amount(kind: String, legacy: Long?): Long? =
            if (request.amounts != null) amounts[kind]?.legacyUah() else legacy
        fun text(value: String?, existing: String?, max: Int): String? =
            if (value == null) existing else value.trim().also { require(it.length <= max) { "Contract field is too long (maximum $max)." } }.ifBlank { null }
        fun date(value: String?, existing: LocalDate?, label: String): LocalDate? =
            if (value == null) existing else parseOptionalDate(value, label)
        val designStartDate = date(request.designStartDate, current.designStartDate, "Design start date")
        val designPlannedEndDate = date(request.designPlannedEndDate, current.designPlannedEndDate, "Design planned completion date")
        if (designStartDate != null && designPlannedEndDate != null) {
            require(!designPlannedEndDate.isBefore(designStartDate)) {
                "Design planned completion cannot precede design start."
            }
        }
        val technicalSupervisionStartDate = date(request.technicalSupervisionStartDate, current.technicalSupervisionStartDate, "Technical supervision start date")
        val technicalSupervisionPlannedEndDate = date(request.technicalSupervisionPlannedEndDate, current.technicalSupervisionPlannedEndDate, "Technical supervision planned completion date")
        if (technicalSupervisionStartDate != null && technicalSupervisionPlannedEndDate != null) {
            require(!technicalSupervisionPlannedEndDate.isBefore(technicalSupervisionStartDate)) {
                "Technical supervision planned completion cannot precede its start."
            }
        }
        val engineerConsultantStartDate = date(request.engineerConsultantStartDate, current.engineerConsultantStartDate, "Engineer-consultant start date")
        val engineerConsultantPlannedEndDate = date(request.engineerConsultantPlannedEndDate, current.engineerConsultantPlannedEndDate, "Engineer-consultant planned completion date")
        if (engineerConsultantStartDate != null && engineerConsultantPlannedEndDate != null) {
            require(!engineerConsultantPlannedEndDate.isBefore(engineerConsultantStartDate)) {
                "Engineer-consultant planned completion cannot precede its start."
            }
        }
        val designTerm = if (designStartDate != null && designPlannedEndDate != null)
            java.time.temporal.ChronoUnit.DAYS.between(designStartDate, designPlannedEndDate).toString()
        else null
        val patch = ProjectPatch(
            name = request.name?.trim() ?: current.name,
            siteName = request.siteName?.trim() ?: current.siteName,
            siteNumber = request.siteNumber?.trim() ?: current.siteNumber,
            description = if (request.description == null) current.description else request.description.trim().ifBlank { null },
            address = request.address?.trim() ?: current.address,
            region = request.region?.let(::normalizeRegion) ?: current.region,
            city = request.city?.trim() ?: current.city,
            status = request.status?.trim()?.uppercase()?.let { value ->
                runCatching { ProjectStatus.valueOf(value) }.getOrElse { throw IllegalArgumentException("Unknown project status.") }
            } ?: current.status,
            latitude = request.latitude ?: current.latitude,
            longitude = request.longitude ?: current.longitude,
            sector = request.sector?.trim() ?: current.sector,
            constructionType = request.constructionType?.let(::normalizeConstructionTypeOrDefault) ?: current.constructionType,
            budgetPlanned = amount("budget", request.budgetPlanned ?: current.budgetPlanned)!!,
            engineerConsultantContractAmount = amount("engineer", request.engineerConsultantContractAmount ?: current.engineerConsultantContractAmount),
            technicalSupervisionAmount = amount("supervision", request.technicalSupervisionAmount ?: current.technicalSupervisionAmount),
            subprojectContractAmount = amount("construction", request.subprojectContractAmount ?: current.subprojectContractAmount),
            startDate = date(request.startDate, current.startDate, "Start date"),
            endDate = date(request.endDate, current.endDate, "End date"),
            contractSignedDate = date(request.contractSignedDate, current.contractSignedDate, "Contract signing date"),
            plannedEndDate = date(request.plannedEndDate, current.plannedEndDate, "Planned end date"),
            designContractSigningDate = date(request.designContractSigningDate, current.designContractSigningDate, "Design contract signing date"),
            designStartDate = designStartDate,
            designPlannedEndDate = designPlannedEndDate,
            constructionContractSigningDate = date(request.constructionContractSigningDate, current.constructionContractSigningDate, "Construction contract signing date"),
            constructionStartDate = date(request.constructionStartDate, current.constructionStartDate, "Construction start date"),
            projectedCompletionTime = date(request.projectedCompletionTime, current.projectedCompletionTime, "Projected completion date"),
            currency = request.currency?.trim()?.uppercase()?.also { require(it.matches(Regex("[A-Z]{3}"))) { "Currency must be a three-letter code." } } ?: current.currency,
            contractorName = text(request.contractorName, current.contractorName, 255),
            designerName = text(request.designerName, current.designerName, 255),
            designContractNumber = text(request.designContractNumber, current.designContractNumber, 100),
            designContractTerm = designTerm,
            constructionContractNumber = text(request.constructionContractNumber, current.constructionContractNumber, 100),
            technicalSupervisionName = text(request.technicalSupervisionName, current.technicalSupervisionName, 255),
            technicalSupervisionContractNumber = text(request.technicalSupervisionContractNumber, current.technicalSupervisionContractNumber, 100),
            technicalSupervisionContractDate = date(request.technicalSupervisionContractDate, current.technicalSupervisionContractDate, "Technical supervision contract date"),
            technicalSupervisionStartDate = technicalSupervisionStartDate,
            technicalSupervisionPlannedEndDate = technicalSupervisionPlannedEndDate,
            engineerConsultantName = text(request.engineerConsultantName, current.engineerConsultantName, 255),
            engineerConsultantContractNumber = text(request.engineerConsultantContractNumber, current.engineerConsultantContractNumber, 100),
            engineerConsultantContractDate = date(request.engineerConsultantContractDate, current.engineerConsultantContractDate, "Engineer-consultant contract date"),
            engineerConsultantStartDate = engineerConsultantStartDate,
            engineerConsultantPlannedEndDate = engineerConsultantPlannedEndDate,
            amounts = amounts
        )
        validateProjectData(patch.name, patch.siteName, patch.siteNumber, patch.budgetPlanned, patch.engineerConsultantContractAmount, patch.technicalSupervisionAmount, current.projectType, patch.subprojectContractAmount, patch.startDate, patch.contractSignedDate, patch.plannedEndDate)
        if (patch.latitude != null && patch.latitude !in -90.0..90.0) throw IllegalArgumentException("Latitude must be between -90 and 90.")
        if (patch.longitude != null && patch.longitude !in -180.0..180.0) throw IllegalArgumentException("Longitude must be between -180 and 180.")
        if (patch.constructionStartDate != null && patch.projectedCompletionTime != null) {
            require(!patch.projectedCompletionTime.isBefore(patch.constructionStartDate)) { "Planned completion cannot precede construction start." }
        }
        return projectRepository.updateByUuid(uuid.trim(), patch)
    }

    private fun parseOptionalDate(value: String?, label: String): LocalDate? =
        value?.trim()?.takeIf { it.isNotEmpty() }?.let { parseRequiredDate(it, label) }

    private fun normalizeConstructionType(value: String): String {
        val normalized = value.trim().lowercase()
        require(normalized in setOf("reconstruction", "capital_repair", "new_construction")) {
            "Construction type must be reconstruction, capital_repair, or new_construction."
        }
        return normalized
    }

    private fun normalizeConstructionTypeOrDefault(value: String): String =
        value.trim().takeIf { it.isNotEmpty() }?.let(::normalizeConstructionType) ?: "reconstruction"

    /**
     * The column itself already makes the administrative unit clear.  Keep the
     * stored value concise (for example, "Рівненська", not "Рівненська область")
     * so filters, exports, and map data all use the same canonical value.
     */
    private fun normalizeRegion(value: String): String =
        value.trim()
            .replace(Regex("\\s+(область|oblast)\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun nextSubprojectPartCode(parent: Project): String {
        val parentCode = parent.siteNumber.trim().ifBlank { parent.siteName.trim() }
        val pattern = Regex("^${Regex.escape(parentCode)}-(\\d+)$")
        val nextNumber = getAllProjects()
            .asSequence()
            .filter { it.projectType == ProjectType.SUBPROJECT_PART && it.parentProjectId == parent.id }
            .mapNotNull { pattern.matchEntire(it.siteNumber.trim())?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull()
            ?.plus(1)
            ?: 1
        return "$parentCode-${nextNumber.toString().padStart(2, '0')}"
    }

    private fun parseRequiredDate(value: String, label: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (_: Exception) {
        throw IllegalArgumentException("$label must use YYYY-MM-DD format.")
    }
}
