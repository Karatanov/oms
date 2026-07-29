package oms.ufsi.repository

import java.time.LocalDate
import oms.ufsi.database.tables.InspectionReportTable
import oms.ufsi.domain.InspectionReport
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
            .select(
                InspectionReportTable.uuid eq uuid
            )
            .singleOrNull()
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

            createdBy = createdBy
        )
    }
}
