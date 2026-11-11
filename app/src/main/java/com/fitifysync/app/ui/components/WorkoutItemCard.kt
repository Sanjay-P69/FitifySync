package com.fitifysync.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitifysync.app.data.model.FitifyExerciseDetail
import com.fitifysync.app.data.model.FitifyWorkout
import com.fitifysync.app.ui.theme.HeartRed
import com.fitifysync.app.ui.theme.getHeartRateBadgeColors
import com.fitifysync.app.ui.theme.getRestSegmentColors
import com.fitifysync.app.ui.theme.getStatusBadgeColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Thread-safe immutable formatter hoisted to file scope (zero allocations during scroll)
private val workoutDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM dd, yyyy • HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

@Composable
fun WorkoutItemCard(
    workout: FitifyWorkout,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColors = getStatusBadgeColors(workout.isSynced)
    val hrBadgeColors = getHeartRateBadgeColors()
    val restColors = getRestSegmentColors()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Title, Date, Meta, Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = workout.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = workoutDateFormatter.format(workout.startTime),
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "🔥 ${workout.caloriesBurned.toInt()} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (workout.avgHeartRate != null) {
                            Text(
                                text = "❤️ ${workout.avgHeartRate} bpm",
                                style = MaterialTheme.typography.bodySmall,
                                color = HeartRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = statusColors.background,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (workout.isSynced) "Synced" else "Pending",
                            color = statusColors.content,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
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

            // Expanded Visual Segments Breakdown with smooth, non-overshooting spring physics
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioNoBouncy
                    )
                ) + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Heart Rate Pill from Samsung Health / Google Fit
                    if (workout.avgHeartRate != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(hrBadgeColors.background, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
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
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = hrBadgeColors.content
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Visual Progress / Segment Composition Bar (Canvas-rendered for 60fps fluid scroll)
                    Text(
                        text = "Exercise Sequence Timeline",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    ExerciseCompositionTimelineBar(exercises = workout.exercises)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Exercise Items List
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        workout.exercises.forEachIndexed { index, ex ->
                            // Rest interval before exercise if any
                            if (ex.restDurationSeconds > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(restColors.background, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "☕ Rest / Recovery",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = restColors.content
                                    )
                                    Text(
                                        text = "${ex.restDurationSeconds}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = restColors.content
                                    )
                                }
                            }

                            // Active Exercise Segment Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.width(22.dp)
                                    )
                                    Text(
                                        text = ex.exerciseName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    val durationOrReps = when {
                                        ex.durationSeconds > 0 -> "${ex.durationSeconds}s"
                                        ex.reps > 0 -> "${ex.reps} reps"
                                        else -> "Hold"
                                    }
                                    Text(
                                        text = durationOrReps,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
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

/**
 * High-performance Canvas timeline bar:
 * Draws directly on the Canvas without creating N layout nodes or running regex per frame.
 */
@Composable
fun ExerciseCompositionTimelineBar(
    exercises: List<FitifyExerciseDetail>,
    modifier: Modifier = Modifier
) {
    // Memoize segment colors keyed on exercises list
    val colors = remember(exercises) {
        exercises.map { getExerciseCategoryColor(it.code, it.exerciseName) }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        if (colors.isEmpty()) return@Canvas
        val total = colors.size
        val segmentWidth = size.width / total
        val cornerRadius = CornerRadius(2.dp.toPx())

        colors.forEachIndexed { i, color ->
            drawRoundRect(
                color = color,
                topLeft = Offset(i * segmentWidth + 0.5f, 0f),
                size = Size(segmentWidth - 1f, size.height),
                cornerRadius = cornerRadius
            )
        }
    }
}

private fun getExerciseCategoryColor(code: String, name: String): Color {
    val key = (code + "_" + name).lowercase(Locale.ROOT)
    return when {
        key.contains("plank") || key.contains("core") || key.contains("crunch") -> Color(0xFF00C896) // Emerald Mint
        key.contains("stretch") || key.contains("neck") || key.contains("warm") -> Color(0xFF00B4D8) // Cyan Sky
        key.contains("squat") || key.contains("lunge") || key.contains("push") -> Color(0xFF8B5CF6)  // Cosmic Purple
        key.contains("jump") || key.contains("hiit") || key.contains("burpee") -> Color(0xFFFF6B4A)  // Sunset Coral
        else -> Color(0xFF0D59F2) // Royal Blue
    }
}
