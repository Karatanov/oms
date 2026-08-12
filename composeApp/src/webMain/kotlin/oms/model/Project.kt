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

    /** Customer-facing project/subproject code (for example, KH08_09). */
    val siteNumber: String,

    val region: String,

    val city: String,

    val sector: String,

    val constructionType: String = "reconstruction",

    val budgetPlanned: Long,

    val contractorName: String? = null,

    val startDate: String? = null,

    val status: ProjectStatus,

    // Географічні координати проєкту для відображення на карті.
    val latitude: Double,

    val longitude: Double
)
