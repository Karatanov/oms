package oms.usif.ua.ufsi.components

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import oms.usif.ua.ufsi.model.ProjectStatus

/*
   Відображає статус проекту
   у вигляді кольорового chip.
*/

@Composable
fun StatusChip(status: ProjectStatus) {

    val (text, color) = when (status) {

        ProjectStatus.ACTIVE ->
            "Active" to Color(0xFF2E7D32)

        ProjectStatus.PLANNING ->
            "Planning" to Color(0xFFF9A825)

        ProjectStatus.COMPLETED ->
            "Completed" to Color(0xFF1565C0)
    }

    AssistChip(

        onClick = { },

        label = {
            Text(text)
        },

        colors = AssistChipDefaults.assistChipColors(

            labelColor = Color.White,

            containerColor = color
        )
    )
}