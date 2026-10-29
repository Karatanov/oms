package oms.urp3

/** Values match the existing tracker validation lists, regardless of UI language. */
object FeedbackOptions {
    const val maxDescriptionLength = 5000
    val types = listOf("Correction", "Improvement")
    val pages = listOf("Dashboard", "Projects", "Map", "Inspection Reports / SIR",
        "Financial Monitoring", "Documents", "Administration", "Authentication / Users",
        "Database", "General / System", "Procurement", "SIR")
}
