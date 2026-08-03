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
            .map { row ->

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

                    inspectionDate =
                        row[
                            InspectionReportTable.inspectionDate
                        ],

                    completionPct =
                        row[
                            InspectionReportTable.completionPct
                        ].toDouble(),

                    summary =
                        row[
                            InspectionReportTable.summary
                        ],

                    status = InspectionReportStatus.valueOf(
                        row[InspectionReportTable.status].uppercase()
                    ),

                    rejectionReason = row[InspectionReportTable.rejectionReason],

                    createdBy =
                        row[
                            InspectionReportTable.createdBy
                        ].value
                )
            }
    }


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

                    inspectionDate =
                        row[
                            InspectionReportTable.inspectionDate
                        ],

                    completionPct =
                        row[
                            InspectionReportTable.completionPct
                        ].toDouble(),

                    summary =
                        row[
                            InspectionReportTable.summary
                        ],

                    status = InspectionReportStatus.valueOf(
                        row[InspectionReportTable.status].uppercase()
                    ),

                    rejectionReason = row[InspectionReportTable.rejectionReason],

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
        completionPct: Double,
        summary: String?,
        createdBy: Long
    ): InspectionReport = transaction {

        val reportUuid = UUID.randomUUID()

        val reportId =
            InspectionReportTable
                .insertAndGetId {

                    it[uuid] =
                        reportUuid.toString()

                    it[this.projectId] =
                        projectId

                    it[this.inspectionDate] =
                        LocalDate.parse(
                            inspectionDate
                        )

                    it[this.completionPct] =
                        completionPct.toBigDecimal()

                    it[this.summary] =
                        summary

                    it[this.createdBy] =
                        createdBy

                    it[status] = "draft"
                }

        InspectionReport(

            id = reportId.value,

            uuid = reportUuid,

            projectId = projectId,

            inspectionDate =
                LocalDate.parse(
                    inspectionDate
                ),

            completionPct = completionPct,

            summary = summary,

            status = InspectionReportStatus.DRAFT,

            rejectionReason = null,

            createdBy = createdBy
        )
    }

    override fun update(
        uuid: String,
        inspectionDate: String,
        completionPct: Double,
        summary: String?
    ): InspectionReport? = transaction {
        val count = InspectionReportTable.update({ InspectionReportTable.uuid eq uuid }) {
            it[this.inspectionDate] = LocalDate.parse(inspectionDate)
            it[this.completionPct] = completionPct.toBigDecimal()
            it[this.summary] = summary
        }
        if (count == 0) null else findByUuid(uuid)
    }

    override fun changeStatus(
        uuid: String,
        status: String,
        rejectionReason: String?
    ): InspectionReport? = transaction {
        val now = java.time.LocalDateTime.now()
        val count = InspectionReportTable.update({ InspectionReportTable.uuid eq uuid }) {
            it[InspectionReportTable.status] = status
            it[this.rejectionReason] = rejectionReason
            if (status == "pending_review") {
                it[this.submittedAt] = now
            }
            if (status == "completed") {
                it[this.reviewedAt] = now
            }
        }
        if (count == 0) null else findByUuid(uuid)
    }

    override fun moveToProject(uuid: String, projectId: Long): InspectionReport? = transaction {
        val count = InspectionReportTable.update({ InspectionReportTable.uuid eq uuid }) {
            it[this.projectId] = projectId
        }
        if (count == 0) null else findByUuid(uuid)
    }

    override fun delete(uuid: String): Boolean = transaction {
        InspectionReportTable.deleteWhere { InspectionReportTable.uuid eq uuid } > 0
    }
}
