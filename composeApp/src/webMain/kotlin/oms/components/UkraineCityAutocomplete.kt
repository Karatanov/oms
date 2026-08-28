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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager

private val ukraineCities = listOf(
    "Вінниця", "Луцьк", "Дніпро", "Донецьк", "Житомир", "Ужгород", "Запоріжжя",
    "Івано-Франківськ", "Київ", "Кропивницький", "Луганськ", "Львів", "Миколаїв",
    "Одеса", "Полтава", "Рівне", "Суми", "Тернопіль", "Харків", "Херсон",
    "Хмельницький", "Черкаси", "Чернівці", "Чернігів", "Сімферополь", "Кривий Ріг", "Маріуполь"
)

@Composable
fun UkraineCityAutocomplete(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, required: Boolean = false) {
    AutocompleteField(value, onValueChange, if (required) "$label *" else label,
        ukraineCities.map { it to it }, modifier)
}
