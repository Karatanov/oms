import oms.urp3.financialConstructionKey
import oms.urp3.financialSectorKey

/** Standalone checks also runnable when Gradle's local daemon cannot bind loopback. */
fun main() {
    listOf("shelter", "Укриття", "Shelters: Other public buildings", "Укриття / Shelters: Schools")
        .forEach { check(financialSectorKey(it) == "shelter") { it } }
    listOf("Security Centres", "Центри безпеки", "Security Centers / Центри безпеки")
        .forEach { check(financialSectorKey(it) == "security_centres") { it } }
    listOf("renovation", "Реконструкція", "Reconstruction/ Реконструкція")
        .forEach { check(financialConstructionKey(it) == "reconstruction") { it } }
    check(financialConstructionKey("Overhaul/ Капітальний ремонт") == "capital_repair")
    check(financialSectorKey("Водовідведення / Sewerage") == "sewerage")
    check(financialSectorKey("Water supply / Водопостачання") == "water_supply")
    val rows = listOf("Укриття" to 100.0, "Shelters: Schools" to 200.0,
        "Security Centres" to 300.0, "Центри безпеки" to 400.0)
    val totals = rows.groupBy { financialSectorKey(it.first) }.mapValues { (_, values) -> values.sumOf { it.second } }
    check(totals.size == 2)
    check(totals["shelter"] == 300.0)
    check(totals["security_centres"] == 700.0)
    check(totals.values.sum() == rows.sumOf { it.second })
    check(oms.urp3.paymentSubprojectKey("KH08_07#Lot1_W") == "KH08_07")
    check(oms.urp3.paymentSubprojectKey("KH08_07#Lot1_TS\$2") == "KH08_07")
    check(oms.urp3.paymentSubprojectKey("KH08_07") == "KH08_07")
    check(oms.urp3.excelPercentagePoints(0.765, true) == 76.5)
    check(oms.urp3.excelPercentagePoints(0.005, true) == 0.5)
    check(oms.urp3.excelPercentagePoints(76.5, false) == 76.5)
    println("Financial category checks passed; grouped totals conserved")
}
