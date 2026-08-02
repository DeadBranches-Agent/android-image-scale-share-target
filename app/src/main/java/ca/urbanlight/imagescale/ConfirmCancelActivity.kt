package ca.urbanlight.imagescale

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import ca.urbanlight.imagescale.service.ScaleService
import ca.urbanlight.imagescale.ui.theme.ImageScaleTheme

/**
 * Confirmation shown when the user presses Stop or swipes the progress
 * notification away. The job is already held in AwaitingCancelConfirm.
 */
class ConfirmCancelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImageScaleTheme {
                var deleteConverted by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = { continueConversion() },
                    title = { Text(stringResource(R.string.cancel_confirm_title)) },
                    text = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = deleteConverted,
                                    onCheckedChange = { deleteConverted = it },
                                )
                                Text(stringResource(R.string.cancel_confirm_delete))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { continueConversion() }) {
                            Text(stringResource(R.string.cancel_confirm_continue))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { stopConversion(deleteConverted) }) {
                            Text(stringResource(R.string.cancel_confirm_stop))
                        }
                    },
                )
            }
        }
    }

    private fun continueConversion() {
        startService(serviceIntent(ScaleService.ACTION_CONTINUE))
        finish()
    }

    private fun stopConversion(deleteConverted: Boolean) {
        startService(
            serviceIntent(ScaleService.ACTION_CONFIRM_CANCEL)
                .putExtra(ScaleService.EXTRA_DELETE_OUTPUTS, deleteConverted)
        )
        finish()
    }

    private fun serviceIntent(action: String): Intent =
        Intent(this, ScaleService::class.java).setAction(action)
}
