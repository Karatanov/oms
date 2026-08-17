package oms.ufsi.domain

import java.time.LocalDate

data class ProcurementRecord(
    val id: Long,
    val recordNumber: Int,
    val batchId: Int,
    val oblastName: String,
    val oblastId: String,
    val subProjectId: String,
    val subProjectLotId: String,
    val purchaseStatus: String,
    val tenderId: String?,
    val prozorroTenderId: String?,
    val contractorNameUkr: String?,
    val contractorNameEng: String?,
    val contractorId: String?,
    val contractDate: LocalDate?,
    val contractEndDate: LocalDate?,
    val contractDurationMonths: Int?,
    val contractAmountUah: Double?,
    val contractAmountEur: Double?,
    val financingContractDifferencePct: Double?
)
