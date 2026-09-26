package oms.umitaf.repository

import oms.umitaf.database.tables.ProjectTable
import oms.umitaf.database.tables.ProgrammeDetailTable
import oms.umitaf.database.tables.ProjectMonitoringDetailTable
import oms.umitaf.database.tables.ProjectAmountTable
import oms.umitaf.domain.ProjectAmount
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.insert
import oms.umitaf.database.tables.ProjectDocumentTable
import oms.umitaf.database.tables.FinancialRecordTable
import oms.umitaf.database.tables.IncidentTable
import oms.umitaf.database.tables.InspectionReportTable
import oms.umitaf.database.tables.ProcurementRecordTable
import oms.umitaf.domain.Project
import oms.umitaf.domain.ProjectStatus
import oms.umitaf.domain.ProjectType
import oms.umitaf.domain.ProjectPatch
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.count
import java.time.LocalDateTime
import java.util.*

/** Iterative child-first traversal; validate the complete subtree before deleting anything. */
internal fun projectDeletionOrder(root: Long, children: Map<Long?, List<Long>>): List<Long> {
    val pending = ArrayDeque<Pair<Long, Boolean>>()
    val visited = mutableSetOf<Long>()
    val result = mutableListOf<Long>()
    pending.addLast(root to false)
    while (pending.isNotEmpty()) {
        val (id, expanded) = pending.removeLast()
        if (expanded) result.add(id) else {
            check(visited.add(id)) { "Project hierarchy contains a cycle." }
            pending.addLast(id to true)
            children[id].orEmpty().asReversed().forEach { pending.addLast(it to false) }
        }
    }
    return result
}

/** Counts only retained records. The caller refuses destructive deletion when any exist. */
private fun projectDependencies(projectId: Long): Map<String, Long> = linkedMapOf(
    "subprojects or parts" to ProjectTable.selectAll().where { ProjectTable.parentProjectId eq projectId }.count(),
    "financial records" to FinancialRecordTable.selectAll().where { FinancialRecordTable.projectId eq projectId }.count(),
    "inspection reports" to InspectionReportTable.selectAll().where { InspectionReportTable.projectId eq projectId }.count(),
    "documents" to ProjectDocumentTable.selectAll().where { ProjectDocumentTable.projectId eq projectId }.count(),
    "incidents" to IncidentTable.selectAll().where { IncidentTable.projectId eq projectId }.count(),
    "project amounts" to ProjectAmountTable.selectAll().where { ProjectAmountTable.projectId eq projectId }.count(),
    "monitoring details" to ProjectMonitoringDetailTable.selectAll().where { ProjectMonitoringDetailTable.projectId eq projectId }.count(),
    "procurement records" to ProcurementRecordTable.selectAll().where { ProcurementRecordTable.projectId eq projectId }.count(),
    "programme details" to ProgrammeDetailTable.selectAll().where { ProgrammeDetailTable.projectId eq projectId }.count()
).filterValues { it > 0 }

class ExposedProjectRepository : ProjectRepository {
    override fun programmeDetails(projectId: Long) = transaction {
        ProgrammeDetailTable.selectAll().where { ProgrammeDetailTable.projectId eq projectId }.limit(1).firstOrNull()?.let { row ->
            oms.umitaf.domain.ProgrammeDetails(
                row[ProgrammeDetailTable.implementor], row[ProgrammeDetailTable.financingInstitution],
                row[ProgrammeDetailTable.financeContractNumber], row[ProgrammeDetailTable.serapisNumber],
                row[ProgrammeDetailTable.agreementDate], row[ProgrammeDetailTable.loanAmount],
                row[ProgrammeDetailTable.loanCurrency], row[ProgrammeDetailTable.sourceWorkbook],
                row[ProgrammeDetailTable.sourceSnapshotDate]
            )
        }
    }

    override fun monitoringDetails(projectId: Long) = transaction {
        ProjectMonitoringDetailTable.selectAll().where { ProjectMonitoringDetailTable.projectId eq projectId }.limit(1).firstOrNull()?.toMonitoringDetails()
    }

