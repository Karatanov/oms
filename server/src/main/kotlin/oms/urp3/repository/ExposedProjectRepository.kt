package oms.urp3.repository

import oms.urp3.database.tables.ProjectTable
import oms.urp3.database.tables.ProgrammeDetailTable
import oms.urp3.database.tables.ProjectMonitoringDetailTable
import oms.urp3.database.tables.ProjectAmountTable
import oms.urp3.domain.ProjectAmount
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.insert
import oms.urp3.database.tables.ProjectDocumentTable
import oms.urp3.database.tables.FinancialRecordTable
import oms.urp3.database.tables.IncidentTable
import oms.urp3.database.tables.InspectionReportTable
import oms.urp3.database.tables.ProcurementRecordTable
import oms.urp3.domain.Project
import oms.urp3.domain.ProjectStatus
import oms.urp3.domain.ProjectType
import oms.urp3.domain.ProjectPatch
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import java.time.LocalDateTime
import java.util.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

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

/**
 * Counts retained records that prevent a physical project deletion.
 * Project amounts are owned by the project and deleted by their database
 * cascade, so they must not make an otherwise empty project undeletable.
 */
private fun projectDependencies(projectId: Long): Map<String, Long> = linkedMapOf(
    "subprojects or parts" to ProjectTable.selectAll().where { ProjectTable.parentProjectId eq projectId }.count(),
    "financial records" to FinancialRecordTable.selectAll().where { FinancialRecordTable.projectId eq projectId }.count(),
    "inspection reports" to InspectionReportTable.selectAll().where { InspectionReportTable.projectId eq projectId }.count(),
    "documents" to ProjectDocumentTable.selectAll().where { ProjectDocumentTable.projectId eq projectId }.count(),
    "incidents" to IncidentTable.selectAll().where { IncidentTable.projectId eq projectId }.count(),
    "monitoring details" to ProjectMonitoringDetailTable.selectAll().where { ProjectMonitoringDetailTable.projectId eq projectId }.count(),
    "procurement records" to ProcurementRecordTable.selectAll().where { ProcurementRecordTable.projectId eq projectId }.count(),
    "programme details" to ProgrammeDetailTable.selectAll().where { ProgrammeDetailTable.projectId eq projectId }.count()
).filterValues { it > 0 }

class ExposedProjectRepository : ProjectRepository {
    override fun monitoringDetails(projectId: Long) = transaction {
        ProjectMonitoringDetailTable.selectAll().where { ProjectMonitoringDetailTable.projectId eq projectId }.limit(1).firstOrNull()?.toMonitoringDetails()
    }

    override fun monitoringDetailsByProjectIds(projectIds: Collection<Long>) = transaction {
        if (projectIds.isEmpty()) emptyMap() else ProjectMonitoringDetailTable.selectAll()
            .where { ProjectMonitoringDetailTable.projectId inList projectIds }
            .associate { it[ProjectMonitoringDetailTable.projectId].value to it.toMonitoringDetails() }
    }

    private fun ResultRow.toMonitoringDetails(): oms.urp3.domain.ProjectMonitoringDetails {
        val row = this
        return oms.urp3.domain.ProjectMonitoringDetails(
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
                row[ProjectMonitoringDetailTable.sourceWorkbook],
                row[ProjectMonitoringDetailTable.contractorNameEn], row[ProjectMonitoringDetailTable.dedUrl],
                row[ProjectMonitoringDetailTable.estimateUrl], row[ProjectMonitoringDetailTable.expertiseUrl],
                row[ProjectMonitoringDetailTable.permitUrl], row[ProjectMonitoringDetailTable.constructionActNumber],
                row[ProjectMonitoringDetailTable.constructionActDate], row[ProjectMonitoringDetailTable.completedWorkPct],
                row[ProjectMonitoringDetailTable.financingUsedPct], row[ProjectMonitoringDetailTable.paidFinancingPct],
                row[ProjectMonitoringDetailTable.constructionWorkSummary], row[ProjectMonitoringDetailTable.constructionWorkComments],
                row[ProjectMonitoringDetailTable.registryJson]?.let { raw ->
                    runCatching { Json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
                }.orEmpty()
            )
    }
    override fun managerIdForUuid(uuid: String): Long? = transaction {
        ProjectTable.select(ProjectTable.managerId).where { ProjectTable.uuid eq uuid }.limit(1).firstOrNull()
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
            .map { row -> row.toProject(amountsByProject[row[ProjectTable.id].value].orEmpty().toAmounts()) }
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
                val amounts = ProjectAmountTable.selectAll()
                    .where { ProjectAmountTable.projectId eq row[ProjectTable.id] }
                    .toList()
                    .toAmounts()
                row.toProject(amounts)
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
            constructionContractSigningDate = null,
            constructionStartDate = null,
            projectedCompletionTime = null,
            currency = "EUR",
            contractorName = null
        )
    }

