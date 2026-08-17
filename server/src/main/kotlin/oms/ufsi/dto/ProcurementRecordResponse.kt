package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProcurementRecordResponse(
    val id: Long,
    val recordNumber: Int,
    val batchId: Int,
    val oblastName: String,
    val oblastId: String,
    val subProjectId: String,
    val subProjectLotId: String,
    val purchaseStatus: String,
    val tenderId: String? = null,
    val prozorroTenderId: String? = null,
    val contractorNameUkr: String? = null,
    val contractorNameEng: String? = null,
    val contractorId: String? = null,
    val contractDate: String? = null,
    val contractEndDate: String? = null,
    val contractDurationMonths: Int? = null,
    val contractAmountUah: Double? = null,
    val contractAmountEur: Double? = null,
    val financingContractDifferencePct: Double? = null
)

/** Writable procurement payload. Dates use the API format YYYY-MM-DD. */
@Serializable
data class ProcurementRecordRequest(
    val recordNumber: Int,
    val batchId: Int,
    val oblastName: String,
    val oblastId: String,
    val subProjectId: String,
    val subProjectLotId: String,
    val purchaseStatus: String,
    val tenderId: String? = null,
    val prozorroTenderId: String? = null,
    val contractorNameUkr: String? = null,
    val contractorNameEng: String? = null,
    val contractorId: String? = null,
    val contractDate: String? = null,
    val contractEndDate: String? = null,
    val contractDurationMonths: Int? = null,
    val contractAmountUah: Double? = null,
    val contractAmountEur: Double? = null,
    val financingContractDifferencePct: Double? = null
)
