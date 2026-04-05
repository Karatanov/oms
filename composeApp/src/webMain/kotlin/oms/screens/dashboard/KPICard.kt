package oms.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import oms.components.Sparkline


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

/*
   Визначає напрям тренду.
   true  → значення зростає,
   false → значення падає
*/
fun trendDirection(data: List<Float>): Boolean {
    return data.last() >= data.first()
}