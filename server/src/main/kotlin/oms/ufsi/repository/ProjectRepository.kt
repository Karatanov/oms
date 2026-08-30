package oms.ufsi.repository

import oms.ufsi.domain.Project

/**
 * Контракт доступу до проєктів.
 *
 * Репозиторій відповідає виключно
 * за отримання та збереження даних.
 */
interface ProjectRepository {
    fun programmeDetails(projectId: Long): oms.ufsi.domain.ProgrammeDetails?
    fun monitoringDetails(projectId: Long): oms.ufsi.domain.ProjectMonitoringDetails?
    fun managerIdForUuid(uuid: String): Long?

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
        projectType: oms.ufsi.domain.ProjectType,
        parentProjectId: Long?,
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

    fun updateByUuid(uuid: String, patch: oms.ufsi.domain.ProjectPatch): Project?

    fun updateStatusByUuids(uuids: List<String>, status: oms.ufsi.domain.ProjectStatus): Int

    fun updateManagerByUuids(uuids: List<String>, managerId: Long): Int

    fun deleteByUuid(uuid: String): Boolean
}
