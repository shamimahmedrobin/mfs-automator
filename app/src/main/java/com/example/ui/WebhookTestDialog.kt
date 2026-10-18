package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.network.WebhookTestResult

/**
 * Shows the server's test-mode diagnostic as a friendly checklist:
 *  ✅ Auth OK / ❌ Auth failed
 *  ✅ Amount parsed / ❌ parse failed
 *  ✅ Would match order #X / ⚠️ No match / ❓ Ambiguous
 */
@Composable
fun WebhookTestResultDialog(
    result: WebhookTestResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Webhook Test Result", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 1. Auth
                if (result.errorMessage != null && !result.authOk) {
                    DiagRow(
                        ok = false,
                        label = "Auth failed",
                        detail = result.errorMessage
                    )
                } else {
                    DiagRow(
                        ok = result.authOk,
                        label = if (result.authOk) "Auth OK" else "Auth failed",
                        detail = if (result.authOk) "Server accepted the webhook secret"
                        else (result.errorMessage ?: "Unknown auth error")
                    )
                }

                // 2. Amount / parse
                val amountText = result.parsedAmount?.let { "৳${it.toLong()}" } ?: "—"
                DiagRow(
                    ok = result.amountParsed,
                    label = if (result.amountParsed) "Amount parsed ($amountText)" else "Amount parse failed",
                    detail = buildString {
                        append("Provider: ${result.parsedProvider ?: "—"}")
                        append("  •  TrxID: ${result.parsedTrxId ?: "—"}")
                        if (result.parsedSenderPhone != null) append("\nSender: ${result.parsedSenderPhone}")
                    }
                )

                // 3. Auto-match dry run
                val (matchOk, matchLabel, matchDetail) = when {
                    result.wouldConfirm == true && result.matchOrderId != null ->
                        Triple(true, "Would match order #${result.matchOrderId}",
                            "Match kind: ${result.matchKind ?: "—"}")
                    result.wouldConfirm == true ->
                        Triple(true, "Would confirm a payment", "Match kind: ${result.matchKind ?: "—"}")
                    result.matchReason == "ambiguous" ->
                        Triple(false, "❓ Ambiguous — ${result.matchCandidates} candidates",
                            "Admin would need to match manually")
                    result.matchReason == "no-pending-order-matched" ->
                        Triple(false, "⚠️ No match",
                            "No pending order with this amount right now")
                    result.matchReason == "amount-not-parsed" ->
                        Triple(false, "⚠️ No match", "Amount could not be parsed")
                    result.matchReason == "dry-run-error" ->
                        Triple(false, "⚠️ Match check errored", "Server could not score orders")
                    else ->
                        Triple(false, "⚠️ Not evaluated", result.matchReason ?: "")
                }
                DiagRow(ok = matchOk, label = matchLabel, detail = matchDetail)

                // 4. Verify simulation
                if (result.wouldRejectAs != null) {
                    DiagRow(
                        ok = true,
                        label = "Verify simulation",
                        detail = result.wouldRejectAs!!
                    )
                }

                HorizontalDivider()
                Text(
                    "Dry-run only: the server saved nothing and touched no real order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun DiagRow(ok: Boolean, label: String, detail: String) {
    // Leading emoji: ✅ on success, ❌ on hard failure. The label itself
    // may carry ⚠️/❓ for soft states (no match / ambiguous).
    val icon = if (ok) "✅" else if (label.startsWith("❓") || label.startsWith("⚠️")) "" else "❌"
    Column {
        Text(
            text = "$icon $label".trim(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