    override fun monitoringDetailsByProjectIds(projectIds: Collection<Long>) = transaction {
        if (projectIds.isEmpty()) emptyMap() else ProjectMonitoringDetailTable.selectAll()
            .where { ProjectMonitoringDetailTable.projectId inList projectIds }
            .associate { it[ProjectMonitoringDetailTable.projectId].value to it.toMonitoringDetails() }
    }

    private fun ResultRow.toMonitoringDetails(): oms.umitaf.domain.ProjectMonitoringDetails {
        val row = this
        return oms.umitaf.domain.ProjectMonitoringDetails(
                row[ProjectMonitoringDetailTable.sourceBatchId], row[ProjectMonitoringDetailTable.sourceSubprojectId],
                row[ProjectMonitoringDetailTable.sourceLotId], row[ProjectMonitoringDetailTable.nameEn],
                row[ProjectMonitoringDetailTable.oblastCode], row[ProjectMonitoringDetailTable.municipalityNameUk],
                row[ProjectMonitoringDetailTable.municipalityNameEn], row[ProjectMonitoringDetailTable.settlementNameEn],
                row[ProjectMonitoringDetailTable.priorityAreaSource], row[ProjectMonitoringDetailTable.projectManagerNameUk],
                row[ProjectMonitoringDetailTable.projectManagerNameEn], row[ProjectMonitoringDetailTable.projectManagerOrgId],
                row[ProjectMonitoringDetailTable.beneficiaryNameUk], row[ProjectMonitoringDetailTable.beneficiaryNameEn],
                row[ProjectMonitoringDetailTable.beneficiaryOrgId], row[ProjectMonitoringDetailTable.dreamProjectId],
                row[ProjectMonitoringDetailTable.dreamProjectUrl], row[ProjectMonitoringDetailTable.applicationId],
                row[ProjectMonitoringDetailTable.dreamApplicationId], row[ProjectMonitoringDetailTable.constructionProcurementStatus],
                row[ProjectMonitoringDetailTable.constructionWorkStatus], row[ProjectMonitoringDetailTable.geocodeAccuracy],
                row[ProjectMonitoringDetailTable.geocodeQuery], row[ProjectMonitoringDetailTable.geocodeDisplayName],
                row[ProjectMonitoringDetailTable.sourceRows],
                row[ProjectMonitoringDetailTable.sourceWorkbook]
            )
    }
    override fun managerIdForUuid(uuid: String): Long? = transaction {
        ProjectTable.selectAll().firstOrNull { it[ProjectTable.uuid] == uuid }
            ?.get(ProjectTable.managerId)
            ?.value
    }

    override fun managedProjectIds(userId: Long): Set<Long> = transaction {
        ProjectTable.select(ProjectTable.id).where { ProjectTable.managerId eq userId }
            .map { it[ProjectTable.id].value }
            .toSet()
    }

