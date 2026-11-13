package oms.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.localization.Language
import oms.localization.LocalizationManager
import oms.urp3.UkraineRegionName as UkraineRegion

private val ukraineRegions = oms.urp3.ukraineRegionNames

private fun UkraineRegion.displayName() =
    if (LocalizationManager.currentLanguage == Language.EN) englishName else ukrainianName

/** One canonical Ukrainian key for filters, imported workbooks and UI labels. */
fun canonicalUkraineRegion(value: String): String = oms.urp3.canonicalUkraineRegion(value)

fun localizedUkraineRegion(value: String): String {
    val knownRegion = ukraineRegions.firstOrNull { it.ukrainianName == canonicalUkraineRegion(value) }
    return knownRegion?.displayName()
        ?: if (LocalizationManager.currentLanguage == Language.EN) {
            value.replace(Regex("\\boblast\\b", RegexOption.IGNORE_CASE), "Region")
        } else value
}

/**
 * An inline, Wasm-safe autocomplete for Ukraine's fixed administrative regions.
 * It stores the Ukrainian canonical name so filters and existing project data stay consistent.
 */
@Composable
fun UkraineRegionAutocomplete(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, required: Boolean = false) {
    AutocompleteField(value, onValueChange, if (required) "$label *" else label,
        ukraineRegions.map { it.ukrainianName to it.displayName() }, modifier)
}
