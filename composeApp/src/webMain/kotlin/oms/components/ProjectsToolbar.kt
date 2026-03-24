package oms.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
   🔹 Toolbar для Projects

   🔹 Містить:
      - пошук
      - кнопки дій
*/
@Composable
fun ProjectsToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Search projects") },
            modifier = Modifier.width(300.dp),
            singleLine = true
        )

        Button(onClick = { /* TODO: create project */ }) {
            Text("New Project")
        }
    }
}