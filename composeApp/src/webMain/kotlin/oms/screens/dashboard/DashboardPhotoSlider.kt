package oms.screens.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import oms.data.ApiInspectionPhoto
import kotlin.js.JsName

@JsName("showDashboardPhotoSlider")
external fun showDashboardPhotoSlider(inspectionDate: String?, photosJson: String)

@JsName("hideDashboardPhotoSlider")
external fun hideDashboardPhotoSlider()

/** Browser-native image element is used here so remote thumbnails work in both JS and Wasm builds. */
@Composable
fun DashboardPhotoSlider(inspectionDate: String?, photos: List<ApiInspectionPhoto>?) {
    DisposableEffect(inspectionDate, photos) {
        val sliderPhotos = photos.orEmpty()
            .sortedByDescending { it.isMain }
            .take(7)
        showDashboardPhotoSlider(inspectionDate, Json.encodeToString(sliderPhotos))
        onDispose(::hideDashboardPhotoSlider)
    }
}
