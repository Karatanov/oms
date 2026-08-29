package oms.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.data.ProjectAmountDto
import oms.data.ProjectExchangeRate
import oms.localization.LocalizationManager as L
import kotlin.math.floor

data class ProjectMoneyDraft(
    val amount: String = "",
    val currency: String = "EUR",
    val converted: String = "",
    val rate: String = "",
    val date: String = "",
    val edited: Boolean = false
) {
    fun recalculate(): ProjectMoneyDraft {
        val value = amount.moneyNumber()
        val factor = rate.moneyNumber()
        return copy(converted = if (value != null && factor != null && factor > 0)
            moneyText(if (currency == "EUR") value * factor else value / factor) else "")
    }
    fun withRate(current: ProjectExchangeRate) = copy(rate = current.uahPerEur, date = current.date, edited = false).recalculate()
    fun toDto() = ProjectAmountDto(moneyText(amount.moneyNumber()!!), currency, moneyText(converted.moneyNumber()!!), rate.replace(',', '.').trimEnd('.'), date, edited)
    fun valid(): Boolean = amount.moneyNumber()?.let { it >= 0 && it < 1e13 } == true &&
        converted.moneyNumber()?.let { it >= 0 && it < 1e13 } == true &&
        rate.moneyNumber()?.let { it > 0 } == true && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
    fun legacyUah(): Long = floor((if (currency == "UAH") amount else converted).moneyNumber()!! + .5).toLong()
}

private fun String.moneyNumber() = replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)
private fun moneyText(value: Double): String {
    if (!value.isFinite() || value < 0 || value >= 1e13) return ""
    val cents = floor(value * 100 + .5).toLong()
    return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}

@Composable
fun ProjectMoneyField(label: String, value: ProjectMoneyDraft, currentRate: ProjectExchangeRate?, onChange: (ProjectMoneyDraft) -> Unit) {
    val amountInput = Regex("[0-9]{0,13}([.,][0-9]{0,2})?")
    val rateInput = Regex("[0-9]{0,8}([.,][0-9]{0,8})?")
    val counterpart = if (value.currency == "EUR") "UAH" else "EUR"
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val amountField: @Composable () -> Unit = {
                OutlinedTextField(value.amount, { if (amountInput.matches(it)) {
                    val changed = value.copy(amount = it)
                    onChange(if (currentRate != null && !value.edited) changed.withRate(currentRate) else changed.recalculate())
                } }, label = { Text(L.t("amount") + ", " + value.currency) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            val convertedField: @Composable () -> Unit = {
                OutlinedTextField(value.converted, { if (amountInput.matches(it)) onChange(value.copy(converted = it, edited = true)) },
                    label = { Text(L.t("project_money_equivalent") + ", " + counterpart) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            val currencyPicker: @Composable () -> Unit = {
                InlineOptionPicker(listOf("EUR", "UAH"), value.currency, L.t("currency"), { selected ->
                    val changed = value.copy(currency = selected, amount = value.converted.ifBlank { value.amount }, converted = value.amount)
                    onChange(if (currentRate != null && !value.edited) changed.withRate(currentRate) else changed.recalculate())
                }, { L.t("currency_${it.lowercase()}") })
            }
            val rateField: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedTextField(value.rate, { if (rateInput.matches(it)) onChange(value.copy(rate = it, date = currentIsoDate(), edited = true).recalculate()) },
                label = { Text(L.t("project_money_rate")) }, singleLine = true, modifier = modifier.fillMaxWidth())
            }
            val rateInfo: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        L.t(if (value.edited) "project_money_manual" else "project_money_nbu") + " " + value.date.toOmsDate(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f), maxLines = 2
                    )
                    OmsTooltipBox(tooltip = { Text(L.t("project_money_reset")) }) {
                        IconButton(
                            onClick = { currentRate?.let { onChange(value.withRate(it)) } },
                            enabled = currentRate != null,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = L.t("project_money_reset"), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            if (maxWidth >= 820.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { amountField() }
                    Box(Modifier.width(132.dp)) { currencyPicker() }
                    Box(Modifier.weight(1f)) { convertedField() }
                    Box(Modifier.width(156.dp)) { rateField(Modifier) }
                    Box(Modifier.width(174.dp)) { rateInfo() }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    amountField()
                    currencyPicker()
                    convertedField()
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        rateField(Modifier.weight(1f))
                        Box(Modifier.weight(1f)) { rateInfo() }
                    }
                }
            }
        }
    }
}
