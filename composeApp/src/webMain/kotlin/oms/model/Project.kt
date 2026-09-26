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

    val contractorName: String? = null,

    val startDate: String? = null,

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
