package oms.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import oms.navigation.Screen

// 🔹 Один пункт sidebar
// 🔹 Підсвічується, якщо активний
@Composable
fun SidebarItem(
    title: String,
    icon: ImageVector,
    screen: Screen,
    current: Screen,
    onNavigate: (Screen) -> Unit
) {

    val isSelected = current::class == screen::class

    Button(
        onClick = { onNavigate(screen) },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(12.dp),
        colors = if (isSelected)
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else
            ButtonDefaults.outlinedButtonColors()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .background(
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            androidx.compose.ui.graphics.Color.Transparent
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title
                )

                Spacer(Modifier.width(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}