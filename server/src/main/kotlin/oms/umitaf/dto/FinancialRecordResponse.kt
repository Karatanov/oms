package oms.umitaf.dto

import kotlinx.serialization.Serializable

@Serializable
data class FinancialRecordResponse(
    val uuid: String, val recordType: String, val referenceNumber: String,
    val amount: Double, val currency: String, val recordDate: String,
    val paymentDate: String?, val description: String?, val milestone: String?,
    val paymentPurpose: String = "works",
    val eurExchangeRate: Double? = null,
    val eurExchangeDate: String? = null,
    val amountEurCents: Long? = null
)

@Serializable
data class FinancialProgressItemResponse(
    val label: String,
    val contractAmountUah: Double,
    val completedAmountUah: Double,
    val completionPct: Double
)

@Serializable
data class FinancialProgressResponse(
    val byRegion: List<FinancialProgressItemResponse> = emptyList(),
    val byConstructionType: List<FinancialProgressItemResponse> = emptyList(),
    val bySector: List<FinancialProgressItemResponse> = emptyList()
)

/** Read-only cumulative indicators from the approved UR III payment workbook. */
@Serializable
data class PaymentMonitoringItemResponse(
    val code: String,
    val totalInvestmentUah: Double? = null,
    val eibInvestmentUah: Double? = null,
    val localCoFinancingUah: Double? = null,
    val paidAndInProgressUah: Double? = null,
    val paidUah: Double? = null,
    val remainingUah: Double? = null,
    val plannedEibSharePct: Double? = null,
    val factualEibSharePct: Double? = null,
    val eibShareCompliance: String? = null
)

@Serializable
data class PaymentMonitoringResponse(
    val refreshedAt: String,
    val items: List<PaymentMonitoringItemResponse> = emptyList()
)

@Serializable
data class PaymentMonitoringImportResponse(
    val imported: Int,
    val refreshedAt: String
)
