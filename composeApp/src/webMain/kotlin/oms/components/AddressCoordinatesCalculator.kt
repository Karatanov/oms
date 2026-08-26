package oms.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oms.data.OmsApiClient
import oms.localization.LocalizationManager

@Composable
fun AddressCoordinatesCalculator(
    address: String,
    city: String,
    region: String,
    calculationRequested: Boolean,
    onCalculationRequestedChange: (Boolean) -> Unit,
    onCoordinatesResolved: (latitude: String, longitude: String) -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var successMessage by remember { mutableStateOf<String?>(null) }
    fun calculate() {
        if (calculationRequested) return
        successMessage = null
        if (listOf(address, city, region).all { it.isBlank() }) {
            onError(LocalizationManager.t("geocode_address_required"))
            return
        }
        onCalculationRequestedChange(true)
        scope.launch {
            runCatching { OmsApiClient.geocodeAddress(address, city, region) }
                .onSuccess { result ->
                    onCalculationRequestedChange(false)
                    onCoordinatesResolved(result.latitude.toString(), result.longitude.toString())
                    successMessage = LocalizationManager.t("geocode_address_success")
                }
                .onFailure {
                    onCalculationRequestedChange(false)
                    onError(LocalizationManager.t("geocode_address_not_found"))
                }
        }
    }
    Row(
        modifier = Modifier.clickable(enabled = !calculationRequested) { calculate() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Checkbox(
            checked = calculationRequested,
            onCheckedChange = { checked ->
                if (checked) calculate() else onCalculationRequestedChange(false)
            }
        )
        Text(LocalizationManager.t(if (calculationRequested) "geocode_address_loading" else "geocode_address"))
        if (calculationRequested) {
            Spacer(Modifier.width(4.dp))
            CircularProgressIndicator(Modifier.width(16.dp), strokeWidth = 2.dp)
        }
    }
    Text(
        LocalizationManager.t("geocode_attribution"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
}
