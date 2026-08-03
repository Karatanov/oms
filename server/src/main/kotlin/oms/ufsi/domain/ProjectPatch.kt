package oms.ufsi.domain

/** Fields of a project that may be changed without changing its lifecycle status. */
data class ProjectPatch(
    val name: String,
    val siteName: String,
    val siteNumber: String,
    val address: String,
    val region: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val sector: String,
    val constructionType: String,
    val budgetPlanned: Long,
    val engineerConsultantContractAmount: Long?,
    val technicalSupervisionAmount: Long?
)
