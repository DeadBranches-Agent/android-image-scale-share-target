package ca.urbanlight.imagescale.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.urbanlight.imagescale.R
import ca.urbanlight.imagescale.data.AppConfig
import ca.urbanlight.imagescale.data.ShareTargetConfig
import ca.urbanlight.imagescale.icons.IconIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetListScreen(
    config: AppConfig,
    loggingActive: Boolean,
    outputFolderName: (ShareTargetConfig) -> String?,
    onAddTarget: () -> Unit,
    onUpdate: (ShareTargetConfig) -> Unit,
    onPickIcon: (ShareTargetConfig) -> Unit,
    onPickOutputFolder: (ShareTargetConfig) -> Unit,
    onToggleHidden: (ShareTargetConfig) -> Unit,
    onDelete: (ShareTargetConfig) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onToggleLogging: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        bottomBar = {
            BottomAppBar {
                TextButton(onClick = onExport) { Text("Export settings") }
                TextButton(onClick = onImport) { Text("Import settings") }
                TextButton(onClick = onToggleLogging) {
                    Text(if (loggingActive) "Stop Logging" else "Start Logging")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(config.targets, key = { it.id }) { target ->
                TargetCard(
                    target = target,
                    outputFolderName = outputFolderName(target),
                    onUpdate = onUpdate,
                    onPickIcon = { onPickIcon(target) },
                    onPickOutputFolder = { onPickOutputFolder(target) },
                    onToggleHidden = { onToggleHidden(target) },
                    onDelete = { onDelete(target) },
                )
            }

            // The plus button is always the last item.
            item(key = "add-button") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    FilledTonalIconButton(onClick = onAddTarget) {
                        Icon(
                            painter = painterResource(IconIndex.resFor("plus")),
                            contentDescription = "Add share target",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    if (config.targets.isEmpty()) {
                        Text(
                            stringResource(R.string.add_first_target),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