    override fun findAll(): List<Project> = transaction {
        val amountsByProject = ProjectAmountTable.selectAll().groupBy { it[ProjectAmountTable.projectId].value }
        ProjectTable
            .selectAll()
            .map { row ->
                Project(
                    id = row[ProjectTable.id].value,
                    uuid = UUID.fromString(row[ProjectTable.uuid]),
                    projectType = row[ProjectTable.projectType].toProjectType(),
                    trancheNumber = row[ProjectTable.trancheNumber],
                    parentProjectId = row[ProjectTable.parentProjectId]?.value,
                    name = row[ProjectTable.name],
                    siteName = row[ProjectTable.siteName],
                    siteNumber = row[ProjectTable.siteNumber],
                    description = row[ProjectTable.description],
                    address = row[ProjectTable.address],
                    region = row[ProjectTable.region],
                    city = row[ProjectTable.city],
                    latitude = row[ProjectTable.latitude]?.toDouble(),
                    longitude = row[ProjectTable.longitude]?.toDouble(),
                    status = ProjectStatus.valueOf(row[ProjectTable.status].uppercase()),
                    sector = row[ProjectTable.sector],
                    constructionType = row[ProjectTable.constructionType],
                    budgetPlanned = row[ProjectTable.budgetPlanned],
                    engineerConsultantContractAmount = row[ProjectTable.engineerConsultantContractAmount],
                    technicalSupervisionAmount = row[ProjectTable.technicalSupervisionAmount],
                    subprojectContractAmount = row[ProjectTable.subprojectContractAmount],
                    startDate = row[ProjectTable.startDate],
                    endDate = row[ProjectTable.endDate],
                    contractSignedDate = row[ProjectTable.contractSignedDate],
                    plannedEndDate = row[ProjectTable.plannedEndDate],
                    designContractSigningDate = row[ProjectTable.designContractSigningDate],
                    designStartDate = row[ProjectTable.designStartDate],
                    designPlannedEndDate = row[ProjectTable.designPlannedEndDate],
                    constructionContractSigningDate = row[ProjectTable.constructionContractSigningDate],
                    constructionStartDate = row[ProjectTable.constructionStartDate],
                    projectedCompletionTime = row[ProjectTable.projectedCompletionTime],
                    currency = row[ProjectTable.currency],
                    contractorName = row[ProjectTable.contractorName],
                    designerName = row[ProjectTable.designerName],
                    designContractNumber = row[ProjectTable.designContractNumber],
                    designContractTerm = row[ProjectTable.designContractTerm],
                    constructionContractNumber = row[ProjectTable.constructionContractNumber],
                    technicalSupervisionName = row[ProjectTable.technicalSupervisionName],
                    technicalSupervisionContractNumber = row[ProjectTable.technicalSupervisionContractNumber],
                    technicalSupervisionContractDate = row[ProjectTable.technicalSupervisionContractDate],
                    technicalSupervisionStartDate = row[ProjectTable.technicalSupervisionStartDate],
                    technicalSupervisionPlannedEndDate = row[ProjectTable.technicalSupervisionPlannedEndDate],
                    engineerConsultantName = row[ProjectTable.engineerConsultantName],
                    engineerConsultantContractNumber = row[ProjectTable.engineerConsultantContractNumber],
                    engineerConsultantContractDate = row[ProjectTable.engineerConsultantContractDate],
                    engineerConsultantStartDate = row[ProjectTable.engineerConsultantStartDate],
                    engineerConsultantPlannedEndDate = row[ProjectTable.engineerConsultantPlannedEndDate],
                    amounts = amountsByProject[row[ProjectTable.id].value].orEmpty().toAmounts(),
                    isArchived = row[ProjectTable.isArchived], archivedAt = row[ProjectTable.archivedAt], archivedBy = row[ProjectTable.archivedBy]?.value
                )
            }
    }

