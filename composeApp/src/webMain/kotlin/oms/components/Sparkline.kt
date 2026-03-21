package oms.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/*
   Sparkline — маленький графік тренду для KPI карток.

   Параметр data — список чисел (наприклад значення за тиждень).
*/

@Composable
fun Sparkline(
    data: List<Float>,
    color: Color
) {

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {

        if (data.size < 2) return@Canvas

        val max = data.max()
        val min = data.min()
        val range = max - min

        val stepX = size.width / (data.size - 1)

        val linePath = Path()
        val fillPath = Path()

        data.forEachIndexed { index, value ->

            val x = index * stepX

            val normalized =
                if (range == 0f) 0.5f
                else (value - min) / range

            val y = size.height - normalized * size.height

            if (index == 0) {

                linePath.moveTo(x, y)

                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)

            } else {

                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            if (index == data.lastIndex) {

                fillPath.lineTo(x, size.height)
                fillPath.close()
            }
        }

        /*
           Малюємо заливку
        */

        drawPath(
            path = fillPath,
            color = color.copy(alpha = 0.15f)
        )

        /*
           Малюємо лінію
        */

        drawPath(
            path = linePath,
            color = color,
            style = Stroke(width = 3f)
        )
    }
}