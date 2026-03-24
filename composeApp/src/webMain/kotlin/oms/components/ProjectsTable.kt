package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import oms.model.Project
import oms.model.ProjectStatus

/*
   🔹 Таблиця проєктів

   🔹 Реалізована через LazyColumn (аналог data grid)
*/
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProjectsTable(
    projects: List<Project>
) {

    Column {

        /*
         Header
        */

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {

            HeaderCell("ID", 60.dp)
            HeaderCell("Name", 160.dp)
            HeaderCell("Status", 120.dp)
            HeaderCell("Region", 120.dp)
            HeaderCell("Lat", 100.dp)
            HeaderCell("Lng", 100.dp)
            HeaderCell("Actions", 120.dp)
        }

        /*
         Rows
        */

        projects.forEach { project ->

            var hovered by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (hovered)
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else
                            Color.Transparent
                    )
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Cell(project.id.toString(), 60.dp)
                Cell(project.name, 160.dp)
                StatusCell(project.status)
                Cell(project.region, 120.dp)
                Cell(project.latitude.toString(), 100.dp)
                Cell(project.longitude.toString(), 100.dp)

                /*
                 Actions
                */

                Row(
                    modifier = Modifier.width(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    TextButton(onClick = { }) {
                        Text("View")
                    }

                    TextButton(onClick = { }) {
                        Text("Edit")
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderCell(text: String, width: Dp) {
    Text(
        text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.labelLarge
    )
}

@Composable
fun Cell(text: String, width: Dp) {
    Text(
        text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
fun StatusCell(status: ProjectStatus) {

    val color = when (status) {
        ProjectStatus.ACTIVE -> Color(0xFF2E7D32)
        ProjectStatus.PLANNING -> Color(0xFFF9A825)
        ProjectStatus.COMPLETED -> Color(0xFF1565C0)
    }

    val label = when (status) {
        ProjectStatus.ACTIVE -> "Active"
        ProjectStatus.PLANNING -> "Planned"
        ProjectStatus.COMPLETED -> "Completed"
    }

    Box(
        modifier = Modifier
            .width(120.dp)
            .background(
                color.copy(alpha = 0.15f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = color)
    }
}