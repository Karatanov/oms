package oms.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
   Простий горизонтальний BarChart
*/

@Composable
fun BarChart(
    data: List<BarData>,
    color: Color
) {

    if (data.isEmpty()) return

    val maxValue = data.maxOf { it.value }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        data.forEach { item ->

            val ratio = item.value / maxValue

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                /*
                 Label
                */

                Text(
                    text = item.label,
                    modifier = Modifier.width(90.dp),
                    style = MaterialTheme.typography.bodyMedium
                )

                /*
                 Bar
                */

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                ) {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .background(
                                color = color.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                }

                /*
                 Value
                */

                Text(
                    text = item.value.toInt().toString(),
                    modifier = Modifier
                        .width(40.dp)
                        .padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}