    override fun deleteByUuid(uuid: String): Boolean = transaction {
        // A record can be physically deleted only when it has no retained
        // historical relationship. Owned amount rows are removed by cascade.
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
            it[status] = ProjectStatus.ARCHIVED.name.lowercase()
        } > 0
    }

    override fun restoreByUuid(uuid: String): Boolean = transaction {
        val root = ProjectTable.selectAll().where { ProjectTable.uuid eq uuid }.firstOrNull() ?: return@transaction false
        val hierarchy = ProjectTable.select(ProjectTable.id, ProjectTable.parentProjectId).toList()
        val children = hierarchy.groupBy({ it[ProjectTable.parentProjectId]?.value }, { it[ProjectTable.id].value })
        val ids = projectDeletionOrder(root[ProjectTable.id].value, children)
        ProjectTable.update({ ProjectTable.id inList ids }) {
            it[isArchived] = false; it[archivedAt] = null; it[ProjectTable.archivedBy] = null
            it[status] = ProjectStatus.ACTIVE.name.lowercase()
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
                it[constructionContractSigningDate] = patch.constructionContractSigningDate
                it[constructionStartDate] = patch.constructionStartDate
                it[projectedCompletionTime] = patch.projectedCompletionTime
                it[currency] = patch.currency
                it[contractorName] = patch.contractorName
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

/** Maps a project row consistently for both list and single-project reads. */
private fun ResultRow.toProject(amounts: Map<String, ProjectAmount>): Project = Project(
    id = this[ProjectTable.id].value,
    uuid = UUID.fromString(this[ProjectTable.uuid]),
    projectType = this[ProjectTable.projectType].toProjectType(),
    trancheNumber = this[ProjectTable.trancheNumber],
    parentProjectId = this[ProjectTable.parentProjectId]?.value,
    name = this[ProjectTable.name],
    siteName = this[ProjectTable.siteName],
    siteNumber = this[ProjectTable.siteNumber],
    description = this[ProjectTable.description],
    address = this[ProjectTable.address],
    region = this[ProjectTable.region],
    city = this[ProjectTable.city],
    latitude = this[ProjectTable.latitude]?.toDouble(),
    longitude = this[ProjectTable.longitude]?.toDouble(),
    status = ProjectStatus.valueOf(this[ProjectTable.status].uppercase()),
    sector = this[ProjectTable.sector],
    constructionType = this[ProjectTable.constructionType],
    budgetPlanned = this[ProjectTable.budgetPlanned],
    engineerConsultantContractAmount = this[ProjectTable.engineerConsultantContractAmount],
    technicalSupervisionAmount = this[ProjectTable.technicalSupervisionAmount],
    subprojectContractAmount = this[ProjectTable.subprojectContractAmount],
    startDate = this[ProjectTable.startDate],
    endDate = this[ProjectTable.endDate],
    contractSignedDate = this[ProjectTable.contractSignedDate],
    plannedEndDate = this[ProjectTable.plannedEndDate],
    constructionContractSigningDate = this[ProjectTable.constructionContractSigningDate],
    constructionStartDate = this[ProjectTable.constructionStartDate],
    projectedCompletionTime = this[ProjectTable.projectedCompletionTime],
    currency = this[ProjectTable.currency],
    contractorName = this[ProjectTable.contractorName],
    constructionContractNumber = this[ProjectTable.constructionContractNumber],
    technicalSupervisionName = this[ProjectTable.technicalSupervisionName],
    technicalSupervisionContractNumber = this[ProjectTable.technicalSupervisionContractNumber],
    technicalSupervisionContractDate = this[ProjectTable.technicalSupervisionContractDate],
    technicalSupervisionStartDate = this[ProjectTable.technicalSupervisionStartDate],
    technicalSupervisionPlannedEndDate = this[ProjectTable.technicalSupervisionPlannedEndDate],
    engineerConsultantName = this[ProjectTable.engineerConsultantName],
    engineerConsultantContractNumber = this[ProjectTable.engineerConsultantContractNumber],
    engineerConsultantContractDate = this[ProjectTable.engineerConsultantContractDate],
    engineerConsultantStartDate = this[ProjectTable.engineerConsultantStartDate],
    engineerConsultantPlannedEndDate = this[ProjectTable.engineerConsultantPlannedEndDate],
    amounts = amounts,
    isArchived = this[ProjectTable.isArchived],
    archivedAt = this[ProjectTable.archivedAt],
    archivedBy = this[ProjectTable.archivedBy]?.value
)

private fun List<ResultRow>.toAmounts(): Map<String, ProjectAmount> = associate { row ->
    row[ProjectAmountTable.kind] to ProjectAmount(
        row[ProjectAmountTable.amount], row[ProjectAmountTable.currency], row[ProjectAmountTable.convertedAmount],
        row[ProjectAmountTable.uahPerEur], row[ProjectAmountTable.rateDate], row[ProjectAmountTable.conversionEdited]
    )
}
