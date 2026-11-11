package com.fitifysync.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitifysync.app.data.model.FitifyExerciseDetail
import com.fitifysync.app.data.model.FitifyWorkout
import com.fitifysync.app.ui.theme.HeartRed
import com.fitifysync.app.ui.theme.HeartRedBg
import com.fitifysync.app.ui.theme.SuccessGreen
import com.fitifysync.app.ui.theme.SuccessGreenBg
import com.fitifysync.app.ui.theme.WarningOrange
import com.fitifysync.app.ui.theme.WarningOrangeBg
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WorkoutItemCard(
    workout: FitifyWorkout,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val formatter = remember {
        DateTimeFormatter.ofPattern("MMM dd, yyyy • HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Title, Date, Stats, Sync Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workout.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = formatter.format(workout.startTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⏱ ${workout.durationSeconds / 60}m ${workout.durationSeconds % 60}s",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "🔥 ${workout.caloriesBurned.toInt()} kcal",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (workout.avgHeartRate != null) {
                            Text(
                                text = "❤️ ${workout.avgHeartRate} bpm",
                                style = MaterialTheme.typography.bodySmall,
                                color = HeartRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (workout.isSynced) SuccessGreenBg else WarningOrangeBg,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (workout.isSynced) "Synced" else "Pending",
                            color = if (workout.isSynced) SuccessGreen else WarningOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${workout.exercises.size} segments",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expanded Visual Segments Breakdown
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Heart Rate Pill from Samsung Health / Google Fit
                    if (workout.avgHeartRate != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(HeartRedBg, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = HeartRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Samsung Health HR: ${workout.avgHeartRate} bpm avg (Min: ${workout.minHeartRate ?: "--"} • Max: ${workout.maxHeartRate ?: "--"})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFB71C1C)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Visual Progress / Segment Composition Bar
                    Text(
                        text = "Exercise Sequence Timeline",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    ExerciseCompositionTimelineBar(exercises = workout.exercises)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Exercise Items List
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        workout.exercises.forEachIndexed { index, ex ->
                            // Rest interval before exercise if any
                            if (ex.restDurationSeconds > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Gray.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "☕ Rest / Transition",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "${ex.restDurationSeconds}s",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            // Active Exercise Segment Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray,
                                        modifier = Modifier.width(22.dp)
                                    )
                                    Text(
                                        text = ex.exerciseName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    val durationOrReps = when {
                                        ex.durationSeconds > 0 -> "${ex.durationSeconds}s"
                                        ex.reps > 0 -> "${ex.reps} reps"
                                        else -> "Hold"
                                    }
                                    Text(
                                        text = durationOrReps,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExerciseCompositionTimelineBar(exercises: List<FitifyExerciseDetail>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
    ) {
        val total = if (exercises.isNotEmpty()) exercises.size.toFloat() else 1f
        exercises.forEach { ex ->
            val color = when {
                ex.exerciseName.contains("Plank", ignoreCase = true) -> Color(0xFF43A047)
                ex.exerciseName.contains("Stretch", ignoreCase = true) || ex.exerciseName.contains("Neck", ignoreCase = true) -> Color(0xFF039BE5)
                ex.exerciseName.contains("Squat", ignoreCase = true) || ex.exerciseName.contains("Lunge", ignoreCase = true) -> Color(0xFFFB8C00)
                ex.exerciseName.contains("Crunch", ignoreCase = true) || ex.exerciseName.contains("Sit", ignoreCase = true) -> Color(0xFF8E24AA)
                else -> Color(0xFF5E35B1)
            }
            Box(
                modifier = Modifier
                    .weight(1f / total)
                    .fillMaxHeight()
                    .padding(horizontal = 0.5.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}
