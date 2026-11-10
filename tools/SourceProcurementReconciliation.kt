package oms.urp3.service

import oms.urp3.isWorksProcurementContract
import kotlin.math.roundToLong

/** Read-only check against the 10 November approved workbook; never writes registry data. */
fun main(args: Array<String>) {
    val rows = ProcurementWorkbookSource.parse(java.io.File(args.single()).readBytes())
    val signed = rows.filter { it.purchaseStatus.orEmpty().contains("Contract signed", true) }
    val works = signed.filter { isWorksProcurementContract(it.sourceContractType) }
    val procedures = signed.distinctBy { it.procurementId ?: it.sourceId }
    check(rows.size == 139)
    check(signed.size == 129)
    check(procedures.size == 124)
    check(signed.sumOf { ((it.contractAmountUah ?: 0.0) * 100).roundToLong() } == 285_885_311_911L)
    check(procedures.sumOf { ((it.signedContractAmountUah ?: 0.0) * 100).roundToLong() } == 285_694_760_040L)
    check(works.size == 61)
    check(works.all { it.contractDate != null })
    check(!isWorksProcurementContract("Технічний нагляд / TS"))
    check(!isWorksProcurementContract("Інженер-консультант / CSC"))
    listOf("W", " Works ", "Роботи / Works", "Works / Роботи", "Будівельні роботи")
        .forEach { check(isWorksProcurementContract(it)) }
    check(!isWorksProcurementContract(null) && !isWorksProcurementContract(""))
    println("139 rows; 129 signed-status rows; 124 procedures; ${works.size} Works rows with dates. " +
        "Contract and tracker totals reconciled separately; service rows excluded from Works count.")
}