    /**
     * Виконує пошук проєкту
     * за публічним UUID.
     */
    override fun findByUuid(
        uuid: String
    ): Project? = transaction {

        ProjectTable
            .selectAll()
            .where { ProjectTable.uuid eq uuid }.firstOrNull()
            ?.let { row ->

                Project(

                    id = row[ProjectTable.id].value,

                    uuid = UUID.fromString(
                        row[ProjectTable.uuid]
                    ),

                    projectType = row[ProjectTable.projectType].toProjectType(),

                    trancheNumber = row[ProjectTable.trancheNumber],

                    parentProjectId =
                        row[ProjectTable.parentProjectId]
                            ?.value,

                    name =
                        row[ProjectTable.name],

                    siteName =
                        row[ProjectTable.siteName],

                    siteNumber =
                        row[ProjectTable.siteNumber],

                    description =
                        row[ProjectTable.description],

                    address =
                        row[ProjectTable.address],

                    region =
                        row[ProjectTable.region],

                    city =
                        row[ProjectTable.city],

                    latitude =
                        row[ProjectTable.latitude]
                            ?.toDouble(),

                    longitude =
                        row[ProjectTable.longitude]
                            ?.toDouble(),

                    status =
                        ProjectStatus.valueOf(
                            row[ProjectTable.status]
                                .uppercase()
                        ),

                    sector =
                        row[ProjectTable.sector],

                    constructionType =
                        row[ProjectTable.constructionType],

                    budgetPlanned =
                        row[ProjectTable.budgetPlanned],

                    engineerConsultantContractAmount =
                        row[ProjectTable.engineerConsultantContractAmount],

                    technicalSupervisionAmount =
                        row[ProjectTable.technicalSupervisionAmount],

                    subprojectContractAmount =
                        row[ProjectTable.subprojectContractAmount],

                    startDate =
                        row[ProjectTable.startDate],

                    endDate =
                        row[ProjectTable.endDate],

                    contractSignedDate =
                        row[ProjectTable.contractSignedDate],

                    plannedEndDate =
                        row[ProjectTable.plannedEndDate],

                    designContractSigningDate =
                        row[ProjectTable.designContractSigningDate],

                    designStartDate = row[ProjectTable.designStartDate],

                    designPlannedEndDate = row[ProjectTable.designPlannedEndDate],

                    constructionContractSigningDate =
                        row[ProjectTable.constructionContractSigningDate],

                    constructionStartDate =
                        row[ProjectTable.constructionStartDate],

                    projectedCompletionTime =
                        row[ProjectTable.projectedCompletionTime],

                    currency =
                        row[ProjectTable.currency],

                    contractorName = row[ProjectTable.contractorName],
                    designerName = row[ProjectTable.designerName],
                    designContractNumber = row[ProjectTable.designContractNumber],
                    designContractTerm = row[ProjectTable.designContractTerm],
                    constructionContractNumber = row[ProjectTable.constructionContractNumber],
                    technicalSupervisionName = row[ProjectTable.technicalSupervisionName],
                    technicalSupervisionContractNumber = row[ProjectTable.technicalSupervisionContractNumber],
                    technicalSupervisionContractDate = row[ProjectTable.technicalSupervisionContractDate],
                    technicalSupervisionStartDate = row[ProjectTable.technicalSupervisionStartDate],
                    technicalSupervisionPlannedEndDate = row[ProjectTable.technicalSupervisionPlannedEndDate],
                    engineerConsultantName = row[ProjectTable.engineerConsultantName],
                    engineerConsultantContractNumber = row[ProjectTable.engineerConsultantContractNumber],
                    engineerConsultantContractDate = row[ProjectTable.engineerConsultantContractDate],
                    engineerConsultantStartDate = row[ProjectTable.engineerConsultantStartDate],
                    engineerConsultantPlannedEndDate = row[ProjectTable.engineerConsultantPlannedEndDate],
                    amounts = ProjectAmountTable.selectAll().where { ProjectAmountTable.projectId eq row[ProjectTable.id] }.toList().toAmounts(),
                    isArchived = row[ProjectTable.isArchived], archivedAt = row[ProjectTable.archivedAt], archivedBy = row[ProjectTable.archivedBy]?.value
                )
            }
    }

    /**
     * Створює новий проєкт.
     */
    override fun create(
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
        projectType: ProjectType,
        parentProjectId: Long?,
        trancheNumber: Int,
        subprojectContractAmount: Long?,
        startDate: java.time.LocalDate?,
        contractSignedDate: java.time.LocalDate?,
        plannedEndDate: java.time.LocalDate?,
        managerId: Long
    ): Project = transaction {

        val projectUuid = UUID.randomUUID()

        val projectId = ProjectTable.insertAndGetId {
            it[ProjectTable.uuid] = projectUuid.toString()
            it[ProjectTable.projectType] = projectType.name.lowercase()
            it[ProjectTable.trancheNumber] = trancheNumber
            it[ProjectTable.parentProjectId] = parentProjectId?.let { id -> org.jetbrains.exposed.v1.core.dao.id.EntityID(id, ProjectTable) }
            it[ProjectTable.name] = name
            it[ProjectTable.siteName] = siteName
            it[ProjectTable.siteNumber] = siteNumber
            it[ProjectTable.address] = address
            it[ProjectTable.region] = region
            it[ProjectTable.city] = city
            it[ProjectTable.latitude] = latitude.toBigDecimal()
            it[ProjectTable.longitude] = longitude.toBigDecimal()
            it[ProjectTable.status] = "planned"
            it[ProjectTable.sector] = sector
            it[ProjectTable.constructionType] = constructionType
            it[ProjectTable.budgetPlanned] = budgetPlanned
            it[ProjectTable.engineerConsultantContractAmount] = engineerConsultantContractAmount
            it[ProjectTable.technicalSupervisionAmount] = technicalSupervisionAmount
            it[ProjectTable.subprojectContractAmount] = subprojectContractAmount
            it[ProjectTable.startDate] = startDate
            it[ProjectTable.contractSignedDate] = contractSignedDate
            it[ProjectTable.plannedEndDate] = plannedEndDate
            it[ProjectTable.currency] = "EUR"
            it[ProjectTable.managerId] = managerId
        }

        Project(
            id = projectId.value,
            uuid = projectUuid,
            projectType = projectType,
            trancheNumber = trancheNumber,
            parentProjectId = parentProjectId,
            name = name,
            siteName = siteName,
            siteNumber = siteNumber,
            description = null,
            latitude = latitude,
            longitude = longitude,
            address = address,
            region = region,
            city = city,
            status = ProjectStatus.PLANNED,
            sector = sector,
            constructionType = constructionType,
            budgetPlanned = budgetPlanned,
            engineerConsultantContractAmount = engineerConsultantContractAmount,
            technicalSupervisionAmount = technicalSupervisionAmount,
            subprojectContractAmount = subprojectContractAmount,
            startDate = startDate,
            endDate = null,
            contractSignedDate = contractSignedDate,
            plannedEndDate = plannedEndDate,
            designContractSigningDate = null,
            designStartDate = null,
            designPlannedEndDate = null,
            constructionContractSigningDate = null,
            constructionStartDate = null,
            projectedCompletionTime = null,
            currency = "EUR",
            contractorName = null
        )
    }

