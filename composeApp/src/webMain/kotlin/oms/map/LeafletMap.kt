package oms.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import oms.localization.LocalizationManager
import oms.model.Project
import oms.model.ProjectStatus
import kotlin.js.JsName

@JsName("showLeafletMapPane")
external fun showLeafletMapPane()

@JsName("hideLeafletMapPane")
external fun hideLeafletMapPane()

@JsName("setLeafletProjects")
external fun setLeafletProjects(projectsJson: String)

@JsName("setLeafletProjectClickHandler")
external fun setLeafletProjectClickHandler(handler: (String) -> Unit)

@Composable
fun LeafletMapView(
    projects: List<Project>,
    onProjectClick: (String) -> Unit = {}
) {
    DisposableEffect(projects) {
        showLeafletMapPane()
        setLeafletProjectClickHandler(onProjectClick)
        setLeafletProjects(projects.toLeafletJson())

        onDispose {
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

            val (statusText, statusColorHex) = project.status.toMapPresentation()

            append(
                """
                {
                  "id": "${project.id.escapeJson()}",
                  "name": "${project.name.escapeJson()}",
                  "region": "${project.region.escapeJson()}",
                  "regionLabel": "${LocalizationManager.t("region").escapeJson()}",
                  "status": "${project.status.name}",
                  "statusText": "${statusText.escapeJson()}",
                  "statusLabel": "${LocalizationManager.t("status").escapeJson()}",
                  "statusColor": "$statusColorHex",
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
   Презентаційна модель статусу для карти.
   Тут ми використовуємо ті самі кольори, що і в таблиці.
*/
private fun ProjectStatus.toMapPresentation(): Pair<String, String> {
    return when (this) {
        ProjectStatus.ACTIVE ->
            LocalizationManager.t("active") to "#2E7D32"

        ProjectStatus.PLANNING ->
            LocalizationManager.t("planning") to "#F9A825"

        ProjectStatus.COMPLETED ->
            LocalizationManager.t("completed") to "#1565C0"
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
