package oms.data

import oms.model.ProjectStatus
import oms.model.Project

/*
   Тимчасове сховище даних.

   Пізніше заміниться на API.
*/

object ProjectRepository {

    val projects = listOf(
        Project(
            id = 1,
            name = "School reconstruction",
            region = "Kyiv",
            status = ProjectStatus.ACTIVE,
            latitude = 50.4501,
            longitude = 30.5234
        ),
        Project(
            id = 2,
            name = "Hospital modernization",
            region = "Lviv",
            status = ProjectStatus.PLANNING,
            latitude = 49.8397,
            longitude = 24.0297
        ),
        Project(
            id = 3,
            name = "Road repair",
            region = "Kharkiv",
            status = ProjectStatus.COMPLETED,
            latitude = 49.9935,
            longitude = 36.2304
        ),
        Project(
            id = 4,
            name = "Bridge restoration",
            region = "Odesa",
            status = ProjectStatus.ACTIVE,
            latitude = 46.4825,
            longitude = 30.7233
        ),
        Project(
            id = 5,
            name = "Water supply upgrade",
            region = "Dnipro",
            status = ProjectStatus.PLANNING,
            latitude = 48.4647,
            longitude = 35.0462
        )
    )
}