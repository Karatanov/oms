package oms.urp3.service

import java.time.LocalDate
import kotlin.math.abs

fun main(args: Array<String>) {
    require(args.size in 1..2) { "Usage: payments.xlsx [monitoring.xlsx]" }
    val transactions = PaymentMonitoringWorkbookSource.parseWorkbookData(java.io.File(args[0]).readBytes()).transactions
    val ids = transactions.map { it.code.paymentMonitoringSubprojectCode().uppercase() }.distinct()
        .mapIndexed { index, code -> code to index.toLong() + 1 }.toMap()
    fun noLegacy(): Map<Long, Double> = error("Unexpected legacy read")
    val totals = financialProgressPayments(transactions, ids, null, ::noLegacy)
    check(abs(totals.values.sum() - 307_988_578.28) < 0.001)
    check(financialProgressPayments(emptyList(), ids, null, ::noLegacy).isEmpty())
    check(financialProgressPayments(transactions, ids, 2000, ::noLegacy).isEmpty())
    check(financialProgressPayments(transactions, emptyMap(), null, ::noLegacy).isEmpty())
    check(financialProgressPayments(null, ids, null) { mapOf(1L to 12.5) } == mapOf(1L to 12.5))
    val lots = listOf("KH08_07#Lot1_W" to 10.25, "KH08_07#Lot2_W" to 20.5, "KH08_07_TS" to 300.0,
        "KH08_07_CSC" to 400.0).map { (code, value) ->
        PaymentMonitoringWorkbookSource.Transaction(code, code, 8, LocalDate.of(2026, 2, 1), value, null, "test")
    }
    check(financialProgressPayments(lots, mapOf("KH08_07" to 1L), 2026, ::noLegacy) == mapOf(1L to 30.75))
    val worksContracts = listOf(2_867_194.45, 2_896_685.69, 2_929_089.77, null, null, 2_905_436.22)
    check(abs(financialProgressSourceWorks(null, worksContracts)!! - 11_598_406.13) < 0.001)
    check(financialProgressSourceWorks(12_000_000.0, worksContracts) == 12_000_000.0)
    check(financialProgressSourceWorks(null, listOf(null, 0.0, Double.NaN, -1.0)) == null)
    if (args.size == 2) {
        java.io.FileInputStream(args[1]).use { input ->
            org.apache.poi.ss.usermodel.WorkbookFactory.create(input).use { workbook ->
                val sheet = workbook.getSheet("DB Table")
                val header = sheet.getRow(11)
                val codeColumn = (0 until header.lastCellNum).single {
                    header.getCell(it)?.toString()?.trim() == "Sub-Project ID"
                }
                val lotAmounts = (15..sheet.lastRowNum).mapNotNull { sheet.getRow(it) }
                    .filter { it.getCell(codeColumn)?.toString()?.trim() == "KH08_07" }
                    .map { row ->
                        row.getCell(53)?.takeIf { cell ->
                            cell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC ||
                                (cell.cellType == org.apache.poi.ss.usermodel.CellType.FORMULA &&
                                    cell.cachedFormulaResultType == org.apache.poi.ss.usermodel.CellType.NUMERIC)
                        }?.numericCellValue
                    }
                check(lotAmounts.size == 6)
                check(abs(financialProgressSourceWorks(null, lotAmounts)!! - 11_598_406.13) < 0.001)
                println("KH08_07: all six source lot rows reconciled to 11598406.13 UAH of available Works contracts")
            }
        }
    }
    println("Works payments reconciled: 307988578.28 UAH; empty snapshot, year and scope cannot revive legacy records; lot/service checks passed")
}
