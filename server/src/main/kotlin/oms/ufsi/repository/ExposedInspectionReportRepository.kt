package oms.ufsi.repository

import java.time.LocalDate
import oms.ufsi.database.tables.InspectionReportTable
import oms.ufsi.domain.InspectionReport
import oms.ufsi.domain.InspectionReportStatus
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import java.util.*

/**
 * Реалізація репозиторію інспекцій
 * на базі Exposed.
 */
class ExposedInspectionReportRepository :
    InspectionReportRepository {

    override fun findAll(): List<InspectionReport> = transaction {
        InspectionReportTable.selectAll().map(::toInspectionReport)
    }

    override fun findByProjectId(
        projectId: Long
    ): List<InspectionReport> = transaction {

        InspectionReportTable
            .selectAll()
            .filter {

                it[
                    InspectionReportTable.projectId
                ].value == projectId
            }
            .map(::toInspectionReport)
    }

    private fun toInspectionReport(row: org.jetbrains.exposed.v1.core.ResultRow) = InspectionReport(
        id = row[InspectionReportTable.id].value,
        uuid = UUID.fromString(row[InspectionReportTable.uuid]),
        projectId = row[InspectionReportTable.projectId].value,
        reportCode = row[InspectionReportTable.reportCode],
        inspectionType = row[InspectionReportTable.inspectionType],
        inspectionDate = row[InspectionReportTable.inspectionDate],
        summary = row[InspectionReportTable.summary],
        status = InspectionReportStatus.valueOf(row[InspectionReportTable.status].uppercase()),
        rejectionReason = row[InspectionReportTable.rejectionReason],
        latitude = row[InspectionReportTable.latitude]?.toDouble(),
        longitude = row[InspectionReportTable.longitude]?.toDouble(),
        createdBy = row[InspectionReportTable.createdBy].value
    )


    /**
     * Виконує пошук інспекції
     * за її публічним UUID.
     */
    override fun findByUuid(
        uuid: String
    ): InspectionReport? = transaction {

        InspectionReportTable
            .selectAll()
            .firstOrNull { it[InspectionReportTable.uuid] == uuid }
            ?.let { row ->

                InspectionReport(

                    id =
                        row[
                            InspectionReportTable.id
                        ].value,

                    uuid =
                        UUID.fromString(
                            row[
                                InspectionReportTable.uuid
                            ]
                        ),

                    projectId =
                        row[
                            InspectionReportTable.projectId
                        ].value,

                    reportCode = row[InspectionReportTable.reportCode],

                    inspectionType = row[InspectionReportTable.inspectionType],

                    inspectionDate =
                        row[
                            InspectionReportTable.inspectionDate
                        ],

                    summary =
                        row[
                            InspectionReportTable.summary
                        ],

                    status = InspectionReportStatus.valueOf(
                        row[InspectionReportTable.status].uppercase()
                    ),

                    rejectionReason = row[InspectionReportTable.rejectionReason],

                    latitude = row[InspectionReportTable.latitude]?.toDouble(),

                    longitude = row[InspectionReportTable.longitude]?.toDouble(),

                    createdBy =
                        row[
                            InspectionReportTable.createdBy
                        ].value
                )
            }
    }

    /**
     * Створює новий звіт інспекції.
     */
    override fun create(
        projectId: Long,
        inspectionDate: String,
        summary: String?,
        createdBy: Long,
        reportCode: String?,
        inspectionType: String,
        latitude: Double?,
        longitude: Double?
    ): InspectionReport = transaction {

        val reportUuid = UUID.randomUUID()

        val reportId =
            InspectionReportTable
                .insertAndGetId {

                    it[uuid] =
                        reportUuid.toString()

                    it[this.projectId] =
                        projectId

                    it[this.reportCode] = reportCode

                    it[this.inspectionType] = inspectionType

                    it[this.inspectionDate] =
                        LocalDate.parse(
                            inspectionDate
                        )

                    it[this.summary] =
                        summary

                    it[this.createdBy] =
                        createdBy

                    it[status] = "draft"

                    it[this.latitude] = latitude?.toBigDecimal()

                    it[this.longitude] = longitude?.toBigDecimal()
                }

        InspectionReport(

            id = reportId.value,

            uuid = reportUuid,

            projectId = projectId,

            reportCode = reportCode,

            inspectionType = inspectionType,

            inspectionDate =
                LocalDate.parse(
                    inspectionDate
                ),

            summary = summary,

            status = InspectionReportStatus.DRAFT,

            rejectionReason = null,

            latitude = latitude,

            longitude = longitude,

            createdBy = createdBy
        )
    }

    override fun update(
        uuid: String,
        inspectionDate: String,
        summary: String?,
        reportCode: String?,
        inspectionType: String,
        latitude: Double?,
        longitude: Double?
    ): InspectionReport? {
        val count = transaction {
            InspectionReportTable.update({ InspectionReportTable.uuid eq uuid }) {
                it[this.inspectionDate] = LocalDate.parse(inspectionDate)
                it[this.summary] = summary
                it[this.reportCode] = reportCode
                it[this.inspectionType] = inspectionType
                it[this.latitude] = latitude?.toBigDecimal()
                it[this.longitude] = longitude?.toBigDecimal()
            }
        }
        return if (count == 0) null else findByUuid(uuid)
    }

    override fun changeStatus(
        uuid: String,
        status: String,
        rejectionReason: String?
    ): InspectionReport? {
        val count = transaction {
            val now = java.time.LocalDateTime.now()
            InspectionReportTable.update({ InspectionReportTable.uuid eq uuid }) {
                it[InspectionReportTable.status] = status
                it[this.rejectionReason] = rejectionReason
                if (status == "pending_review") {
                    it[this.submittedAt] = now
                }
                if (status == "completed") {
                    it[this.reviewedAt] = now
                }
            }
        }
        return if (count == 0) null else findByUuid(uuid)
    }

    override fun moveToProject(uuid: String, projectId: Long): InspectionReport? {
        val count = transaction {
            InspectionReportTable.update({ InspectionReportTable.uuid eq uuid }) {
                it[this.projectId] = projectId
            }
        }
        return if (count == 0) null else findByUuid(uuid)
    }

    override fun delete(uuid: String): Boolean = transaction {
        InspectionReportTable.deleteWhere { InspectionReportTable.uuid eq uuid } > 0
    }
}
