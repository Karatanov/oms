package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.charts.BarChart
import oms.charts.BarData
import oms.components.FilterDropdown

@Composable
fun FinancialScreen(
    canAccessFinancials: Boolean = true,
    canManageFinancials: Boolean = true
) {
    if (!canAccessFinancials) {
        FinancialAccessDenied()
        return
    }

    val monthlyPayments = remember {
        listOf(
            BarData("Jan", 12f),
            BarData("Feb", 18f),
            BarData("Mar", 15f),
            BarData("Apr", 24f),
            BarData("May", 30f),
            BarData("Jun", 21f),
            BarData("Jul", 28f),
            BarData("Aug", 34f),
            BarData("Sep", 26f),
            BarData("Oct", 32f),
            BarData("Nov", 19f),
            BarData("Dec", 40f)
        )
    }

    var recordTypeFilter = remember { mutableStateOf<FinancialRecordType?>(null) }
    var dateRangeLabel = remember { mutableStateOf("All dates") }

    val records = remember { sampleFinancialRecords() }

    val filteredRecords = remember(recordTypeFilter.value, dateRangeLabel.value) {
        records
            .filter { recordTypeFilter.value == null || it.type == recordTypeFilter.value }
            .sortedByDescending { it.date }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Financial Monitoring",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )

            if (canManageFinancials) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { }) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import XLS")
                    }

                    OutlinedButton(onClick = { }) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export to Excel")
                    }

                    Button(onClick = { }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Record")
                    }
                }
            }
        }

        BudgetSummaryBar(
            spent = 84f,
            remaining = 16f
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Monthly Payments",
                    style = MaterialTheme.typography.titleMedium
                )

                BarChart(
                    data = monthlyPayments,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterDropdown(
                label = "Record Type",
                options = FinancialRecordType.entries,
                selected = recordTypeFilter.value,
                onSelect = { recordTypeFilter.value = it }
            )

            OutlinedButton(onClick = { }) {
                Text(dateRangeLabel.value)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FinancialTableHeader()

                HorizontalDivider()

                filteredRecords.forEach { record ->
                    FinancialRecordRow(
                        record = record,
                        onOpen = { },
                        onEdit = { }
                    )
                }
            }
        }
    }
}

@Composable
private fun FinancialAccessDenied() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Access restricted",
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    text = "Financial data is restricted. Contact your Project Manager.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BudgetSummaryBar(
    spent: Float,
    remaining: Float
) {
    val total = spent + remaining
    val spentPercent = if (total > 0) spent / total else 0f

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Budget summary", style = MaterialTheme.typography.titleMedium)
                Text("${spent.toInt()}% spent / ${remaining.toInt()}% remaining")
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(999.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(spentPercent)
                        .fillMaxHeight()
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(999.dp)
                        )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Spent: $spent%")
                Text("Remaining: $remaining%")
            }
        }
    }
}

@Composable
private fun FinancialTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Invoice #", modifier = Modifier.width(120.dp), style = MaterialTheme.typography.labelLarge)
        Text("Type", modifier = Modifier.width(110.dp), style = MaterialTheme.typography.labelLarge)
        Text("Date", modifier = Modifier.width(120.dp), style = MaterialTheme.typography.labelLarge)
        Text("Payment Date", modifier = Modifier.width(140.dp), style = MaterialTheme.typography.labelLarge)
        Text("Amount", modifier = Modifier.width(120.dp), style = MaterialTheme.typography.labelLarge)
        Text("Currency", modifier = Modifier.width(100.dp), style = MaterialTheme.typography.labelLarge)
        Text("Milestone", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text("Actions", modifier = Modifier.width(120.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FinancialRecordRow(
    record: FinancialRecordUi,
    onOpen: (FinancialRecordUi) -> Unit,
    onEdit: (FinancialRecordUi) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(record.invoiceNo, modifier = Modifier.width(120.dp))
        Text(record.type.label, modifier = Modifier.width(110.dp))
        Text(record.date, modifier = Modifier.width(120.dp))
        Text(record.paymentDate, modifier = Modifier.width(140.dp))
        Text(record.amount, modifier = Modifier.width(120.dp))
        Text(record.currency, modifier = Modifier.width(100.dp))
        Text(record.milestone, modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.width(120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { onOpen(record) }) { Text("View") }
            TextButton(onClick = { onEdit(record) }) { Text("Edit") }
        }
    }

    HorizontalDivider()
}

private enum class FinancialRecordType(val label: String) {
    INVOICE("Invoice"),
    ACT("Act"),
    PAYMENT("Payment"),
    ADVANCE("Advance")
}

private data class FinancialRecordUi(
    val invoiceNo: String,
    val type: FinancialRecordType,
    val date: String,
    val paymentDate: String,
    val amount: String,
    val currency: String,
    val milestone: String
)

private fun sampleFinancialRecords(): List<FinancialRecordUi> = listOf(
    FinancialRecordUi(
        "INV-1042",
        FinancialRecordType.PAYMENT,
        "2026-03-24",
        "2026-03-26",
        "12,500",
        "USD",
        "Foundation"
    ),
    FinancialRecordUi("INV-1038", FinancialRecordType.INVOICE, "2026-03-18", "2026-03-20", "8,900", "USD", "Roofing"),
    FinancialRecordUi("INV-1031", FinancialRecordType.ACT, "2026-03-10", "2026-03-12", "6,400", "USD", "Facade"),
    FinancialRecordUi(
        "INV-1027",
        FinancialRecordType.ADVANCE,
        "2026-03-02",
        "2026-03-03",
        "15,000",
        "USD",
        "Mobilization"
    )
)