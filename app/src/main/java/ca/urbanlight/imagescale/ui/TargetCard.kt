package ca.urbanlight.imagescale.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ca.urbanlight.imagescale.data.ShareTargetConfig
import ca.urbanlight.imagescale.icons.IconIndex
import kotlinx.coroutines.delay

@Composable
fun TargetCard(
    target: ShareTargetConfig,
    outputFolderName: String?,
    onUpdate: (ShareTargetConfig) -> Unit,
    onPickIcon: () -> Unit,
    onPickOutputFolder: () -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
) {
    val enabled = !target.hidden

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onPickIcon, enabled = enabled) {
                    Icon(
                        painter = painterResource(IconIndex.resFor(target.iconName)),
                        contentDescription = "Share target icon: ${target.iconName}",
                        modifier = Modifier.size(24.dp),
                    )
                }

                var label by remember(target.id) { mutableStateOf(target.label) }
                LaunchedEffect(target.id, label) {
                    if (label != target.label) {
                        delay(500)
                        onUpdate(target.copy(label = label))
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(ShareTargetConfig.MAX_LABEL_LENGTH) },
                    label = { Text("Share target text") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            LabeledSlider(
                label = { "JPG quality: $it" },
                value = target.quality,
                range = ShareTargetConfig.QUALITY_RANGE,
                enabled = enabled,
                onCommit = { onUpdate(target.copy(quality = it)) },
            )

            LabeledSlider(
                label = { "Scale factor: $it%" },
                value = target.scaleFactorPercent,
                range = ShareTargetConfig.SCALE_RANGE,
                enabled = enabled,
                onCommit = { onUpdate(target.copy(scaleFactorPercent = it)) },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = target.discardMetadata,
                    onCheckedChange = { onUpdate(target.copy(discardMetadata = it)) },
                    enabled = enabled,
                )
                Text("Discard metadata", style = MaterialTheme.typography.bodyLarge)
            }

            Button(onClick = onPickOutputFolder, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(IconIndex.resFor("folder")),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(outputFolderName ?: "Choose output folder…")
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onToggleHidden) {
                    Text(if (target.hidden) "Unhide" else "Hide")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: (Int) -> String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onCommit: (Int) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Text(label(sliderValue.toInt()), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onCommit(sliderValue.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
        )
    }
}
