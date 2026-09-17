package oms.screens.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import oms.data.ApiInspectionPhoto
import oms.data.omsApiUrl
import kotlin.js.JsName
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.components.NativePaneAnchor

@JsName("showDashboardPhotoSlider")
external fun showDashboardPhotoSlider(reportUuid: String?, inspectionDate: String?, subprojectCode: String?, photosJson: String)

@JsName("hideDashboardPhotoSlider")
external fun hideDashboardPhotoSlider()

@JsName("setDashboardInspectionPreviewHandler")
private external fun setDashboardInspectionPreviewHandler(handler: (String) -> Unit)

/** Browser-native image element is used here so remote thumbnails work in both JS and Wasm builds. */
@Composable
fun DashboardPhotoSlider(
    reportUuid: String?,
    inspectionDate: String?,
    subprojectCode: String?,
    photos: List<ApiInspectionPhoto>?,
    onOpenReport: (String) -> Unit = {}
) {
    NativePaneAnchor("dashboard-photo-slider", Modifier.fillMaxWidth().height(232.dp))
    DisposableEffect(reportUuid, inspectionDate, subprojectCode, photos, oms.localization.LocalizationManager.currentLanguage) {
        val sliderPhotos = photos.orEmpty()
            .sortedByDescending { it.isMain }
            .take(7)
            .map { photo ->
                photo.copy(
                    downloadUrl = photo.downloadUrl.toOmsUrl(),
                    thumbnailUrl = photo.thumbnailUrl.toOmsUrl()
                )
            }
        setDashboardInspectionPreviewHandler(onOpenReport)
        showDashboardPhotoSlider(reportUuid, inspectionDate, subprojectCode, Json.encodeToString(sliderPhotos))
        onDispose {
            setDashboardInspectionPreviewHandler { _ -> }
            hideDashboardPhotoSlider()
        }
    }
}

private fun String.toOmsUrl(): String =
    if (startsWith("http://") || startsWith("https://")) this else omsApiUrl(this)
