package ca.urbanlight.imagescale.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ca.urbanlight.imagescale.data.ShareTargetConfig
import ca.urbanlight.imagescale.icons.IconIndex

/** Shown when images arrive via the generic app entry and several targets could apply. */
@Composable
fun TargetPickerDialog(
    targets: List<ShareTargetConfig>,
    onSelect: (ShareTargetConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scale with which target?") },
        text = {
            Column {
                targets.forEach { target ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(target) }
                            .padding(vertical = 12.dp),
                    ) {
                        Icon(
                            painter = painterResource(IconIndex.resFor(target.iconName)),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            target.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
