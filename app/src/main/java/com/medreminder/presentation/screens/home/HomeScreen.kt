package com.medreminder.presentation.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medreminder.R
import com.medreminder.ai.AnalysisResult
import com.medreminder.ai.RiskLevel
import com.medreminder.domain.model.DoseLog
import com.medreminder.domain.model.DoseStatus
import com.medreminder.util.DateUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddMedication: () -> Unit,
    onEditMedication: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.generateTodayDoses() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Greeting header
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = uiState.greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Progress card
        item {
            TodayProgressCard(
                taken = uiState.takenCount,
                total = uiState.totalCount,
                rate = uiState.adherenceRate,
                streak = uiState.currentStreak
            )
        }

        // AI Analysis card
        item {
            AiInsightCard(
                analysis = uiState.aiAnalysis,
                isAnalyzing = uiState.isAnalyzing,
                onRunAnalysis = { viewModel.runDailyAnalysis() }
            )
        }

        // Refill alerts
        if (uiState.refillAlerts.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.refill_needed),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                uiState.refillAlerts.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // Section header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.today_medications),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = SimpleDateFormat("EEEE, MMMM d · h:mm a", Locale.getDefault()).format(Date()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(onClick = onAddMedication) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.add))
                }
            }
        }

        // Dose cards
        if (uiState.todayDoses.isEmpty() && !uiState.isLoading) {
            item {
                EmptyStateCard(onAddMedication)
            }
        }

        items(uiState.todayDoses, key = { it.id }) { dose ->
            DoseCard(
                dose = dose,
                onTaken = { viewModel.markDoseTaken(dose.id) },
                onSkip = { viewModel.markDoseSkipped(dose.id) },
                onSnooze = { viewModel.snoozeDose(dose.id) },
                onEdit = { onEditMedication(dose.medicationId) }
            )
        }

        // Bottom spacer
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
fun TodayProgressCard(taken: Int, total: Int, rate: Float, streak: Int) {
    val animatedRate by animateFloatAsState(
        targetValue = rate / 100f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.taken_of_total, taken, total),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        stringResource(R.string.medications_taken_today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                // Streak badge
                if (streak > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("\uD83D\uDD25", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (streak > 1) stringResource(R.string.days_streak, streak)
                                else stringResource(R.string.day_streak, streak),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedRate },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                stringResource(R.string.percent_complete, rate.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun DoseCard(
    dose: DoseLog,
    onTaken: () -> Unit,
    onSkip: () -> Unit,
    onSnooze: () -> Unit,
    onEdit: () -> Unit
) {
    val isDone = dose.status == DoseStatus.TAKEN || dose.status == DoseStatus.SKIPPED
    val isMissed = dose.status == DoseStatus.MISSED
    val pillColor = try { Color(dose.medicationColor.toColorInt()) } catch (_: Exception) { Color(0xFF4A90D9) }
    val containerColor = when {
        isDone -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        isMissed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pill indicator
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        pillColor.copy(alpha = if (isDone) 0.3f else 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    dose.status == DoseStatus.TAKEN ->
                        Icon(Icons.Default.Check, null, tint = pillColor, modifier = Modifier.size(28.dp))
                    dose.status == DoseStatus.SKIPPED ->
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                    dose.status == DoseStatus.MISSED ->
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                    else ->
                        Text("\uD83D\uDC8A", fontSize = 24.sp)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Medication info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dose.medicationName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dose.medicationDosage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = DateUtils.formatTimeOnly(dose.scheduledTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dose.status != DoseStatus.PENDING) {
                    StatusChip(dose.status)
                }
            }

            // Action buttons for pending/snoozed doses
            if (dose.status == DoseStatus.PENDING || dose.status == DoseStatus.SNOOZED) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledIconButton(
                        onClick = onTaken,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Check, stringResource(R.string.mark_taken), modifier = Modifier.size(24.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onSnooze, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Snooze, stringResource(R.string.snooze),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onSkip, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Close, stringResource(R.string.skip),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Action button for missed doses - allow marking as taken
            if (dose.status == DoseStatus.MISSED) {
                FilledIconButton(
                    onClick = onTaken,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.Check, stringResource(R.string.mark_taken), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: DoseStatus) {
    val (text, color) = when (status) {
        DoseStatus.TAKEN -> stringResource(R.string.status_taken) to MaterialTheme.colorScheme.primary
        DoseStatus.SKIPPED -> stringResource(R.string.status_skipped) to MaterialTheme.colorScheme.error
        DoseStatus.MISSED -> stringResource(R.string.status_missed) to MaterialTheme.colorScheme.error
        DoseStatus.SNOOZED -> stringResource(R.string.status_snoozed) to Color(0xFFF39C12)
        else -> return
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun EmptyStateCard(onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("\uD83D\uDC8A", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.no_medications_scheduled),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.add_first_medication),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAdd, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_medication))
            }
        }
    }
}

@Composable
fun AiInsightCard(
    analysis: AnalysisResult?,
    isAnalyzing: Boolean,
    onRunAnalysis: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.ai_daily_insight),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (analysis != null) {
                    val providerLabel = when (analysis.providerUsed) {
                        com.medreminder.ai.AiProviderType.CLOUD -> "Cloud"
                        com.medreminder.ai.AiProviderType.SYSTEM_AI -> "On-Device"
                        com.medreminder.ai.AiProviderType.CUSTOM_LOCAL -> "Local"
                        com.medreminder.ai.AiProviderType.AUTO -> "Auto"
                    }
                    Text(
                        providerLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isAnalyzing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.analyzing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (analysis != null) {
                Text(analysis.summary, style = MaterialTheme.typography.bodyMedium)

                if (analysis.recommendations.isNotEmpty()) {
                    val topRec = analysis.recommendations.first()
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Lightbulb,
                            null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            topRec,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onRunAnalysis,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.get_ai_insight))
                }
            }
        }
    }
}