    override fun deleteByUuid(uuid: String): Boolean = transaction {
        // Never trigger legacy database cascades. A record can be physically
        // deleted only when it has no retained historical relationship.
        val project = ProjectTable.selectAll().where { ProjectTable.uuid eq uuid }.firstOrNull() ?: return@transaction false
        val id = project[ProjectTable.id].value
        val dependencies = projectDependencies(id)
        require(dependencies.isEmpty()) { "Project still has dependencies: ${dependencies.keys.joinToString()}." }
        ProjectTable.deleteWhere { ProjectTable.id eq id } > 0
    }

    override fun archiveByUuid(uuid: String, archivedBy: Long): Boolean = transaction {
        val root = ProjectTable.selectAll().where { ProjectTable.uuid eq uuid }.firstOrNull() ?: return@transaction false
        val hierarchy = ProjectTable.select(ProjectTable.id, ProjectTable.parentProjectId).toList()
        val children = hierarchy.groupBy({ it[ProjectTable.parentProjectId]?.value }, { it[ProjectTable.id].value })
        val ids = projectDeletionOrder(root[ProjectTable.id].value, children)
        ProjectTable.update({ ProjectTable.id inList ids }) {
            it[isArchived] = true; it[archivedAt] = LocalDateTime.now(); it[ProjectTable.archivedBy] = archivedBy
        } > 0
    }

    override fun restoreByUuid(uuid: String): Boolean = transaction {
        val root = ProjectTable.selectAll().where { ProjectTable.uuid eq uuid }.firstOrNull() ?: return@transaction false
        val hierarchy = ProjectTable.select(ProjectTable.id, ProjectTable.parentProjectId).toList()
        val children = hierarchy.groupBy({ it[ProjectTable.parentProjectId]?.value }, { it[ProjectTable.id].value })
        val ids = projectDeletionOrder(root[ProjectTable.id].value, children)
        ProjectTable.update({ ProjectTable.id inList ids }) {
            it[isArchived] = false; it[archivedAt] = null; it[ProjectTable.archivedBy] = null
        } > 0
    }

    override fun dependencyCounts(uuid: String): Map<String, Long> = transaction {
        val project = ProjectTable.selectAll().where { ProjectTable.uuid eq uuid }.firstOrNull() ?: return@transaction emptyMap()
        projectDependencies(project[ProjectTable.id].value)
    }

