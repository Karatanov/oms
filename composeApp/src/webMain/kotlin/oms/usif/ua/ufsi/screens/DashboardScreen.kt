package oms.usif.ua.ufsi.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import oms.usif.ua.ufsi.charts.BarChart
import oms.usif.ua.ufsi.charts.BarData
import oms.usif.ua.ufsi.components.Sparkline

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

        item { ProjectsByRegionChart() }

        item { StatisticsSection() }

        item { ActivitySection() }
    }
}

@Composable
fun ProjectsByRegionChart() {

    val data = listOf(

        BarData("Kyiv", 18f),
        BarData("Lviv", 12f),
        BarData("Odesa", 9f),
        BarData("Dnipro", 7f),
        BarData("Kharkiv", 4f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),

        shape = RoundedCornerShape(12.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = "Projects by Region",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(16.dp))

            BarChart(
                data = data,
                color = Color(0xFF3F51B5)
            )
        }
    }
}

@Composable
fun KPIRow() {
    KPICard(
        title = "Projects",
        value = "42",
        icon = Icons.Default.Folder,
        color = Color(0xFF3F51B5),
        trend = listOf(10f, 12f, 14f, 18f, 22f, 30f, 42f)
    )

    KPICard(
        title = "Reports",
        value = "126",
        icon = Icons.Default.Description,
        color = Color(0xFF673AB7),
        trend = listOf(40f, 48f, 60f, 75f, 92f, 110f, 126f)
    )

    KPICard(
        title = "Issues",
        value = "5",
        icon = Icons.Default.Warning,
        color = Color(0xFFE53935),
        trend = listOf(12f, 11f, 9f, 10f, 8f, 6f, 5f)
    )
}

/*
   Визначає напрям тренду.
   true  → значення зростає,
   false → значення падає
*/
fun trendDirection(data: List<Float>): Boolean {
    return data.last() >= data.first()
}

@Composable
fun KPICard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    trend: List<Float>
) {
    val positive = trendDirection(trend)
    Card(
        modifier = Modifier
            .height(140.dp)
            .fillMaxWidth(),

        shape = RoundedCornerShape(12.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {

                Column {

                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Gray
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {

                        Text(
                            text = value,
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Spacer(Modifier.width(6.dp))

                        Icon(
                            imageVector =
                                if (positive)
                                    Icons.Default.ArrowUpward
                                else
                                    Icons.Default.ArrowDownward,

                            contentDescription = null,

                            tint =
                                if (positive)
                                    Color(0xFF2E7D32)
                                else
                                    Color(0xFFC62828),

                            modifier = Modifier.size(18.dp)
                        )
                    }
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
                        icon,
                        contentDescription = null,
                        tint = color
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            /*
               Sparkline тренду
            */

            Sparkline(
                data = trend,
                color = color
            )
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