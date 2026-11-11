package com.fitifysync.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.fitifysync.app.ui.components.FloatingExpressiveHeader
import com.fitifysync.app.ui.components.HeroWorkoutStatsGrid
import com.fitifysync.app.ui.components.IntegrationsGuideCard
import com.fitifysync.app.ui.components.PrerequisitesCard
import com.fitifysync.app.ui.components.SyncActionCard
import com.fitifysync.app.ui.components.WorkoutItemCard
import com.fitifysync.app.ui.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    val contentPadding = WindowInsets.safeDrawing
        .add(WindowInsets(left = 16.dp, right = 16.dp, top = 12.dp, bottom = 32.dp))
        .asPaddingValues()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0)
    ) { _ ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Floating Expressive Pill Header
            item(contentType = "header") {
                FloatingExpressiveHeader(
                    hasWorkouts = state.workouts.isNotEmpty(),
                    isReady = state.canSync,
                    onExportCsv = { viewModel.exportToCsv() }
                )
            }

            // 2. Bento Grid Workout Dashboard (When Workouts Exist)
            if (state.workouts.isNotEmpty()) {
                item(contentType = "bento_stats") {
                    HeroWorkoutStatsGrid(stats = state.stats)
                }
            }

            // 3. Status Cards (Root & Health Connect)
            item(contentType = "prerequisites") {
                PrerequisitesCard(
                    isRootGranted = state.isRootGranted,
                    isHcAvailable = state.isHealthConnectAvailable,
                    hasHcPerms = state.hasHealthPermissions,
                    onRequestRoot = { viewModel.requestRoot() },
                    onRequestHcPerms = { viewModel.requestHealthPermissions() }
                )
            }

            // 4. Main Sync Action Engine Card
            item(contentType = "sync_action") {
                SyncActionCard(
                    isSyncing = state.isSyncing,
                    statusMessage = state.statusMessage,
                    canSync = state.canSync,
                    onSyncNow = { viewModel.triggerSync() }
                )
            }

            // 5. Automation & Ecosystem Guide Card
            item(contentType = "integrations_guide") {
                IntegrationsGuideCard(
                    isPeriodicActive = state.isPeriodicSyncActive,
                    onTogglePeriodic = { viewModel.togglePeriodicSync(it) }
                )
            }

            // 6. Workout Sessions Section Header
            item(contentType = "section_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Extracted Sessions (${state.workouts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (state.workouts.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.exportToCsv() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Export CSV",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 7. Workouts List or Empty State
            if (state.workouts.isEmpty()) {
                item(contentType = "empty_state") {
                    EmptyWorkoutState(isSyncing = state.isSyncing)
                }
            } else {
                items(
                    items = state.workouts,
                    key = { it.id },
                    contentType = { "workout" }
                ) { workout ->
                    WorkoutItemCard(
                        workout = workout,
                        isExpanded = state.expandedWorkoutIds.contains(workout.id),
                        onToggleExpand = { viewModel.toggleWorkoutExpansion(workout.id) }
                    )
                }
            }
        }
    }
}
