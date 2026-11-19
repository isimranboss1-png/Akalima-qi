package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElevatedNavyCard
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.ObsidianVoid

@Composable
fun ScreenSharePrivacyDialog(
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = ElevatedNavyCard,
        icon = {
            Icon(
                imageVector = Icons.Outlined.ScreenShare,
                contentDescription = null,
                tint = CyanPulse
            )
        },
        title = {
            Text(
                text = stringResource(R.string.screen_share_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                color = InkPrimary
            )
        },
        text = {
            Text(
                text = stringResource(R.string.screen_share_dialog_body),
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPulse,
                    contentColor = ObsidianVoid
                ),
                modifier = Modifier.testTag("screen_share_continue")
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("screen_share_cancel")
            ) {
                Text("Cancel", color = InkSecondary)
            }
        }
    )
}
