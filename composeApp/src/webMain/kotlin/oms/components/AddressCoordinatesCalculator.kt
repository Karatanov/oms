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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
    onCoordinatesResolved: (latitude: String, longitude: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var successMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCalculating by remember { mutableStateOf(false) }
    fun calculate() {
        if (isCalculating) return
        successMessage = null
        errorMessage = null
        if (listOf(address, city, region).all { it.isBlank() }) {
            onCalculationRequestedChange(false)
            errorMessage = LocalizationManager.t("geocode_address_required")
            return
        }
        isCalculating = true
        scope.launch {
            runCatching { OmsApiClient.geocodeAddress(address, city, region) }
                .onSuccess { result ->
                    isCalculating = false
                    onCoordinatesResolved(result.latitude.toString(), result.longitude.toString())
                    successMessage = LocalizationManager.t("geocode_address_success")
                }
                .onFailure {
                    isCalculating = false
                    errorMessage = LocalizationManager.t("geocode_address_not_found")
                }
        }
    }
    Row(
        modifier = Modifier.clickable(enabled = !isCalculating) {
            if (!calculationRequested) {
                onCalculationRequestedChange(true)
                calculate()
            }
        }.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Checkbox(
            checked = calculationRequested,
            onCheckedChange = { checked ->
                if (checked) {
                    onCalculationRequestedChange(true)
                    calculate()
                } else onCalculationRequestedChange(false)
            }
        )
        Text(LocalizationManager.t(if (isCalculating) "geocode_address_loading" else "geocode_address"))
        if (isCalculating) {
            Spacer(Modifier.width(4.dp))
            CircularProgressIndicator(Modifier.width(16.dp), strokeWidth = 2.dp)
        }
    }
    Text(
        LocalizationManager.t("geocode_attribution"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
}
