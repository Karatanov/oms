package oms.urp3.service

/** Exercises the real helper and source reader without a Gradle daemon. */
fun main(args: Array<String>) {
    fun item(code: String, paid: Double, ratio: Double? = null) =
        PaymentMonitoringWorkbookSource.Item(code, null, null, null, null, paid,
            null, null, ratio, null)
    val rows = listOf(item("VN08_01", 100.0, 10.0), item("VN08_02", 200.0),
        item("VN08_01_W", 80.0), item("VN08_01_TS", 20.0))
    check(primaryMonitoringPaidUah(rows, setOf("VN08_01", "VN08_02")) == 300.0)
    check(primaryMonitoringPaidUah(rows, setOf("VN08_01")) == 100.0)
    check(primaryMonitoringPaidUah(rows, emptySet()) == 0.0)
    if (args.isNotEmpty()) {
        val source = PaymentMonitoringWorkbookSource.parseWorkbookData(java.io.File(args[0]).readBytes())
        val first = source.items.first { it.code == "VN08_01" }
        check(kotlin.math.abs(first.plannedEibSharePct!! - 76.77569919) < 0.000001)
        check(kotlin.math.abs(first.factualEibSharePct!! - 10.73987593) < 0.000001)
        check(source.transactions.size == 93)
        println("Source percentage units and 93 payment rows verified")
    }
    println("Primary paid-total checks passed")
}
