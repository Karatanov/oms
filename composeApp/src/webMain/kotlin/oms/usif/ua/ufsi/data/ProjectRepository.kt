package oms.usif.ua.ufsi.data

import oms.usif.ua.ufsi.model.Project
import oms.usif.ua.ufsi.model.ProjectStatus

/*
   Тимчасове сховище даних.

   Пізніше заміниться на API.
*/

object ProjectRepository {

    val projects = listOf(
        Project(1, "School reconstruction", "Kyiv", ProjectStatus.ACTIVE),
        Project(2, "Hospital modernization", "Lviv", ProjectStatus.PLANNING),
        Project(3, "Road repair", "Kharkiv", ProjectStatus.COMPLETED),
        Project(4, "Bridge restoration", "Odesa", ProjectStatus.ACTIVE),
        Project(5, "Water supply upgrade", "Dnipro", ProjectStatus.PLANNING)
    )
}