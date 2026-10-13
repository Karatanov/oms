package oms.umitaf.repository

import oms.umitaf.domain.Project

/** Persistence boundary for projects and their monitoring metadata. */
interface ProjectRepository {
    fun monitoringDetails(projectId: Long): oms.umitaf.domain.ProjectMonitoringDetails?
    fun monitoringDetailsByProjectIds(projectIds: Collection<Long>): Map<Long, oms.umitaf.domain.ProjectMonitoringDetails>
    fun managerIdForUuid(uuid: String): Long?
    fun managedProjectIds(userId: Long): Set<Long>

    /** Returns the complete hierarchy; filtering and pagination belong to the service/API layer. */
    fun findAll(): List<Project>
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

    fun findByUuid(
        uuid: String
    ): Project?

    fun updateByUuid(uuid: String, patch: oms.umitaf.domain.ProjectPatch): Project?

    fun updateStatusByUuids(uuids: List<String>, status: oms.umitaf.domain.ProjectStatus): Int

    fun updateManagerByUuids(uuids: List<String>, managerId: Long): Int

    fun deleteByUuid(uuid: String): Boolean
    fun archiveByUuid(uuid: String, archivedBy: Long): Boolean
    fun restoreByUuid(uuid: String): Boolean
    fun dependencyCounts(uuid: String): Map<String, Long>
}
