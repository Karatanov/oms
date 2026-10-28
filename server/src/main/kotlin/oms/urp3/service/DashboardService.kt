package oms.urp3.service

import oms.urp3.database.tables.*
import oms.urp3.config.AppContainer
import oms.urp3.domain.InspectionReport
import oms.urp3.domain.InspectionReportStatus
import oms.urp3.domain.ProjectAmount
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.time.LocalDate
import kotlin.math.roundToLong

data class MonthlyActPayment(val month: String, val amountEurCents: Long, val amountUahCents: Long? = null)
/**
 * The approved amount in both presentation currencies.  Values are taken from
 * the frozen project conversion, never from a live NBU request made while the
 * dashboard is loading.
 */
data class DashboardSubprojectFunding(
    val projectUuid: String,
    val name: String,
    val region: String,
    val amountUah: Double,
    val amountEur: Double
)
data class DashboardSubprojectProgress(val projectUuid: String, val code: String, val name: String, val nameEn: String?, val region: String, val completionPct: Double)
data class DashboardMetric(val label: String, val value: Long)
data class DashboardRecentInspection(val report: InspectionReport, val subprojectCode: String?)
data class DashboardNationalSummary(
    val subprojectsCount: Long,
    val regionsCount: Long,
    val municipalitiesCount: Long,
    val settlementsCount: Long,
    val approvedFundingUah: Double,
    val approvedFundingEur: Double,
    val signedContractsCount: Long,
    val signedContractsAmountUah: Double,
    val servicesPaidEurCents: Long,
    val servicesPaidUahCents: Long?,
    /** Actual paid amount multiplied by the factual EIB share from the approved payment-monitoring register. */
    val eibPaidUnderContractsUah: Double? = null
)
/**
 * Signed-contract indicators mirrored from the engineering dashboard.  The
 * source workbook records actual contract values in UAH, so no speculative
 * EUR conversion is applied here.
 */
data class DashboardContractTypeMetric(
    val type: String,
    val contractsCount: Long,
    val contractAmountUah: Double
)
data class DashboardOverviewData(
    val recentInspections: List<DashboardRecentInspection>,
    val monthlyActPayments: List<MonthlyActPayment>,
    val subprojectFunding: List<DashboardSubprojectFunding>,
    val subprojectProgress: List<DashboardSubprojectProgress>,
    val procurementStatusCounts: List<DashboardMetric>,
    val signedContractsByType: List<DashboardContractTypeMetric>,
    val nationalSummary: DashboardNationalSummary
)

private val procurementStatusOrder = listOf(
    "Не розпочато / Not Started",
    "Закупівля триває / Tender Ongoing",
    "Повідомлення про намір укласти договір / Contract award notice",
    "Договір укладено / Contract signed",
    "Відмінено / Cancelled",
    "Договір розірвано / Contract terminated"
)

/**
 * Source procurement spreadsheets contain a few technical values (for example
 * a batch id) in otherwise free-text cells.  A dashboard category must only
 * ever be one of the six statuses agreed for the UI, so normalize bilingual
 * values and ignore anything else rather than rendering a phantom bar.
 */
private fun canonicalProcurementStatus(value: String?): String? {
    val status = value?.trim()?.lowercase().orEmpty()
    return when {
        status.contains("не розпочато") || status.contains("not started") -> procurementStatusOrder[0]
        status.contains("закупівля триває") || status.contains("tender ongoing") -> procurementStatusOrder[1]
        status.contains("повідомлення про намір") || status.contains("contract award notice") -> procurementStatusOrder[2]
        status.contains("договір укладено") || status.contains("contract signed") -> procurementStatusOrder[3]
        status.contains("відмінено") || status.contains("cancelled") -> procurementStatusOrder[4]
        status.contains("договір розірвано") || status.contains("contract terminated") -> procurementStatusOrder[5]
        else -> null
    }
}

private fun procurementStatusMetrics(procurementRecords: List<org.jetbrains.exposed.v1.core.ResultRow>): List<DashboardMetric> {
    val countByStatus = procurementRecords
        .mapNotNull { row -> canonicalProcurementStatus(row[ProcurementRecordTable.purchaseStatus])?.let { status ->
            status to (row[ProcurementRecordTable.procurementId]
                ?: row[ProcurementRecordTable.tenderId]
                ?: row[ProcurementRecordTable.subProjectId])
        } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, procurementIds) -> procurementIds.distinct().size.toLong() }
    return procurementStatusOrder.map { status -> DashboardMetric(status, countByStatus[status] ?: 0L) }
}

