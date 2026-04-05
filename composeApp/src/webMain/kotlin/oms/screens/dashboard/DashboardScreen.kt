package oms.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val error = MaterialTheme.colorScheme.error

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),

        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        item { KPIRow(primary, secondary, error) }

        item { ProjectsByRegionChart(primary) }

        item { StatisticsSection(primary, secondary, tertiary) }

        item { ActivitySection() }
    }
}