    override fun updateByUuid(uuid: String, patch: ProjectPatch): Project? {
        val updated = transaction {
            val projectId = ProjectTable.selectAll().where { ProjectTable.uuid eq uuid }.firstOrNull()?.get(ProjectTable.id)
                ?: return@transaction 0
            val count = ProjectTable.update({ ProjectTable.uuid eq uuid }) {
                it[name] = patch.name
                it[siteName] = patch.siteName
                it[siteNumber] = patch.siteNumber
                it[description] = patch.description
                it[address] = patch.address
                it[region] = patch.region
                it[city] = patch.city
                it[status] = patch.status.name.lowercase()
                it[latitude] = patch.latitude?.toBigDecimal()
                it[longitude] = patch.longitude?.toBigDecimal()
                it[sector] = patch.sector
                it[constructionType] = patch.constructionType
                it[trancheNumber] = patch.trancheNumber
                it[budgetPlanned] = patch.budgetPlanned
                it[engineerConsultantContractAmount] = patch.engineerConsultantContractAmount
                it[technicalSupervisionAmount] = patch.technicalSupervisionAmount
                it[subprojectContractAmount] = patch.subprojectContractAmount
                it[startDate] = patch.startDate
                it[endDate] = patch.endDate
                it[contractSignedDate] = patch.contractSignedDate
                it[plannedEndDate] = patch.plannedEndDate
                it[designContractSigningDate] = patch.designContractSigningDate
                it[designStartDate] = patch.designStartDate
                it[designPlannedEndDate] = patch.designPlannedEndDate
                it[constructionContractSigningDate] = patch.constructionContractSigningDate
                it[constructionStartDate] = patch.constructionStartDate
                it[projectedCompletionTime] = patch.projectedCompletionTime
                it[currency] = patch.currency
                it[contractorName] = patch.contractorName
                it[designerName] = patch.designerName
                it[designContractNumber] = patch.designContractNumber
                it[designContractTerm] = patch.designContractTerm
                it[constructionContractNumber] = patch.constructionContractNumber
                it[technicalSupervisionName] = patch.technicalSupervisionName
                it[technicalSupervisionContractNumber] = patch.technicalSupervisionContractNumber
                it[technicalSupervisionContractDate] = patch.technicalSupervisionContractDate
                it[technicalSupervisionStartDate] = patch.technicalSupervisionStartDate
                it[technicalSupervisionPlannedEndDate] = patch.technicalSupervisionPlannedEndDate
                it[engineerConsultantName] = patch.engineerConsultantName
                it[engineerConsultantContractNumber] = patch.engineerConsultantContractNumber
                it[engineerConsultantContractDate] = patch.engineerConsultantContractDate
                it[engineerConsultantStartDate] = patch.engineerConsultantStartDate
                it[engineerConsultantPlannedEndDate] = patch.engineerConsultantPlannedEndDate
            }
            ProjectAmountTable.deleteWhere { ProjectAmountTable.projectId eq projectId }
            patch.amounts.forEach { (key, value) ->
                ProjectAmountTable.insert {
                    it[ProjectAmountTable.projectId] = projectId
                    it[kind] = key
                    it[amount] = value.amount
                    it[currency] = value.currency
                    it[convertedAmount] = value.convertedAmount
                    it[uahPerEur] = value.uahPerEur
                    it[rateDate] = value.rateDate
                    it[conversionEdited] = value.conversionEdited
                }
            }
            count
        }
        return if (updated == 0) null else findByUuid(uuid)
    }

    override fun updateStatusByUuids(uuids: List<String>, status: ProjectStatus): Int = transaction {
        ProjectTable.update({ ProjectTable.uuid inList uuids }) {
            it[ProjectTable.status] = status.name.lowercase()
        }
    }

    override fun updateManagerByUuids(uuids: List<String>, managerId: Long): Int = transaction {
        ProjectTable.update({ ProjectTable.uuid inList uuids }) {
            it[ProjectTable.managerId] = managerId
        }
    }
}

private fun String.toProjectType(): ProjectType = when (this) {
    "project" -> ProjectType.PROJECT
    "subproject" -> ProjectType.SUBPROJECT
    "subproject_part" -> ProjectType.SUBPROJECT_PART
    else -> error("Unknown project type: $this")
}

private fun List<ResultRow>.toAmounts(): Map<String, ProjectAmount> = associate { row ->
    row[ProjectAmountTable.kind] to ProjectAmount(
        row[ProjectAmountTable.amount], row[ProjectAmountTable.currency], row[ProjectAmountTable.convertedAmount],
        row[ProjectAmountTable.uahPerEur], row[ProjectAmountTable.rateDate], row[ProjectAmountTable.conversionEdited]
    )
}
