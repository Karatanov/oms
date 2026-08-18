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
    UkraineRegion("Вінницька область", "Vinnytsia Oblast"),
    UkraineRegion("Волинська область", "Volyn Oblast"),
    UkraineRegion("Дніпропетровська область", "Dnipropetrovsk Oblast"),
    UkraineRegion("Донецька область", "Donetsk Oblast"),
    UkraineRegion("Житомирська область", "Zhytomyr Oblast"),
    UkraineRegion("Закарпатська область", "Zakarpattia Oblast"),
    UkraineRegion("Запорізька область", "Zaporizhzhia Oblast"),
    UkraineRegion("Івано-Франківська область", "Ivano-Frankivsk Oblast"),
    UkraineRegion("Київська область", "Kyiv Oblast"),
    UkraineRegion("Кіровоградська область", "Kirovohrad Oblast"),
    UkraineRegion("Луганська область", "Luhansk Oblast"),
    UkraineRegion("Львівська область", "Lviv Oblast"),
    UkraineRegion("Миколаївська область", "Mykolaiv Oblast"),
    UkraineRegion("Одеська область", "Odesa Oblast"),
    UkraineRegion("Полтавська область", "Poltava Oblast"),
    UkraineRegion("Рівненська область", "Rivne Oblast"),
    UkraineRegion("Сумська область", "Sumy Oblast"),
    UkraineRegion("Тернопільська область", "Ternopil Oblast"),
    UkraineRegion("Харківська область", "Kharkiv Oblast"),
    UkraineRegion("Херсонська область", "Kherson Oblast"),
    UkraineRegion("Хмельницька область", "Khmelnytskyi Oblast"),
    UkraineRegion("Черкаська область", "Cherkasy Oblast"),
    UkraineRegion("Чернівецька область", "Chernivtsi Oblast"),
    UkraineRegion("Чернігівська область", "Chernihiv Oblast"),
    UkraineRegion("місто Київ", "Kyiv City")
)

private fun UkraineRegion.displayName() =
    if (LocalizationManager.currentLanguage == Language.EN) englishName else ukrainianName

/**
 * An inline, Wasm-safe autocomplete for Ukraine's fixed administrative regions.
 * It stores the Ukrainian canonical name so filters and existing project data stay consistent.
 */
@Composable
fun UkraineRegionAutocomplete(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false
) {
    val language = LocalizationManager.currentLanguage
    var query by remember { mutableStateOf(value) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(value, language) {
        query = ukraineRegions.firstOrNull { it.ukrainianName == value }?.displayName() ?: value
    }

    val matches = remember(query, language) {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) emptyList() else ukraineRegions.filter { region ->
            region.ukrainianName.lowercase().contains(needle) || region.englishName.lowercase().contains(needle)
        }
    }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { entered ->
                query = entered
                expanded = true
                onValueChange(entered)
            },
            label = { Text(if (required) "$label *" else label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (expanded && matches.isNotEmpty()) {
            Card(
                Modifier.fillMaxWidth().heightIn(max = 224.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                LazyColumn {
                    items(matches, key = { it.ukrainianName }) { region ->
                        TextButton(
                            onClick = {
                                onValueChange(region.ukrainianName)
                                query = region.displayName()
                                expanded = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(region.displayName()) }
                    }
                }
            }
        }
    }
}
