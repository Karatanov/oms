package oms.urp3

/** Matches the register's Works class without depending on the active UI language. */
fun isWorksProcurementContract(type: String?): Boolean = type.orEmpty()
    .lowercase().split('/').any {
        it.trim() in setOf("w", "works", "роботи", "construction works", "будівельні роботи")
    }
