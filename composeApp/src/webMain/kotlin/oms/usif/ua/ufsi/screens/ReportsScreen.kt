package oms.usif.ua.ufsi.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
   Сторінка Site Inspection Reports.

   У реальній OMS тут знаходиться:
   - список звітів
   - перегляд звіту
   - статус інспекції
*/

@Composable
fun ReportsScreen() {

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Text(
            text = "Inspection Reports",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        ReportRow("SIR-001", "Kyiv School", "Completed")

        ReportRow("SIR-002", "Lviv Hospital", "Pending")

        ReportRow("SIR-003", "Kharkiv Road", "In progress")
    }
}

/*
   Один рядок списку звітів
*/

@Composable
fun ReportRow(
    id: String,
    project: String,
    status: String
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {

            Text(id, modifier = Modifier.width(120.dp))

            Text(project, modifier = Modifier.weight(1f))

            Text(status, modifier = Modifier.width(120.dp))
        }
    }
}