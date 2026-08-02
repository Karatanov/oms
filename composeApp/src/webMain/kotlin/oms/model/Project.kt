package oms.model

/*
   Модель проекту.

   У реальній системі ці дані прийдуть
   з backend API.
*/

data class Project(

    val id: String,

    val name: String,

    val region: String,

    val status: ProjectStatus,

    // Географічні координати проєкту для відображення на карті.
    val latitude: Double,

    val longitude: Double
)
