package com.fitifysync.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitifysync.app.ui.theme.SuccessGreen

@Composable
fun PrerequisitesCard(
    isRootGranted: Boolean?,
    isHcAvailable: Boolean,
    hasHcPerms: Boolean,
    onRequestRoot: () -> Unit,
    onRequestHcPerms: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "System Authorization",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Root Status Row
            StatusRow(
                title = if (isRootGranted == true) "Root Access: Granted" else "Root Access: Missing",
                subtitle = if (isRootGranted == true) "Magisk / KernelSU verified" else "Required to read Fitify private DB",
                isOk = isRootGranted == true,
                actionText = "Grant",
                onAction = onRequestRoot,
                showAction = isRootGranted != true
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Health Connect Status Row
            val hcTitle = when {
                !isHcAvailable -> "Health Connect: Unavailable"
                hasHcPerms -> "Health Connect: Authorized"
                else -> "Health Connect: Permission Needed"
            }
            val hcSubtitle = when {
                !isHcAvailable -> "Install Health Connect from Play Store"
                hasHcPerms -> "Syncing Exercise & Samsung Health HR"
                else -> "Allow Read/Write Exercise & HR"
            }

            StatusRow(
                title = hcTitle,
                subtitle = hcSubtitle,
                isOk = isHcAvailable && hasHcPerms,
                actionText = "Authorize",
                onAction = onRequestHcPerms,
                showAction = isHcAvailable && !hasHcPerms
            )
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    subtitle: String,
    isOk: Boolean,
    actionText: String,
    onAction: () -> Unit,
    showAction: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = if (isOk) SuccessGreen.copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isOk) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isOk) SuccessGreen else Color(0xFFFF5252),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        if (showAction) {
            FilledTonalButton(
                onClick = onAction,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
