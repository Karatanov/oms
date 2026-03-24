package oms.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Pagination() {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {

        Button(onClick = { }) { Text("<") }

        Spacer(Modifier.width(8.dp))

        Button(onClick = { }) { Text("1") }

        Spacer(Modifier.width(8.dp))

        Button(onClick = { }) { Text("2") }

        Spacer(Modifier.width(8.dp))

        Button(onClick = { }) { Text("3") }

        Spacer(Modifier.width(8.dp))

        Button(onClick = { }) { Text(">") }
    }
}