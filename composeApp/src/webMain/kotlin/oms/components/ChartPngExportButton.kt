package oms.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import oms.charts.BarChartOrientation
import oms.charts.BarData
import oms.localization.LocalizationManager
import kotlin.js.JsName

@JsName("downloadOmsChartPng")
private external fun downloadOmsChartPng(payloadJson: String): Boolean

@Serializable
private data class ChartExportPayload(
    val title: String,
    val filename: String,
    val orientation: String,
    val showDataLabels: Boolean,
    val data: List<ChartExportItem>,
    val exportFailedMessage: String
)

@Serializable
private data class ChartExportItem(val label: String, val value: String)

/**
 * Exports the exact data currently rendered by a reusable OMS bar chart.
 * The browser-side renderer receives the complete list rather than the
 * scroll viewport, which keeps long charts intact in the downloaded PNG.
 */
@Composable
fun ChartPngExportButton(
    title: String,
    filenameStem: String,
    data: List<BarData>,
    orientation: BarChartOrientation,
    showDataLabels: Boolean,
    valueLabel: (Float) -> String,
    modifier: Modifier = Modifier
) {
    OmsTooltipBox(tooltip = { Text(LocalizationManager.t("download_png")) }) {
        IconButton(
            onClick = {
                val payload = ChartExportPayload(
                    title = title,
                    filename = "oms-${filenameStem.toSafeFilenameStem()}.png",
                    orientation = orientation.name,
                    showDataLabels = showDataLabels,
                    data = data.map { item -> ChartExportItem(item.label, item.formattedValue ?: valueLabel(item.value)) },
                    exportFailedMessage = LocalizationManager.t("export_failed")
                )
                downloadOmsChartPng(Json.encodeToString(payload))
            },
            enabled = data.isNotEmpty(),
            modifier = modifier
                .size(36.dp)
                .buttonHandCursor()
                .semantics { contentDescription = LocalizationManager.t("download_png") }
        ) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

private fun String.toSafeFilenameStem(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "chart" }
