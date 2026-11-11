package com.fitifysync.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitifysync.app.data.model.FitifyWorkout
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestRoot: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onSyncNow: () -> Unit,
    onExportCsv: () -> Unit,
    onTogglePeriodic: (Boolean) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Fitify Sync", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onExportCsv) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 1. Status Cards (Root & Health Connect)
            item {
                StatusOverviewCard(
                    isRootGranted = state.isRootGranted,
                    isHcAvailable = state.isHealthConnectAvailable,
                    hasHcPerms = state.hasHealthPermissions,
                    onRequestRoot = onRequestRoot,
                    onRequestHcPerms = onRequestHealthPermissions
                )
            }

            // 2. Main Sync Action Card
            item {
                SyncActionCard(
                    isSyncing = state.isSyncing,
                    statusMessage = state.statusMessage,
                    canSync = state.isRootGranted == true && state.hasHealthPermissions,
                    onSyncNow = onSyncNow
                )
            }

            // 3. Automation & Samsung Health / Google Fit Integration Card
            item {
                IntegrationsGuideCard(
                    isPeriodicActive = state.isPeriodicSyncActive,
                    onTogglePeriodic = onTogglePeriodic
                )
            }

            // 4. Workout Sessions Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Extracted Sessions (${state.workouts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (state.workouts.isNotEmpty()) {
                        TextButton(onClick = onExportCsv) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export CSV")
                        }
                    }
                }
            }

            // 5. Workouts List
            if (state.workouts.isEmpty()) {
                item {
                    EmptyWorkoutState(isSyncing = state.isSyncing)
                }
            } else {
                items(state.workouts) { workout ->
                    WorkoutItemCard(workout = workout)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun StatusOverviewCard(
    isRootGranted: Boolean?,
    isHcAvailable: Boolean,
    hasHcPerms: Boolean,
    onRequestRoot: () -> Unit,
    onRequestHcPerms: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Prerequisites", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            // Root Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isRootGranted == true) Icons.Default.CheckCircle else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isRootGranted == true) Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isRootGranted == true) "Root Access: Granted" else "Root Access: Missing",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (isRootGranted != true) {
                    Button(onClick = onRequestRoot, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("Grant", fontSize = 12.sp)
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Health Connect Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (hasHcPerms) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (hasHcPerms) Color(0xFF2E7D32) else Color(0xFFE65100),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            !isHcAvailable -> "Health Connect: Not Installed"
                            hasHcPerms -> "Health Connect: Authorized"
                            else -> "Health Connect: Missing Permissions"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (isHcAvailable && !hasHcPerms) {
                    Button(onClick = onRequestHcPerms, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("Authorize", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SyncActionCard(
    isSyncing: Boolean,
    statusMessage: String,
    canSync: Boolean,
    onSyncNow: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Data Bridge Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSyncNow,
                enabled = canSync && !isSyncing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Extracting & Syncing...")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Extract & Sync Fitify Workouts")
                }
            }
        }
    }
}

@Composable
fun IntegrationsGuideCard(
    isPeriodicActive: Boolean,
    onTogglePeriodic: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Automated Background Sync", fontWeight = FontWeight.SemiBold)
                    Text("Syncs new Fitify sessions in the background every 6 hours", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Switch(
                    checked = isPeriodicActive,
                    onCheckedChange = onTogglePeriodic
                )
            }

            Divider(modifier = Modifier.padding(vertical = 10.dp))

            Text("How Samsung Health & Google Fit Receive Data:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "• Samsung Health: Open Samsung Health → Settings → Health Connect → Allow read & write.\n" +
                "• Google Fit: Open Google Fit → Settings → Sync with Health Connect → Turn On.\n" +
                "• Detailed Analysis: Health Connect automatically feeds full exercise duration, type, and active calories to both platforms.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.DarkGray
            )
        }
    }
}

@Composable
fun WorkoutItemCard(workout: FitifyWorkout) {
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy • HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(workout.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatter.format(workout.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⏱ ${workout.durationSeconds / 60}m ${workout.durationSeconds % 60}s", style = MaterialTheme.typography.bodySmall)
                    Text("🔥 ${workout.caloriesBurned.toInt()} kcal", style = MaterialTheme.typography.bodySmall)
                    Text("🏷 ${workout.workoutType}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Box(
                modifier = Modifier
                    .background(
                        color = if (workout.isSynced) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (workout.isSynced) "Synced" else "Pending",
                    color = if (workout.isSynced) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun EmptyWorkoutState(isSyncing: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CloudSync,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isSyncing) "Extracting Fitify sessions..." else "No workouts extracted yet.\nTap 'Extract & Sync Fitify Workouts' above.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}
