package oms.ufsi.repository

import oms.ufsi.database.tables.ProjectTable
import oms.ufsi.database.tables.ProgrammeDetailTable
import oms.ufsi.database.tables.ProjectMonitoringDetailTable
import oms.ufsi.database.tables.ProjectAmountTable
import oms.ufsi.domain.ProjectAmount
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.insert
import oms.ufsi.database.tables.ProjectDocumentTable
import oms.ufsi.database.tables.FinancialRecordTable
import oms.ufsi.database.tables.IncidentTable
import oms.ufsi.database.tables.InspectionReportTable
import oms.ufsi.domain.Project
import oms.ufsi.domain.ProjectStatus
import oms.ufsi.domain.ProjectType
import oms.ufsi.domain.ProjectPatch
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import java.util.*

class ExposedProjectRepository : ProjectRepository {
    override fun programmeDetails(projectId: Long) = transaction {
        ProgrammeDetailTable.selectAll().firstOrNull { it[ProgrammeDetailTable.projectId].value == projectId }?.let { row ->
            oms.ufsi.domain.ProgrammeDetails(
                row[ProgrammeDetailTable.implementor], row[ProgrammeDetailTable.financingInstitution],
                row[ProgrammeDetailTable.financeContractNumber], row[ProgrammeDetailTable.serapisNumber],
                row[ProgrammeDetailTable.agreementDate], row[ProgrammeDetailTable.loanAmount],
                row[ProgrammeDetailTable.loanCurrency], row[ProgrammeDetailTable.sourceWorkbook],
                row[ProgrammeDetailTable.sourceSnapshotDate]
            )
        }
    }

    override fun monitoringDetails(projectId: Long) = transaction {
        ProjectMonitoringDetailTable.selectAll().firstOrNull { it[ProjectMonitoringDetailTable.projectId].value == projectId }?.toMonitoringDetails()
    }

    override fun monitoringDetailsByProjectIds(projectIds: Collection<Long>) = transaction {
        if (projectIds.isEmpty()) emptyMap() else ProjectMonitoringDetailTable.selectAll()
            .filter { it[ProjectMonitoringDetailTable.projectId].value in projectIds }
            .associate { it[ProjectMonitoringDetailTable.projectId].value to it.toMonitoringDetails() }
    }

    private fun ResultRow.toMonitoringDetails(): oms.ufsi.domain.ProjectMonitoringDetails {
        val row = this
        return oms.ufsi.domain.ProjectMonitoringDetails(
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
                    amounts = amountsByProject[row[ProjectTable.id].value].orEmpty().toAmounts()
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
                    amounts = ProjectAmountTable.selectAll().where { ProjectAmountTable.projectId eq row[ProjectTable.id] }.toList().toAmounts()
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
            it[ProjectTable.trancheNumber] = 1
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
            trancheNumber = 1,
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
        val project = ProjectTable.selectAll().firstOrNull { it[ProjectTable.uuid] == uuid } ?: return@transaction false
        val projectIds = mutableListOf<Long>()

        // Children are collected before their parent, so self-referential FK restrictions
        // do not block removal of a complete project/subproject/part hierarchy.
        fun collectSubtree(projectId: Long) {
            ProjectTable.selectAll()
                .filter { it[ProjectTable.parentProjectId]?.value == projectId }
                .forEach { collectSubtree(it[ProjectTable.id].value) }
            projectIds += projectId
        }
        collectSubtree(project[ProjectTable.id].value)

        projectIds.forEach { projectId ->
            ProjectDocumentTable.deleteWhere { ProjectDocumentTable.projectId eq projectId }
            FinancialRecordTable.deleteWhere { FinancialRecordTable.projectId eq projectId }
            IncidentTable.deleteWhere { IncidentTable.projectId eq projectId }
            // Report descendants (source files, photos and findings) use DB cascades.
            InspectionReportTable.deleteWhere { InspectionReportTable.projectId eq projectId }
            ProjectTable.deleteWhere { ProjectTable.id eq projectId }
        }
        true
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
