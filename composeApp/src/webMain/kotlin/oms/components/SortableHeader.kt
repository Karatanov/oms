package oms.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.screens.SortColumn

@Composable
fun SortableHeader(
    title: String,
    column: SortColumn,
    currentSort: SortColumn,
    ascending: Boolean,
    onSort: (SortColumn) -> Unit,
    modifier: Modifier
) {

    TextButton(
        onClick = { onSort(column) },
        modifier = modifier
    ) {

        Row {

            Text(title)

            if (currentSort == column) {

                Spacer(Modifier.width(4.dp))

                Icon(
                    imageVector =
                        if (ascending)
                            Icons.Default.ArrowUpward
                        else
                            Icons.Default.ArrowDownward,
                    contentDescription = null
                )
            }
        }
    }
}