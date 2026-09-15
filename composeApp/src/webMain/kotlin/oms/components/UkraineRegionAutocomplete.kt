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

private data class UkraineRegion(val ukrainianName: String, val englishName: String)

private val ukraineRegions = listOf(
    UkraineRegion("Вінницька область", "Vinnytsia Region"),
    UkraineRegion("Волинська область", "Volyn Region"),
    UkraineRegion("Дніпропетровська область", "Dnipropetrovsk Region"),
    UkraineRegion("Донецька область", "Donetsk Region"),
    UkraineRegion("Житомирська область", "Zhytomyr Region"),
    UkraineRegion("Закарпатська область", "Zakarpattia Region"),
    UkraineRegion("Запорізька область", "Zaporizhzhia Region"),
    UkraineRegion("Івано-Франківська область", "Ivano-Frankivsk Region"),
    UkraineRegion("Київська область", "Kyiv Region"),
    UkraineRegion("Кіровоградська область", "Kirovohrad Region"),
    UkraineRegion("Луганська область", "Luhansk Region"),
    UkraineRegion("Львівська область", "Lviv Region"),
    UkraineRegion("Миколаївська область", "Mykolaiv Region"),
    UkraineRegion("Одеська область", "Odesa Region"),
    UkraineRegion("Полтавська область", "Poltava Region"),
    UkraineRegion("Рівненська область", "Rivne Region"),
    UkraineRegion("Сумська область", "Sumy Region"),
    UkraineRegion("Тернопільська область", "Ternopil Region"),
    UkraineRegion("Харківська область", "Kharkiv Region"),
    UkraineRegion("Херсонська область", "Kherson Region"),
    UkraineRegion("Хмельницька область", "Khmelnytskyi Region"),
    UkraineRegion("Черкаська область", "Cherkasy Region"),
    UkraineRegion("Чернівецька область", "Chernivtsi Region"),
    UkraineRegion("Чернігівська область", "Chernihiv Region"),
    UkraineRegion("місто Київ", "Kyiv City")
)

private fun UkraineRegion.displayName() =
    if (LocalizationManager.currentLanguage == Language.EN) englishName else ukrainianName

private fun normalizedRegionName(value: String): String = value
    .lowercase()
    .replace(Regex("[.,]"), "")
    .replace(Regex("\\s+(область|обл|oblast|region)$"), "")
    .replace(Regex("\\s+"), " ")
    .trim()

/** One canonical Ukrainian key for filters, imported workbooks and UI labels. */
fun canonicalUkraineRegion(value: String): String {
    val normalized = normalizedRegionName(value)
    return ukraineRegions.firstOrNull {
        normalizedRegionName(it.ukrainianName) == normalized || normalizedRegionName(it.englishName) == normalized
    }?.ukrainianName ?: value.replace(Regex("\\s+"), " ").trim()
}

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
