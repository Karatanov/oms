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
external fun showDashboardPhotoSlider(inspectionDate: String?, inspectionCode: String?, photosJson: String)

@JsName("hideDashboardPhotoSlider")
external fun hideDashboardPhotoSlider()

/** Browser-native image element is used here so remote thumbnails work in both JS and Wasm builds. */
@Composable
fun DashboardPhotoSlider(inspectionDate: String?, inspectionCode: String?, photos: List<ApiInspectionPhoto>?) {
    NativePaneAnchor("dashboard-photo-slider", Modifier.fillMaxWidth().height(232.dp))
    DisposableEffect(inspectionDate, inspectionCode, photos, oms.localization.LocalizationManager.currentLanguage) {
        val sliderPhotos = photos.orEmpty()
            .sortedByDescending { it.isMain }
            .take(7)
            .map { photo ->
                photo.copy(
                    downloadUrl = photo.downloadUrl.toOmsUrl(),
                    thumbnailUrl = photo.thumbnailUrl.toOmsUrl()
                )
            }
        showDashboardPhotoSlider(inspectionDate, inspectionCode, Json.encodeToString(sliderPhotos))
        onDispose(::hideDashboardPhotoSlider)
    }
}

private fun String.toOmsUrl(): String =
    if (startsWith("http://") || startsWith("https://")) this else omsApiUrl(this)
