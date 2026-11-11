package com.fitifysync.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitifysync.app.ui.components.EmptyWorkoutState
import com.fitifysync.app.ui.components.IntegrationsGuideCard
import com.fitifysync.app.ui.components.PrerequisitesCard
import com.fitifysync.app.ui.components.SyncActionCard
import com.fitifysync.app.ui.components.WorkoutItemCard
import com.fitifysync.app.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        Text(
                            text = "Fitify Sync",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    if (state.workouts.isNotEmpty()) {
                        IconButton(onClick = { viewModel.exportToCsv() }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
                        }
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
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Status Cards (Root & Health Connect)
            item {
                PrerequisitesCard(
                    isRootGranted = state.isRootGranted,
                    isHcAvailable = state.isHealthConnectAvailable,
                    hasHcPerms = state.hasHealthPermissions,
                    onRequestRoot = { viewModel.requestRoot() },
                    onRequestHcPerms = { viewModel.requestHealthPermissions() }
                )
            }

            // 2. Main Sync Action Card
            item {
                SyncActionCard(
                    isSyncing = state.isSyncing,
                    statusMessage = state.statusMessage,
                    canSync = state.canSync,
                    onSyncNow = { viewModel.triggerSync() }
                )
            }

            // 3. Automation & Samsung Health / Google Fit Integration Card
            item {
                IntegrationsGuideCard(
                    isPeriodicActive = state.isPeriodicSyncActive,
                    onTogglePeriodic = { viewModel.togglePeriodicSync(it) }
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
                        TextButton(onClick = { viewModel.exportToCsv() }) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
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
                items(
                    items = state.workouts,
                    key = { it.id }
                ) { workout ->
                    WorkoutItemCard(workout = workout)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
