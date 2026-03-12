package oms.usif.ua.ufsi.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import oms.usif.ua.ufsi.model.Project
import kotlin.js.JsName

@JsName("showLeafletMapPane")
external fun showLeafletMapPane()

@JsName("hideLeafletMapPane")
external fun hideLeafletMapPane()

@JsName("setLeafletProjects")
external fun setLeafletProjects(projectsJson: String)

@Composable
fun LeafletMapView(
    projects: List<Project>
) {
    DisposableEffect(projects) {
        // Показуємо панель карти.
        showLeafletMapPane()

        // Передаємо список проєктів у JavaScript.
        setLeafletProjects(projects.toLeafletJson())

        onDispose {
            // При виході з екрану ховаємо карту.
            hideLeafletMapPane()
        }
    }
}

/*
   Перетворює список проєктів у JSON-рядок,
   який далі обробляє JavaScript-код Leaflet.
*/
private fun List<Project>.toLeafletJson(): String {
    return buildString {
        append("[")

        this@toLeafletJson.forEachIndexed { index, project ->
            if (index > 0) append(",")

            append(
                """
                {
                  "id": ${project.id},
                  "name": "${project.name.escapeJson()}",
                  "region": "${project.region.escapeJson()}",
                  "status": "${project.status.name}",
                  "latitude": ${project.latitude},
                  "longitude": ${project.longitude}
                }
                """.trimIndent()
            )
        }

        append("]")
    }
}

/*
   Мінімальне екранування спецсимволів для JSON.
*/
private fun String.escapeJson(): String {
    return this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}