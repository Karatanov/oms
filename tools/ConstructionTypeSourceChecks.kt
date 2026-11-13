package oms.urp3.service

/** Read-only check of the actual approved allocation book using the production parser. */
fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: approved-allocation.xlsx" }
    val type = Class.forName("oms.urp3.service.MonitoringWorkbookSource")
    val source = type.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
    val progress: (Int, String) -> Unit = { _, _ -> }
    val rows = type.declaredMethods.single { it.name == "parse" }.apply { isAccessible = true }
        .invoke(source, java.io.File(args.single()).readBytes(), progress) as List<*>
    fun code(row: Any) = row.javaClass.getDeclaredMethod("getSubprojectCode").apply { isAccessible = true }.invoke(row) as String
    fun construction(row: Any) = row.javaClass.getDeclaredMethod("constructionType").apply { isAccessible = true }.invoke(row) as String?
    val nonNullRows = rows.filterNotNull()
    val batchB = nonNullRows.filter { code(it).substring(2, 4) == "09" }
    check(batchB.size == 71) { "Expected 71 approved Batch 9 subprojects, got ${batchB.size}" }
    val missing = nonNullRows.filter { construction(it) == null }.map(::code)
    check(missing.isEmpty()) { "Missing explicit construction classifications: $missing" }
    check(construction(batchB.single { code(it) == "CH09_01" }) == "new_construction")
    check(construction(batchB.single { code(it) == "ZH09_01" }) == "capital_repair")
    println("PASS: ${nonNullRows.size} allocation rows classified, including all 71 Batch 9 subprojects")
}
