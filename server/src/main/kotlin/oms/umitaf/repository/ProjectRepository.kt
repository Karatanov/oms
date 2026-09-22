package oms.umitaf.repository

import oms.umitaf.domain.Project

/**
 * Контракт доступу до проєктів.
 *
 * Репозиторій відповідає виключно
 * за отримання та збереження даних.
 */
interface ProjectRepository {
    fun programmeDetails(projectId: Long): oms.umitaf.domain.ProgrammeDetails?
    fun monitoringDetails(projectId: Long): oms.umitaf.domain.ProjectMonitoringDetails?
    fun monitoringDetailsByProjectIds(projectIds: Collection<Long>): Map<Long, oms.umitaf.domain.ProjectMonitoringDetails>
    fun managerIdForUuid(uuid: String): Long?
    fun managedProjectIds(userId: Long): Set<Long>

    /**
     * Повертає всі доступні проєкти.
     *
     * На поточній ітерації фільтрація,
     * сортування та пагінація ще
     * не реалізовані.
     */
    fun findAll(): List<Project>

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
        projectType: oms.umitaf.domain.ProjectType,
        parentProjectId: Long?,
        trancheNumber: Int = 1,
        subprojectContractAmount: Long?,
        startDate: java.time.LocalDate?,
        contractSignedDate: java.time.LocalDate?,
        plannedEndDate: java.time.LocalDate?,
        managerId: Long
    ): Project

    /**
     * Повертає проєкт за його публічним UUID.
     */
    fun findByUuid(
        uuid: String
    ): Project?

    fun updateByUuid(uuid: String, patch: oms.umitaf.domain.ProjectPatch): Project?

    fun updateStatusByUuids(uuids: List<String>, status: oms.umitaf.domain.ProjectStatus): Int

    fun updateManagerByUuids(uuids: List<String>, managerId: Long): Int

    fun deleteByUuid(uuid: String): Boolean
}
