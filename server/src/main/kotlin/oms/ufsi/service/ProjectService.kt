package oms.ufsi.service

import oms.ufsi.domain.Project
import oms.ufsi.domain.ProjectPatch
import oms.ufsi.domain.ProjectType
import oms.ufsi.repository.ProjectRepository
import java.time.LocalDate

/**
 * Бізнес-логіка роботи з проєктами.
 */
class ProjectService(
    private val projectRepository: ProjectRepository
) {

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
            (search.isNullOrBlank() || listOf(project.name, project.address, project.city, project.contractorName.orEmpty()).any { it.contains(search.trim(), true) })
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

        val normalizedConstructionType = normalizeConstructionType(constructionType)

        validateProjectData(
            name = name,
            region = region,
            city = city,
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

            region = region,

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
        region: String,
        city: String,
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

        if (region.isBlank()) {

            throw IllegalArgumentException(
                "Область не може бути порожньою."
            )
        }

        if (city.isBlank()) {

            throw IllegalArgumentException(
                "Населений пункт не може бути порожнім."
            )
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

        if (projectType != ProjectType.PROJECT) {
            require(subprojectContractAmount != null && subprojectContractAmount > 0) {
                "Subproject or subproject part contract amount must be positive."
            }
            require(startDate != null && contractSignedDate != null && plannedEndDate != null) {
                "Subproject or subproject part start date, contract signing date, and planned end date are required."
            }
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

        val normalizedConstructionType = normalizeConstructionType(constructionType)
        val normalizedType = when (projectType.trim().lowercase()) {
            "project" -> ProjectType.PROJECT
            "subproject" -> ProjectType.SUBPROJECT
            "subproject_part" -> ProjectType.SUBPROJECT_PART
            else -> throw IllegalArgumentException("Project type must be project, subproject, or subproject_part.")
        }
        val parent = parentProjectUuid?.trim()?.takeIf { it.isNotEmpty() }?.let { parentUuid ->
            getProjectByUuid(parentUuid) ?: throw IllegalArgumentException("Parent project was not found.")
        }
        if (normalizedType == ProjectType.SUBPROJECT) {
            require(parent?.projectType == ProjectType.PROJECT) { "A subproject must reference a parent project." }
        } else if (normalizedType == ProjectType.SUBPROJECT_PART) {
            require(parent?.projectType == ProjectType.SUBPROJECT) { "A subproject part must reference a parent subproject." }
        } else {
            require(parent == null) { "Only a subproject or subproject part may reference a parent." }
        }
        val parsedStartDate = parseOptionalDate(startDate, "Start date")
        val parsedContractSignedDate = parseOptionalDate(contractSignedDate, "Contract signing date")
        val parsedPlannedEndDate = parseOptionalDate(plannedEndDate, "Planned end date")
        validateProjectData(
            name = name,
            region = region,
            city = city,
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

            siteName = siteName,

            siteNumber = siteNumber,

            address = address,

            region = region,

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

    fun updateProject(uuid: String, request: oms.ufsi.dto.UpdateProjectRequest): Project? {
        val current = getProjectByUuid(uuid) ?: return null
        val patch = ProjectPatch(
            name = request.name?.trim() ?: current.name,
            siteName = request.siteName?.trim() ?: current.siteName,
            siteNumber = request.siteNumber?.trim() ?: current.siteNumber,
            address = request.address?.trim() ?: current.address,
            region = request.region?.trim() ?: current.region,
            city = request.city?.trim() ?: current.city,
            latitude = request.latitude ?: current.latitude,
            longitude = request.longitude ?: current.longitude,
            sector = request.sector?.trim() ?: current.sector,
            constructionType = request.constructionType?.let(::normalizeConstructionType) ?: current.constructionType,
            budgetPlanned = request.budgetPlanned ?: current.budgetPlanned,
            engineerConsultantContractAmount = request.engineerConsultantContractAmount ?: current.engineerConsultantContractAmount,
            technicalSupervisionAmount = request.technicalSupervisionAmount ?: current.technicalSupervisionAmount,
            subprojectContractAmount = request.subprojectContractAmount ?: current.subprojectContractAmount,
            startDate = request.startDate?.let { parseRequiredDate(it, "Start date") } ?: current.startDate,
            contractSignedDate = request.contractSignedDate?.let { parseRequiredDate(it, "Contract signing date") } ?: current.contractSignedDate,
            plannedEndDate = request.plannedEndDate?.let { parseRequiredDate(it, "Planned end date") } ?: current.plannedEndDate
        )
        validateProjectData(patch.name, patch.region, patch.city, patch.budgetPlanned, patch.engineerConsultantContractAmount, patch.technicalSupervisionAmount, current.projectType, patch.subprojectContractAmount, patch.startDate, patch.contractSignedDate, patch.plannedEndDate)
        if (patch.latitude !in -90.0..90.0) throw IllegalArgumentException("Latitude must be between -90 and 90.")
        if (patch.longitude !in -180.0..180.0) throw IllegalArgumentException("Longitude must be between -180 and 180.")
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

    private fun parseRequiredDate(value: String, label: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (_: Exception) {
        throw IllegalArgumentException("$label must use YYYY-MM-DD format.")
    }
}
