package oms.model

/*
   Модель проекту.

   У реальній системі ці дані прийдуть
   з backend API.
*/

data class Project(

    val id: String,

    val projectType: String = "project",

    val trancheNumber: Int = 1,

    val parentProjectUuid: String? = null,

    val name: String,
    val nameEn: String? = null,

    /** Beneficiary is shown in the map card when supplied by monitoring data. */
    val beneficiaryNameUk: String? = null,
    val beneficiaryNameEn: String? = null,

    /** Customer-facing project/subproject code (for example, KH08_09). */
    val siteNumber: String,

    val region: String,

    val city: String,
    val cityEn: String? = null,

    val sector: String,

    val constructionType: String = "reconstruction",

    val budgetPlanned: Long,

    /** Exact budget amount and currency selected in the project financial form. */
    val budgetDisplayAmount: String? = null,
    val budgetCurrency: String = "UAH",

    /** Project-level financial values used by the map preview. */
    val financingAmount: String? = null,
    val financingCurrency: String? = null,
    val financingDisbursedEurCents: Long = 0L,

    val contractorName: String? = null,

    val startDate: String? = null,

    /** Planned completion and contract period shown in the subproject registry. */
    val plannedEndDate: String? = null,

    /** Duration between contract signing and planned completion, supplied by the API. */
    val contractDurationDays: Long? = null,

    val status: ProjectStatus,

    // Географічні координати проєкту для відображення на карті.
    val latitude: Double,

    val longitude: Double,

    /** Archived records are retained for historical references but hidden by default. */
    val isArchived: Boolean = false
)

fun Project.localizedName(): String =
    if (oms.localization.LocalizationManager.currentLanguage == oms.localization.Language.EN) nameEn?.takeIf(String::isNotBlank) ?: name else name

fun Project.localizedCity(): String =
    if (oms.localization.LocalizationManager.currentLanguage == oms.localization.Language.EN) cityEn?.takeIf(String::isNotBlank) ?: city else city

fun Project.localizedBeneficiary(): String =
    if (oms.localization.LocalizationManager.currentLanguage == oms.localization.Language.EN)
        beneficiaryNameEn?.takeIf(String::isNotBlank) ?: beneficiaryNameUk.orEmpty()
    else beneficiaryNameUk?.takeIf(String::isNotBlank) ?: beneficiaryNameEn.orEmpty()
