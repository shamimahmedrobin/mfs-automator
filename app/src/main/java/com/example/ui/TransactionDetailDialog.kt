package com.example.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.PaymentTransaction
import com.example.formatTxDateTime
import com.example.DirectionBadge

/**
 * Full transaction details — opened by tapping a transaction card.
 * Nothing here is truncated: suspicion reason, error log and the raw
 * SMS body are all shown in full.
 */
@Composable
fun TransactionDetailDialog(
    transaction: PaymentTransaction,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val isOut = transaction.direction == "out"

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = "${transaction.mfsName} • ৳ ${transaction.amount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                DirectionBadge(isOut = isOut)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailRow(
                    label = "Type",
                    value = if (isOut) "Money OUT — left your wallet" else "Money IN — payment received"
                )
                DetailRow(
                    label = "TrxID",
                    value = transaction.trxId,
                    mono = true,
                    actionLabel = "Copy",
                    onAction = { clipboard.setText(AnnotatedString(transaction.trxId)) }
                )
                DetailRow(
                    label = if (isOut) "To" else "From",
                    value = transaction.senderNumber
                )
                DetailRow(label = "Date & time", value = formatTxDateTime(transaction.timestamp))
                if (isOut && !transaction.fee.isNullOrBlank()) {
                    DetailRow(label = "Fee", value = "৳ ${transaction.fee}")
                }
                if (!transaction.currentBalance.isNullOrBlank()) {
                    DetailRow(label = "Balance (from SMS)", value = "৳ ${transaction.currentBalance}")
                }
                DetailRow(
                    label = "Server sync",
                    value = if (transaction.isSynced) "✓ Synced to API" else "✗ Not synced yet"
                )
                if (!transaction.isSynced && !transaction.errorLog.isNullOrBlank()) {
                    DetailRow(label = "Sync error", value = transaction.errorLog!!)
                }
                if (!transaction.deviceId.isNullOrBlank()) {
                    DetailRow(label = "Device", value = transaction.deviceId!!)
                }

                if (transaction.isSuspicious) {
                    HorizontalDivider()
                    Text(
                        text = "⚠️ Suspicious — needs review",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = com.example.ui.theme.WarningOrange
                    )
                    if (!transaction.suspicionReason.isNullOrBlank()) {
                        Text(
                            text = transaction.suspicionReason!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()
                Text(
                    text = "Full SMS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = transaction.body,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                )
            }
        }
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    mono: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else null
        )
    }
}
