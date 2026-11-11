package com.fitifysync.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitifysync.app.ui.state.WorkoutStats
import com.fitifysync.app.ui.theme.HeartRed

@Composable
fun HeroWorkoutStatsGrid(
    stats: WorkoutStats,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Sessions & Duration
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoStatTile(
                modifier = Modifier.weight(1f),
                title = "Total Sessions",
                value = stats.totalSessions.toString(),
                subtitle = "Synced to HC",
                icon = Icons.Default.Bolt,
                iconTint = Color(0xFF00C896),
                iconBg = Color(0xFF00C896).copy(alpha = 0.15f)
            )

            BentoStatTile(
                modifier = Modifier.weight(1f),
                title = "Training Time",
                value = stats.formattedDuration,
                subtitle = "Accumulated",
                icon = Icons.Default.Timer,
                iconTint = Color(0xFF0D59F2),
                iconBg = Color(0xFF0D59F2).copy(alpha = 0.15f)
            )
        }

        // Row 2: Active Energy & Samsung Health HR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoStatTile(
                modifier = Modifier.weight(1f),
                title = "Active Energy",
                value = stats.formattedCalories,
                subtitle = "Caloric Burn",
                icon = Icons.Default.LocalFireDepartment,
                iconTint = Color(0xFFFF6B4A),
                iconBg = Color(0xFFFF6B4A).copy(alpha = 0.15f)
            )

            val hrValue = if (stats.avgHeartRate != null) "${stats.avgHeartRate} bpm" else "-- bpm"
            val hrSubtitle = if (stats.minHeartRate != null && stats.maxHeartRate != null) {
                "${stats.minHeartRate}-${stats.maxHeartRate} bpm"
            } else {
                "Samsung Health"
            }

            BentoStatTile(
                modifier = Modifier.weight(1f),
                title = "Avg Heart Rate",
                value = hrValue,
                subtitle = hrSubtitle,
                icon = Icons.Default.Favorite,
                iconTint = HeartRed,
                iconBg = HeartRed.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun BentoStatTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(iconBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
