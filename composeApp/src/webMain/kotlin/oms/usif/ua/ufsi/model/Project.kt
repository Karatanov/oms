package oms.usif.ua.ufsi.model

/*
   Модель проекту.

   У реальній системі ці дані прийдуть
   з backend API.
*/

data class Project(

    val id: Int,

    val name: String,

    val region: String,

    val status: ProjectStatus,

    // Географічні координати проєкту для відображення на карті.
    val latitude: Double,

    val longitude: Double
)