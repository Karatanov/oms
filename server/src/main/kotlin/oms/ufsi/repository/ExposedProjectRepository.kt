package oms.ufsi.repository

import oms.ufsi.database.tables.ProjectTable
import oms.ufsi.database.tables.ProjectDocumentTable
import oms.ufsi.database.tables.FinancialRecordTable
import oms.ufsi.domain.Project
import oms.ufsi.domain.ProjectStatus
import oms.ufsi.domain.ProjectType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
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
                    address = row[ProjectTable.address],
                    region = row[ProjectTable.region],
                    city = row[ProjectTable.city],
                    latitude = row[ProjectTable.latitude].toDouble(),
                    longitude = row[ProjectTable.longitude].toDouble(),
                    status = ProjectStatus.valueOf(row[ProjectTable.status].uppercase()),
                    sector = row[ProjectTable.sector],
                    constructionType = row[ProjectTable.constructionType],
                    budgetPlanned = row[ProjectTable.budgetPlanned],
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
        managerId: Long
    ): Project = transaction {

        val projectUuid = UUID.randomUUID()

        val projectId = ProjectTable.insertAndGetId {
            it[ProjectTable.uuid] = projectUuid.toString()
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
            it[ProjectTable.currency] = "UAH"
            it[ProjectTable.managerId] = managerId
        }

        Project(
            id = projectId.value,
            uuid = projectUuid,
            projectType = ProjectType.PROJECT,
            parentProjectId = null,
            name = name,
            siteName = siteName,
            siteNumber = siteNumber,
            latitude = latitude,
            longitude = longitude,
            address = address,
            region = region,
            city = city,
            status = ProjectStatus.PLANNED,
            sector = sector,
            constructionType = constructionType,
            budgetPlanned = budgetPlanned,
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
}
