package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.viewmodel.CareerPlusViewModel
import com.example.ui.theme.GoldenSun
import com.example.ui.theme.RoyalNavy

@Composable
fun SecurityCenterComponent(viewModel: CareerPlusViewModel) {
    val trg = viewModel.aiTranslationRefreshTrigger
    val t = { text: String -> 
        com.example.util.AppLocalizer.translate(text, viewModel.languageInput)
    }

    var showEraseConfirmDialog by remember { mutableStateOf(false) }

    // Observe state toast/alerts and show them
    LaunchedEffect(viewModel.showSecurityToastMessage) {
        viewModel.showSecurityToastMessage?.let { msg ->
            // Clear message once handled
            viewModel.showSecurityToastMessage = null
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Section Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Security Status Indicator",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = t("Account Security Settings"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = t("Configure secure verification and delete your account preferences below."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(vertical = 6.dp)
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            // 1. Two-Step Verification Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("Two-Step Verification (2FA)"),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = t("Requires authentication code before performing critical actions."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = viewModel.isTwoFactorEnabled,
                    onCheckedChange = { viewModel.toggleMfaSetting(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GoldenSun,
                        checkedTrackColor = RoyalNavy
                    )
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            // 2. Erase Account / Delete Account
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("Delete Account"),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = t("Permanently delete and purge all registered data and preferences from device cache."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { showEraseConfirmDialog = true },
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Delete Account icon",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(t("Delete Account"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // --- DIALOG: WIPING CONFIRMATION ---
    if (showEraseConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEraseConfirmDialog = false },
            title = {
                Text(
                    text = "⚠️ " + t("Confirm Account & Data Wipe?"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = t("Under GDPR 'Right to be Forgotten' (Article 17), this operation will fully execute a cryptographic sanitization. All your profile info, examination reminders, custom study paths, and offline credentials will be instantly purged from SQLite database caches. This cannot be undone.")
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showEraseConfirmDialog = false
                        viewModel.purgeAllUserDataPermanentlyGDPR()
                    }
                ) {
                    Text(t("Confirm Erase"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseConfirmDialog = false }) {
                    Text(t("Cancel"))
                }
            }
        )
    }
}
