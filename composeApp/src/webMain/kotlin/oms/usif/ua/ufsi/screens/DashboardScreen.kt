package oms.usif.ua.ufsi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/*
   DashboardScreen

   Головний екран системи.
   Тут розміщуються:
   - KPI картки
   - графіки
   - останні активності
*/

@Composable
fun DashboardScreen() {

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),

        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        item { KPIRow() }

        item { ChartsSection() }

        item { StatisticsSection() }

        item { ActivitySection() }
    }
}

@Composable
fun KPIRow() {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        KPICard(
            title = "Projects",
            value = "42",
            icon = Icons.Default.Folder,
            color = Color(0xFF3F51B5)
        )

        KPICard(
            title = "Inspections",
            value = "8",
            icon = Icons.Default.Search,
            color = Color(0xFF009688)
        )

        KPICard(
            title = "Reports",
            value = "126",
            icon = Icons.Default.Description,
            color = Color(0xFF673AB7)
        )

        KPICard(
            title = "Issues",
            value = "5",
            icon = Icons.Default.Warning,
            color = Color(0xFFE53935)
        )
    }
}

@Composable
fun KPICard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {

    Card(
        modifier = Modifier
            .height(120.dp)
            .fillMaxWidth(),

        shape = RoundedCornerShape(12.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),

            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column {

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Gray
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = color.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ),

                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color
                )
            }
        }
    }
}

@Composable
fun ActivitySection() {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = "Recent Activity",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            ActivityRow("Project A updated", "2 hours ago")
            ActivityRow("Inspection completed", "5 hours ago")
            ActivityRow("New report uploaded", "Yesterday")
        }
    }
}

@Composable
fun ActivityRow(
    title: String,
    time: String
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),

        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Text(title)

        Text(
            text = time,
            color = Color.Gray
        )
    }
}

@Composable
fun ChartsSection() {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        ChartCard("Projects by Region")

        ChartCard("Reports by Month")
    }
}

@Composable
fun ChartCard(
    title: String
) {

    Card(
        modifier = Modifier
            .height(320.dp)
            .fillMaxWidth(),

        shape = RoundedCornerShape(12.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(12.dp))

            HorizontalDivider()

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "Chart placeholder",
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun StatisticsSection() {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {

        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),

            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            StatItem("Total Budget", "$12.4M")
            StatItem("Completed", "18")
            StatItem("In Progress", "7")
            StatItem("Delayed", "2")
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray
        )
    }
}