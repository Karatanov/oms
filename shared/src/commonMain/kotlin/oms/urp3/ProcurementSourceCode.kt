package oms.urp3

/** Keep source identifiers intact, including lot and repeated-contract suffixes. */
fun procurementSourceCode(sourceId: String?, lotCode: String?, subprojectCode: String): String =
    sourceId?.takeIf(String::isNotBlank) ?: lotCode?.takeIf(String::isNotBlank) ?: subprojectCode

/** The source row suffix is authoritative; the translated Type cell is not. */
fun procurementSourceType(code: String): String? = code.substringBefore('$').substringAfterLast('_')
    .trim().uppercase().takeIf { it in setOf("W", "TS", "CSC") }