/**
 * Dashboard source data has historically used several spellings for the same
 * oblast: Ukrainian/English, with or without the "oblast" suffix, and in a
 * few cases the city adjective.  The national indicator must count actual
 * Ukrainian oblasts, rather than raw labels from source spreadsheets.
 *
 * Kyiv city is deliberately not included here: it is a city with special
 * status, not one of Ukraine's 24 oblasts.
 */
private fun canonicalUkraineOblast(value: String?): String? {
    val normalized = value.orEmpty()
        .lowercase()
        .replace('’', '\'')
        .replace(Regex("[^a-zа-яіїєґ']+"), " ")
        .replace(Regex("\\b(область|обл|oblast|region)\\b"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    return when {
        normalized.contains("вінни") || normalized.contains("vinnyt") -> "Vinnytsia"
        normalized.contains("волин") || normalized.contains("volyn") -> "Volyn"
        normalized.contains("дніпр") || normalized.contains("dnipro") -> "Dnipropetrovsk"
        normalized.contains("донець") || normalized.contains("donetsk") -> "Donetsk"
        normalized.contains("житом") || normalized.contains("zhytom") -> "Zhytomyr"
        normalized.contains("закар") || normalized.contains("zakarp") || normalized.contains("transcarpath") -> "Zakarpattia"
        normalized.contains("запор") || normalized.contains("zaporiz") -> "Zaporizhzhia"
        normalized.contains("івано") || normalized.contains("frankiv") || normalized.contains("ivano") -> "Ivano-Frankivsk"
        normalized.contains("київ") || normalized.contains("kyiv") || normalized.contains("kiev") -> "Kyiv"
        normalized.contains("кіров") || normalized.contains("kirov") -> "Kirovohrad"
        normalized.contains("луган") || normalized.contains("luhansk") || normalized.contains("lugansk") -> "Luhansk"
        normalized.contains("львів") || normalized.contains("lviv") || normalized.contains("l'viv") -> "Lviv"
        normalized.contains("микола") || normalized.contains("mykol") || normalized.contains("nikola") -> "Mykolaiv"
        normalized.contains("одес") || normalized.contains("odes") -> "Odesa"
        normalized.contains("полтав") || normalized.contains("poltav") -> "Poltava"
        normalized.contains("рівнен") || normalized.contains("rivne") || normalized.contains("rovno") -> "Rivne"
        normalized.contains("сумсь") || normalized == "sumy" -> "Sumy"
        normalized.contains("терноп") || normalized.contains("ternop") -> "Ternopil"
        normalized.contains("харків") || normalized.contains("khark") || normalized.contains("hark") -> "Kharkiv"
        normalized.contains("херсон") || normalized.contains("kherson") -> "Kherson"
        normalized.contains("хмель") || normalized.contains("khmel") || normalized.contains("hmeln") -> "Khmelnytskyi"
        normalized.contains("черкас") || normalized.contains("cherkas") -> "Cherkasy"
        normalized.contains("чернів") || normalized.contains("cherniv") -> "Chernivtsi"
        normalized.contains("черніг") || normalized.contains("chernih") || normalized.contains("chernig") -> "Chernihiv"
        else -> null
    }
}

private fun isConstructionContractSigned(status: String?) =
    status?.let { it.contains("Договір укладено", ignoreCase = true) || it.contains("Contract signed", ignoreCase = true) } == true

/** A tracker procurement can have several DB6.3 contract rows but only one AI total. */
private fun List<org.jetbrains.exposed.v1.core.ResultRow>.uniqueProcurements() =
    distinctBy { row ->
        row[ProcurementRecordTable.procurementId]
            ?: row[ProcurementRecordTable.tenderId]
            ?: "record-${row[ProcurementRecordTable.id].value}"
    }

private fun signedContractAmount(row: org.jetbrains.exposed.v1.core.ResultRow): Double =
    row[ProcurementRecordTable.signedContractAmountUah]?.toDouble()
        ?: row[ProcurementRecordTable.contractAmountUah]?.toDouble()
        ?: 0.0

private fun isWorksContract(type: String?) = type?.let {
    it.contains("робот", ignoreCase = true) || it.contains("works", ignoreCase = true)
} == true

private val dashboardContractTypeOrder = listOf(
    "dashboard_contract_works",
    "dashboard_contract_technical_supervision",
    "dashboard_contract_engineer_consultant"
)

private fun canonicalDashboardContractType(type: String?): String? {
    val value = type?.trim()?.lowercase().orEmpty()
    return when {
        value == "ts" || value.contains("technical supervision") || value.contains("технічн") ->
            "dashboard_contract_technical_supervision"
        value == "csc" || value.contains("engineer-consultant") || value.contains("engineer consultant") ||
            value.contains("інженер") || value.contains("консультант") ->
            "dashboard_contract_engineer_consultant"
        value == "w" || value.contains("works") || value.contains("робот") -> "dashboard_contract_works"
        else -> null
    }
}

private fun signedContractTypeMetrics(
    procurementRecords: List<org.jetbrains.exposed.v1.core.ResultRow>
): List<DashboardContractTypeMetric> {
    val signedByType = procurementRecords.asSequence()
        .filter { isConstructionContractSigned(it[ProcurementRecordTable.purchaseStatus]) }
        .mapNotNull { row -> canonicalDashboardContractType(row[ProcurementRecordTable.sourceContractType])?.let { it to row } }
        .groupBy({ it.first }, { it.second })
    return dashboardContractTypeOrder.map { type ->
        val rows = signedByType[type].orEmpty().uniqueProcurements()
        DashboardContractTypeMetric(
            type = type,
            contractsCount = rows.size.toLong(),
            contractAmountUah = rows.sumOf(::signedContractAmount)
        )
    }
}

/** Source workbooks identify tranches as batches 8 and 9; user-created records
 * use the application values 1 and 2. Both pairs represent the same A/B cut. */
private fun Int.matchesTrancheFilter(filter: Int?): Boolean = filter == null || when (filter) {
    1 -> this == 1 || this == 8
    2 -> this == 2 || this == 9
    else -> false
}

/**
 * Procurement workbooks use batch IDs 8 and 9, while legacy application rows
 * use 1 and 2.  Filtering at the database boundary avoids reading the whole
 * procurement registry just to discard the other tranche in memory.
 */
private fun procurementBatchIdsForTranche(filter: Int?): List<Int>? = when (filter) {
    null -> null
    1 -> listOf(1, 8)
    2 -> listOf(2, 9)
    else -> emptyList()
}

/**
 * Fetch only the project scope needed by a dashboard request.  The final
 * in-memory predicate preserves the established A/B mapping for legacy data,
 * while the common manager and tranche filters now narrow the database read.
 */
private fun dashboardProjects(
    allowedProjectIds: Set<Long>?,
    trancheNumber: Int?
): List<org.jetbrains.exposed.v1.core.ResultRow> {
    if (allowedProjectIds?.isEmpty() == true) return emptyList()
    val trancheValues = procurementBatchIdsForTranche(trancheNumber)
    if (trancheValues?.isEmpty() == true) return emptyList()

    val rows = when {
        allowedProjectIds != null -> ProjectTable.selectAll()
            .where { ProjectTable.id inList allowedProjectIds.toList() }
            .toList()
        trancheValues != null -> ProjectTable.selectAll()
            .where { ProjectTable.trancheNumber inList trancheValues }
            .toList()
        else -> ProjectTable.selectAll().toList()
    }
    return rows.filter { row ->
        (allowedProjectIds == null || row[ProjectTable.id].value in allowedProjectIds) &&
            row[ProjectTable.trancheNumber].matchesTrancheFilter(trancheNumber)
    }
}

private fun org.jetbrains.exposed.v1.core.ResultRow.amountUahCents(): Long? {
    val amount = this[FinancialRecordTable.amount].toDouble()
    return when (this[FinancialRecordTable.currency].uppercase()) {
        "UAH" -> (amount * 100).roundToLong()
        "EUR" -> this[FinancialRecordTable.eurExchangeRate]
            ?.toDouble()
            ?.takeIf { it.isFinite() && it > 0 }
            ?.let { (amount * it * 100).roundToLong() }
        else -> null
    }
}

private fun monthlyPayments(rows: List<org.jetbrains.exposed.v1.core.ResultRow>): List<MonthlyActPayment> {
    data class MonthlyTotal(
        var amountEurCents: Long = 0L,
        var amountUahCents: Long = 0L,
        var hasCompleteUahAmounts: Boolean = true
    )

    // A dashboard response can cover several payment categories. Aggregate
    // directly instead of allocating a list of rows for every month, while
    // retaining the rule that UAH is displayed only when every row has a
    // reliable conversion.
    val totalsByMonth = linkedMapOf<String, MonthlyTotal>()
    rows.forEach { row ->
        val eurCents = row[FinancialRecordTable.amountEurCents] ?: return@forEach
        val month = row[FinancialRecordTable.recordDate].toString().take(7)
        val total = totalsByMonth.getOrPut(month, ::MonthlyTotal)
        total.amountEurCents += eurCents
        row.amountUahCents()?.let { total.amountUahCents += it }
            ?: run { total.hasCompleteUahAmounts = false }
    }
    return totalsByMonth.map { (month, total) ->
        MonthlyActPayment(
            month = month,
            amountEurCents = total.amountEurCents,
            amountUahCents = total.amountUahCents.takeIf { total.hasCompleteUahAmounts }
        )
    }.sortedBy { it.month }
}

/** The approved payment workbook is the preferred source for disbursement charts. */
private fun monthlySourcePayments(
    transactions: List<PaymentMonitoringWorkbookSource.Transaction>,
    trancheNumber: Int?
): List<MonthlyActPayment> = transactions
    .asSequence()
    .filter { trancheNumber == null || it.batchId == trancheNumber + 7 }
    .groupBy { it.paymentDate.toString().take(7) }
    .map { (month, rows) ->
        MonthlyActPayment(
            month = month,
            amountEurCents = rows.sumOf { ((it.amountEur ?: 0.0) * 100).roundToLong() },
            amountUahCents = rows.sumOf { (it.amountUah * 100).roundToLong() }
        )
    }
    .sortedBy { it.month }
data class DashboardData(
    val projectsTotal:Long, val projectsActive:Long, val projectsCompletedThisMonth:Long, val budgetPlanned:Long,
    val amountSpent:Long, val inspectionsTotal:Long, val pendingInspections:Long, val findingsTotal:Long,
    val recentInspections:List<InspectionReport>, val activities:List<ActivityEntry>, val monthlyActPayments:List<MonthlyActPayment>,
    val subprojectFunding: List<DashboardSubprojectFunding>, val subprojectProgress: List<DashboardSubprojectProgress>,
    val procurementStatusCounts: List<DashboardMetric>, val monthlyInspectionCounts: List<DashboardMetric>,
    val monthlyEshsViolations: List<DashboardMetric>, val monthlyEquipmentPayments: List<MonthlyActPayment>,
    val monthlySignedConstructionContracts: List<DashboardMetric>
)

class DashboardService(
    private val auditLogService: AuditLogService,
    private val paymentMonitoringService: PaymentMonitoringService? = null
) {
    /**
     * Data used only by the visual Dashboard.  Keep it independent from the
     * broader administrative Dashboard payload so opening the workspace does
     * not wait for audit history, inspection findings or unused financial cuts.
     */
    fun getOverview(allowedProjectIds: Set<Long>? = null, trancheNumber: Int? = null): DashboardOverviewData {
        val sourcedPayments = paymentMonitoringService?.currentIfAvailable()?.transactions.orEmpty()
        return transaction {
        val projects = dashboardProjects(allowedProjectIds, trancheNumber)
        val projectIds = projects.map { it[ProjectTable.id].value }.toSet()
        // The overview renders only the five newest reports.  Apply that
        // window in SQL instead of transferring the full report history and
        // sorting it in application memory on every dashboard visit.
        val reports = if (projectIds.isEmpty()) emptyList() else InspectionReportTable.selectAll()
            .where { InspectionReportTable.projectId inList projectIds }
            .orderBy(InspectionReportTable.inspectionDate, SortOrder.DESC)
            .limit(5)
            .toList()
        // An empty project scope is a normal state for a freshly deployed or
        // fully filtered workspace. Do not send an empty IN predicate to the
        // database just to discover that there can be no ledger rows.
        val financialRecords = if (projectIds.isEmpty()) emptyList() else FinancialRecordTable.selectAll()
            .where { FinancialRecordTable.projectId inList projectIds }.toList()
        // Acts represent completed construction work and are used for the
        // subproject-completion chart below. Payments are a separate cash
        // flow used by the monthly disbursement chart.
        val actRecords = financialRecords.filter { it[FinancialRecordTable.recordType] == "act" }
        val paymentRecords = financialRecords.filter {
            it[FinancialRecordTable.recordType] in setOf("payment", "advance")
        }
        val monthlyActPayments = monthlySourcePayments(sourcedPayments, trancheNumber).ifEmpty { monthlyPayments(paymentRecords) }
        val projectsById = projects.associateBy { it[ProjectTable.id].value }
        val monitoringByProjectId = if (projectIds.isEmpty()) emptyMap() else ProjectMonitoringDetailTable.selectAll()
            .where { ProjectMonitoringDetailTable.projectId inList projectIds }.associateBy {
                it[ProjectMonitoringDetailTable.projectId].value
            }
        val nameEnByProjectId = monitoringByProjectId.mapValues { (_, row) -> row[ProjectMonitoringDetailTable.nameEn] }
        val subprojects = projects.filter { it[ProjectTable.projectType] == "subproject" }
        // The portfolio headline describes the approved programme scope, not
        // the wider screening register.  Screening candidates are retained in
        // OMS for traceability, but only active subprojects belong in funding
        // and national summary indicators.
        val portfolioSubprojects = subprojects.filter { it[ProjectTable.status] == "active" }
        val subprojectIdByCode = subprojects.mapNotNull { row ->
            row[ProjectTable.siteNumber].orEmpty().trim().takeIf(String::isNotBlank)?.lowercase()
                ?.let { code -> code to row[ProjectTable.id].value }
        }.toMap()
        val amountsByProject = if (projectIds.isEmpty()) emptyMap() else ProjectAmountTable.selectAll()
            .where { ProjectAmountTable.projectId inList projectIds }
            .groupBy { it[ProjectAmountTable.projectId].value }
            .mapValues { (_, rows) -> rows.associate { it[ProjectAmountTable.kind] to ProjectAmount(
                it[ProjectAmountTable.amount], it[ProjectAmountTable.currency], it[ProjectAmountTable.convertedAmount],
                it[ProjectAmountTable.uahPerEur], it[ProjectAmountTable.rateDate], it[ProjectAmountTable.conversionEdited]
            ) } }
        val subprojectFunding = portfolioSubprojects.map { row -> row.toFunding(amountsByProject[row[ProjectTable.id].value]) }
        fun subprojectFor(projectId: Long): Long? {
            var current = projectId
            while (true) {
                val project = projectsById[current] ?: return null
                if (project[ProjectTable.projectType] == "subproject") {
                    // Some legacy payment imports predate lot-to-part mapping
                    // and point at a temporary "CODE#LotN" subproject. Map
                    // it to CODE when present so Dashboard progress uses the
                    // real region and contract amount instead of a zero stub.
                    val siteCode = project[ProjectTable.siteNumber].orEmpty().trim()
                    val baseCode = siteCode.substringBefore('#').trim()
                    return if (baseCode.equals(siteCode, ignoreCase = true)) current else
                        subprojectIdByCode[baseCode.lowercase()] ?: current
                }
                current = project[ProjectTable.parentProjectId]?.value ?: return null
            }
        }
        // Completion is defined as completed-work acts / construction contract.
        // Convert every act to UAH before aggregating — a raw sum of UAH and
        // EUR was the reason mixed-currency portfolios could misleadingly show
        // 0% (or an unrelated percentage) for every subproject.
        val actualBySubproject = actRecords.groupBy { subprojectFor(it[FinancialRecordTable.projectId].value) }
            .filterKeys { it != null }
            .mapValues { (_, rows) -> rows.sumOf { (it.amountUahCents() ?: 0L).toDouble() / 100.0 } }
        val procurementRecords = if (allowedProjectIds == null) {
            procurementBatchIdsForTranche(trancheNumber)?.let { batchIds ->
                if (batchIds.isEmpty()) emptyList() else ProcurementRecordTable.selectAll()
                    .where { ProcurementRecordTable.batchId inList batchIds }.toList()
            } ?: ProcurementRecordTable.selectAll().toList()
        } else emptyList()
        val signedWorksAmountBySubprojectCode = procurementRecords.asSequence()
            .filter { record ->
                isConstructionContractSigned(record[ProcurementRecordTable.purchaseStatus]) &&
                    isWorksContract(record[ProcurementRecordTable.sourceContractType])
            }
            .groupBy { it[ProcurementRecordTable.subProjectId].trim().lowercase() }
            .mapValues { (_, records) -> records.uniqueProcurements().sumOf { signedContractAmount(it) } }
        // Progress must cover every subproject.  A missing or unsigned
        // construction contract simply results in 0%, rather than hiding its
        // region from the dashboard filter.
        val subprojectProgress = portfolioSubprojects.map { row ->
            val id = row[ProjectTable.id].value
            val contract = signedWorksAmountBySubprojectCode[row[ProjectTable.siteNumber].orEmpty().trim().lowercase()]
                ?: (row[ProjectTable.subprojectContractAmount] ?: row[ProjectTable.budgetPlanned]).toDouble()
            DashboardSubprojectProgress(
                row[ProjectTable.uuid], row[ProjectTable.siteNumber].orEmpty(), row[ProjectTable.name], nameEnByProjectId[id], row[ProjectTable.region].orEmpty(),
                if (contract > 0) (actualBySubproject[id] ?: 0.0) * 100.0 / contract else 0.0
            )
        }.sortedBy { it.name }
        val procurementStatusCounts = procurementStatusMetrics(procurementRecords)
        val signedContractsByType = signedContractTypeMetrics(procurementRecords)
        val signedContracts = procurementRecords.filter { record ->
            isConstructionContractSigned(record[ProcurementRecordTable.purchaseStatus])
        }
        val servicesPaidUah = paymentRecords.mapNotNull { it.amountUahCents() }
        // The payment-monitoring register is the only approved source that
        // carries both the amount actually paid and the factual EIB share.
        // Do not derive an EIB contribution from all ledger records: payments
        // for TS/CSC/equipment use different financing bases.
        val selectedSubprojectCodes = portfolioSubprojects
            .map { it[ProjectTable.siteNumber].substringBefore('#').trim().uppercase() }
            .toSet()
        val eibPaidUnderContractsUah = runCatching {
            val monitoringSnapshot = AppContainer.paymentMonitoringService.currentIfAvailable()
                ?: return@runCatching null
            monitoringSnapshot.items
                .asSequence()
                // The workbook has supporting `_W`, `_TS` and `_CSC` rows
                // in addition to the primary SP line. They are not separate
                // subprojects and must never be added to the KPI a second time.
                .filter { isPrimaryPaymentMonitoringSubprojectCode(it.code) }
                .filter { it.code.substringBefore('#').substringBefore('$').trim().uppercase() in selectedSubprojectCodes }
                .mapNotNull { item ->
                    val paid = item.paidUah?.takeIf { it.isFinite() } ?: return@mapNotNull null
                    val eibShare = item.factualEibSharePct?.takeIf { it.isFinite() } ?: return@mapNotNull null
                    paid * eibShare / 100.0
                }
                .sum()
        }.getOrNull()
        // The regional funding chart may present the construction-contract
        // value where one exists. The programme summary has a different,
        // source-defined meaning: it must always show approved subproject
        // funding (DB Monitoring Table: fundingTotal), never a later contract.
        val approvedFundingUah = portfolioSubprojects.sumOf { it[ProjectTable.budgetPlanned].toDouble() }
        val nationalSummary = DashboardNationalSummary(
            subprojectsCount = portfolioSubprojects.size.toLong(),
            regionsCount = portfolioSubprojects.mapNotNull { canonicalUkraineOblast(it[ProjectTable.region]) }.distinct().size.toLong(),
            municipalitiesCount = portfolioSubprojects.mapNotNull { row ->
                monitoringByProjectId[row[ProjectTable.id].value]?.get(ProjectMonitoringDetailTable.municipalityNameUk)
                    ?.trim()?.takeIf(String::isNotBlank)
            }.distinct().size.toLong(),
            settlementsCount = portfolioSubprojects.map { it[ProjectTable.city].orEmpty().trim() }.filter(String::isNotBlank).distinct().size.toLong(),
            approvedFundingUah = approvedFundingUah,
            approvedFundingEur = approvedFundingUah.toBigDecimal()
                .divide(DASHBOARD_UAH_PER_EUR, 2, RoundingMode.HALF_UP)
                .toDouble(),
            signedContractsCount = signedContracts.uniqueProcurements().size.toLong(),
            signedContractsAmountUah = signedContracts.uniqueProcurements().sumOf(::signedContractAmount),
            servicesPaidEurCents = paymentRecords.sumOf { it[FinancialRecordTable.amountEurCents] ?: 0L },
            servicesPaidUahCents = servicesPaidUah.takeIf { it.size == paymentRecords.size }?.sum(),
            eibPaidUnderContractsUah = eibPaidUnderContractsUah
        )
        fun report(row: org.jetbrains.exposed.v1.core.ResultRow) = InspectionReport(
            row[InspectionReportTable.id].value, UUID.fromString(row[InspectionReportTable.uuid]), row[InspectionReportTable.projectId].value,
            row[InspectionReportTable.reportCode], row[InspectionReportTable.inspectionType], row[InspectionReportTable.inspectionDate],
            row[InspectionReportTable.summary], InspectionReportStatus.valueOf(row[InspectionReportTable.status].uppercase()),
            row[InspectionReportTable.rejectionReason], row[InspectionReportTable.latitude]?.toDouble(), row[InspectionReportTable.longitude]?.toDouble(), row[InspectionReportTable.createdBy].value
        )
        DashboardOverviewData(
            reports.map { row ->
                val inspection = report(row)
                DashboardRecentInspection(
                    inspection,
                    subprojectFor(inspection.projectId)?.let { projectsById[it]?.get(ProjectTable.siteNumber) }
                )
            },
            monthlyActPayments, subprojectFunding, subprojectProgress, procurementStatusCounts, signedContractsByType, nationalSummary
        )
        }
    }

    fun get(allowedProjectIds: Set<Long>? = null, trancheNumber: Int? = null): DashboardData {
        val sourcedPayments = paymentMonitoringService?.currentIfAvailable()?.transactions.orEmpty()
        return transaction {
        val projects = dashboardProjects(allowedProjectIds, trancheNumber)
        val projectIds = projects.map { it[ProjectTable.id].value }.toSet()
        val reports = if (projectIds.isEmpty()) emptyList() else InspectionReportTable.selectAll()
            .where { InspectionReportTable.projectId inList projectIds }.toList()
        val reportIds = reports.map { it[InspectionReportTable.id].value }.toSet()
        // Read this table once for the scoped dashboard. The former two full
        // scans (acts and payments) were especially wasteful for the admin
        // screen, which also requests activity data.
        val financialRecords = if (projectIds.isEmpty()) emptyList() else FinancialRecordTable.selectAll()
            .where { FinancialRecordTable.projectId inList projectIds }.toList()
        val actRecords = financialRecords.filter { it[FinancialRecordTable.recordType] == "act" }
        val paymentRecords = financialRecords.filter {
            it[FinancialRecordTable.recordType] in setOf("payment", "advance")
        }
        val spent = actRecords.sumOf { it[FinancialRecordTable.amount] }.toDouble()
        val monthlyActPayments = monthlySourcePayments(sourcedPayments, trancheNumber).ifEmpty { monthlyPayments(paymentRecords) }
        val projectsById = projects.associateBy { it[ProjectTable.id].value }
        val nameEnByProjectId = if (projectIds.isEmpty()) emptyMap() else ProjectMonitoringDetailTable.selectAll()
            .where { ProjectMonitoringDetailTable.projectId inList projectIds }
            .associate { it[ProjectMonitoringDetailTable.projectId].value to it[ProjectMonitoringDetailTable.nameEn] }
        val subprojects = projects.filter { it[ProjectTable.projectType] == "subproject" }
        val subprojectIdByCode = subprojects.mapNotNull { row ->
            row[ProjectTable.siteNumber].orEmpty().trim().takeIf(String::isNotBlank)?.lowercase()
                ?.let { code -> code to row[ProjectTable.id].value }
        }.toMap()
        val amountsByProject = if (projectIds.isEmpty()) emptyMap() else ProjectAmountTable.selectAll()
            .where { ProjectAmountTable.projectId inList projectIds }
            .groupBy { it[ProjectAmountTable.projectId].value }
            .mapValues { (_, rows) -> rows.associate { it[ProjectAmountTable.kind] to ProjectAmount(
                it[ProjectAmountTable.amount], it[ProjectAmountTable.currency], it[ProjectAmountTable.convertedAmount],
                it[ProjectAmountTable.uahPerEur], it[ProjectAmountTable.rateDate], it[ProjectAmountTable.conversionEdited]
            ) } }
        val subprojectFunding = subprojects.map { row -> row.toFunding(amountsByProject[row[ProjectTable.id].value]) }
        fun subprojectFor(projectId: Long): Long? {
            var current = projectId
            while (true) {
                val project = projectsById[current] ?: return null
                if (project[ProjectTable.projectType] == "subproject") {
                    val siteCode = project[ProjectTable.siteNumber].orEmpty().trim()
                    val baseCode = siteCode.substringBefore('#').trim()
                    return if (baseCode.equals(siteCode, ignoreCase = true)) current else
                        subprojectIdByCode[baseCode.lowercase()] ?: current
                }
                current = project[ProjectTable.parentProjectId]?.value ?: return null
            }
        }
        val actualBySubproject = actRecords.groupBy { subprojectFor(it[FinancialRecordTable.projectId].value) }
            .filterKeys { it != null }
            .mapValues { (_, records) ->
                records.sumOf { (it.amountUahCents() ?: 0L).toDouble() / 100.0 }
            }
        val procurementRecords = if (allowedProjectIds == null) {
            procurementBatchIdsForTranche(trancheNumber)?.let { batchIds ->
                if (batchIds.isEmpty()) emptyList() else ProcurementRecordTable.selectAll()
                    .where { ProcurementRecordTable.batchId inList batchIds }.toList()
            } ?: ProcurementRecordTable.selectAll().toList()
        } else emptyList()
        val subprojectProgress = subprojects.map { row ->
            val id = row[ProjectTable.id].value
            val contract = row[ProjectTable.subprojectContractAmount] ?: row[ProjectTable.budgetPlanned]
            DashboardSubprojectProgress(row[ProjectTable.uuid], row[ProjectTable.siteNumber].orEmpty(), row[ProjectTable.name], nameEnByProjectId[id], row[ProjectTable.region].orEmpty(), if (contract > 0) (actualBySubproject[id] ?: 0.0) * 100.0 / contract else 0.0)
        }.sortedBy { it.name }
        fun month(date: java.time.LocalDate) = date.toString().take(7)
        val monthlyInspectionCounts = reports.groupBy { month(it[InspectionReportTable.inspectionDate]) }
            .map { DashboardMetric(it.key, it.value.size.toLong()) }.sortedBy { it.label }
        val reportMonthById = reports.associate { it[InspectionReportTable.id].value to month(it[InspectionReportTable.inspectionDate]) }
        val findingsRows = if (reportIds.isEmpty()) emptyList() else InspectionFindingTable.selectAll()
            .where { InspectionFindingTable.inspectionReportId inList reportIds }.toList()
        val monthlyEshsViolations = findingsRows
            .filter { finding ->
                val category = finding[InspectionFindingTable.category].lowercase()
                category.contains("hse") || category.contains("eshs") || category.contains("health") || category.contains("safety") || category.contains("environment") || category.contains("social")
            }
            .groupBy { reportMonthById[it[InspectionFindingTable.inspectionReportId].value] }
            .filterKeys { it != null }
            .map { DashboardMetric(it.key!!, it.value.size.toLong()) }.sortedBy { it.label }
        val monthlyEquipmentPayments = monthlyPayments(financialRecords
            .filter { it[FinancialRecordTable.paymentPurpose] == "equipment" && it[FinancialRecordTable.recordType] in setOf("payment", "advance") }
        )
        // Procurement reference rows do not carry a project foreign key. Do not
        // expose their global aggregate to a manager whose dashboard is scoped.
        val procurementStatusCounts = procurementStatusMetrics(procurementRecords)
        val monthlySignedConstructionContracts = procurementRecords
            .filter { isConstructionContractSigned(it[ProcurementRecordTable.purchaseStatus]) }
            .mapNotNull { row -> (row[ProcurementRecordTable.contractDate] ?: row[ProcurementRecordTable.estimatedContractDate])?.let(::month) }
            .groupingBy { it }.eachCount().map { DashboardMetric(it.key, it.value.toLong()) }.sortedBy { it.label }
        val findings = findingsRows.size
        // The admin activity panel filters and sorts locally, so return a useful
        // recent window without introducing a separate request for every user.
        val activities = if (allowedProjectIds == null) auditLogService.recent(limit = 100) else emptyList()
        val currentMonth = LocalDate.now()
        DashboardData(projects.size.toLong(), projects.count { it[ProjectTable.status] == "active" }.toLong(), projects.count { it[ProjectTable.status] == "completed" && it[ProjectTable.endDate]?.let { date -> date.year == currentMonth.year && date.month == currentMonth.month } == true }.toLong(), projects.sumOf { it[ProjectTable.budgetPlanned] }, spent.toLong(), reports.size.toLong(), reports.count { it[InspectionReportTable.status] == "pending_review" }.toLong(), findings.toLong(), reports.sortedByDescending { it[InspectionReportTable.inspectionDate] }.take(5).map { r -> InspectionReport(r[InspectionReportTable.id].value, UUID.fromString(r[InspectionReportTable.uuid]), r[InspectionReportTable.projectId].value, r[InspectionReportTable.reportCode], r[InspectionReportTable.inspectionType], r[InspectionReportTable.inspectionDate], r[InspectionReportTable.summary], InspectionReportStatus.valueOf(r[InspectionReportTable.status].uppercase()), r[InspectionReportTable.rejectionReason], r[InspectionReportTable.latitude]?.toDouble(), r[InspectionReportTable.longitude]?.toDouble(), r[InspectionReportTable.createdBy].value) }, activities, monthlyActPayments, subprojectFunding, subprojectProgress, procurementStatusCounts, monthlyInspectionCounts, monthlyEshsViolations, monthlyEquipmentPayments, monthlySignedConstructionContracts)
        }
    }
}

/** Supporting payment-monitoring rows describe works/TS/CSC components, not standalone SPs. */
internal fun isPrimaryPaymentMonitoringSubprojectCode(code: String): Boolean =
    !code.trim().uppercase().matches(Regex(".*_(W|TS|CSC)$"))

private fun org.jetbrains.exposed.v1.core.ResultRow.toFunding(
    amounts: Map<String, ProjectAmount>?
): DashboardSubprojectFunding {
    // Preserve the existing chart semantics: a construction contract is used
    // when it exists; otherwise the approved project budget is shown.
    val amount = amounts?.get("construction") ?: amounts?.get("budget")
    val legacyUah = this[ProjectTable.subprojectContractAmount] ?: this[ProjectTable.budgetPlanned]
    val uah = amount?.uah?.toDouble() ?: legacyUah.toDouble()
    // Regional funding is one comparison.  Individual records can retain
    // their own frozen conversion dates, while legacy imports have no EUR
    // conversion at all.  For this aggregate we therefore use the canonical
    // UAH total and one programme presentation rate; a currency toggle can
    // change labels, but never a region's relative share or colour.
    val eur = uah.toBigDecimal().divide(DASHBOARD_UAH_PER_EUR, 2, RoundingMode.HALF_UP).toDouble()
    return DashboardSubprojectFunding(
        this[ProjectTable.uuid], this[ProjectTable.name], this[ProjectTable.region].orEmpty(), uah, eur
    )
}

private val DASHBOARD_UAH_PER_EUR = BigDecimal("48.00")
