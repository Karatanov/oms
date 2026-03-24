package oms.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProjectsFilterBar(
    search: String,
    onSearchChange: (String) -> Unit,
    selectedStatus: String?,
    onStatusChange: (String?) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        /*
         Search
        */

        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            placeholder = { Text("Search projects...") },
            modifier = Modifier.width(240.dp)
        )

        /*
         Status filter
        */

        var expanded by remember { mutableStateOf(false) }

        Box {

            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedStatus ?: "Status")
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {

                DropdownMenuItem(
                    text = { Text("All") },
                    onClick = {
                        onStatusChange(null)
                        expanded = false
                    }
                )

                listOf("Active", "Completed", "Planned").forEach {

                    DropdownMenuItem(
                        text = { Text(it) },
                        onClick = {
                            onStatusChange(it)
                            expanded = false
                        }
                    )
                }
            }
        }

        /*
         Reset
        */

        TextButton(onClick = {
            onSearchChange("")
            onStatusChange(null)
        }) {
            Text("Reset")
        }
    }
}