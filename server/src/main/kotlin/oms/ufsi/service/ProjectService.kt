package oms.ufsi.service

import oms.ufsi.domain.Project
import oms.ufsi.repository.ProjectRepository

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
        managerId: Long
    ): Project {

        validateProjectData(
            name = name,
            region = region,
            city = city,
            budgetPlanned = budgetPlanned,
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

            constructionType = constructionType,

            budgetPlanned = budgetPlanned,

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
        budgetPlanned: Long
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
        managerId: Long
    ): Project {
        validateProjectData(
            name = name,
            region = region,
            city = city,
            budgetPlanned = budgetPlanned,
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

            constructionType = constructionType,

            budgetPlanned = budgetPlanned,

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
}
