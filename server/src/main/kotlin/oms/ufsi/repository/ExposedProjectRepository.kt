package oms.ufsi.repository

import oms.ufsi.database.tables.ProjectTable
import oms.ufsi.database.tables.ProjectDocumentTable
import oms.ufsi.database.tables.FinancialRecordTable
import oms.ufsi.domain.Project
import oms.ufsi.domain.ProjectStatus
import oms.ufsi.domain.ProjectType
import oms.ufsi.domain.ProjectPatch
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import java.util.*

class ExposedProjectRepository : ProjectRepository {

    override fun findAll(): List<Project> = transaction {
        ProjectTable
            .selectAll()
            .map { row ->
                Project(
                    id = row[ProjectTable.id].value,
                    uuid = UUID.fromString(row[ProjectTable.uuid]),
                    projectType = when (row[ProjectTable.projectType]) {
                        "project" -> ProjectType.PROJECT
                        else -> ProjectType.SUBPROJECT
                    },
                    parentProjectId = row[ProjectTable.parentProjectId]?.value,
                    name = row[ProjectTable.name],
                    siteName = row[ProjectTable.siteName],
                    siteNumber = row[ProjectTable.siteNumber],
                    description = row[ProjectTable.description],
                    address = row[ProjectTable.address],
                    region = row[ProjectTable.region],
                    city = row[ProjectTable.city],
                    latitude = row[ProjectTable.latitude].toDouble(),
                    longitude = row[ProjectTable.longitude].toDouble(),
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
                    constructionContractSigningDate = row[ProjectTable.constructionContractSigningDate],
                    constructionStartDate = row[ProjectTable.constructionStartDate],
                    projectedCompletionTime = row[ProjectTable.projectedCompletionTime],
                    currency = row[ProjectTable.currency],
                    contractorName = row[ProjectTable.contractorName]
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
            .firstOrNull { it[ProjectTable.uuid] == uuid }
            ?.let { row ->

                Project(

                    id = row[ProjectTable.id].value,

                    uuid = UUID.fromString(
                        row[ProjectTable.uuid]
                    ),

                    projectType =
                        when (row[ProjectTable.projectType]) {

                            "project" ->
                                ProjectType.PROJECT

                            else ->
                                ProjectType.SUBPROJECT
                        },

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
                            .toDouble(),

                    longitude =
                        row[ProjectTable.longitude]
                            .toDouble(),

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

                    constructionContractSigningDate =
                        row[ProjectTable.constructionContractSigningDate],

                    constructionStartDate =
                        row[ProjectTable.constructionStartDate],

                    projectedCompletionTime =
                        row[ProjectTable.projectedCompletionTime],

                    currency =
                        row[ProjectTable.currency],

                    contractorName =
                        row[ProjectTable.contractorName]
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
            it[ProjectTable.currency] = "UAH"
            it[ProjectTable.managerId] = managerId
        }

        Project(
            id = projectId.value,
            uuid = projectUuid,
            projectType = projectType,
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
            constructionContractSigningDate = null,
            constructionStartDate = null,
            projectedCompletionTime = null,
            currency = "UAH",
            contractorName = null
        )
    }

    override fun deleteByUuid(uuid: String): Boolean = transaction {
        val project = ProjectTable.selectAll().firstOrNull { it[ProjectTable.uuid] == uuid } ?: return@transaction false
        val projectId = project[ProjectTable.id].value
        ProjectDocumentTable.deleteWhere { ProjectDocumentTable.projectId eq projectId }
        FinancialRecordTable.deleteWhere { FinancialRecordTable.projectId eq projectId }
        ProjectTable.deleteWhere { ProjectTable.id eq projectId } > 0
    }

    override fun updateByUuid(uuid: String, patch: ProjectPatch): Project? {
        val updated = transaction {
            ProjectTable.update({ ProjectTable.uuid eq uuid }) {
                it[name] = patch.name
                it[siteName] = patch.siteName
                it[siteNumber] = patch.siteNumber
                it[address] = patch.address
                it[region] = patch.region
                it[city] = patch.city
                it[latitude] = patch.latitude.toBigDecimal()
                it[longitude] = patch.longitude.toBigDecimal()
                it[sector] = patch.sector
                it[constructionType] = patch.constructionType
                it[budgetPlanned] = patch.budgetPlanned
                it[engineerConsultantContractAmount] = patch.engineerConsultantContractAmount
                it[technicalSupervisionAmount] = patch.technicalSupervisionAmount
                it[subprojectContractAmount] = patch.subprojectContractAmount
                it[startDate] = patch.startDate
                it[contractSignedDate] = patch.contractSignedDate
                it[plannedEndDate] = patch.plannedEndDate
            }
        }
        return if (updated == 0) null else findByUuid(uuid)
    }
}
