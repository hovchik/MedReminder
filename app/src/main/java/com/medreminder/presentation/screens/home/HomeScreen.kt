package com.medreminder.presentation.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medreminder.R
import com.medreminder.domain.model.DoseLog
import com.medreminder.domain.model.DoseStatus
import com.medreminder.util.DateUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.*

/**
 * Groups doses into time-of-day sections based on their scheduled hour.
 */
private enum class TimeSection(val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    MORNING(R.string.time_section_morning, Icons.Default.WbSunny),
    AFTERNOON(R.string.time_section_afternoon, Icons.Default.WbSunny),
    EVENING(R.string.time_section_evening, Icons.Default.Nightlight),
    NIGHT(R.string.time_section_night, Icons.Default.DarkMode);

    companion object {
        fun fromMillis(millis: Long): TimeSection {
            val hour = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
            return when {
                hour < 12 -> MORNING
                hour < 17 -> AFTERNOON
                hour < 21 -> EVENING
                else -> NIGHT
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddMedication: () -> Unit,
    onEditMedication: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    // Re-generate today's dose logs every time the screen resumes
    // (after add / edit / delete or returning from background).
    // LaunchedEffect(Unit) only fires once; the lifecycle observer
    // fires on every ON_RESUME so the screen always reflects changes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTodayScreen()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Greeting header
        item {
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
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

        // All done celebration
        if (uiState.totalCount > 0 && uiState.takenCount == uiState.totalCount && !uiState.isLoading) {
            item { AllDoneCard() }
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

        // Section header with add button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.today_medications),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FilledTonalButton(onClick = onAddMedication) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.add))
                }
            }
        }

        // Empty state
        if (uiState.todayDoses.isEmpty() && !uiState.isLoading) {
            item { EmptyStateCard(onAddMedication) }
        }

        // Dose cards - grouped by user if multi-user, then by time section
        val groups = uiState.userDoseGroups
        val hasMultipleGroups = groups.size > 1

        if (hasMultipleGroups) {
            groups.forEach { group ->
                val groupKey = group.userId?.toString() ?: "self"
                val isExpanded = expandedGroups.getOrDefault(groupKey, true)

                item(key = "header_$groupKey") {
                    UserSectionHeader(
                        userName = group.userName,
                        isSelf = group.userId == null,
                        takenCount = group.takenCount,
                        totalCount = group.totalCount,
                        expanded = isExpanded,
                        onToggle = { expandedGroups[groupKey] = !isExpanded }
                    )
                }

                if (isExpanded) {
                    val timeSections = group.doses.groupBy { TimeSection.fromMillis(it.scheduledTime) }
                    timeSections.forEach { (section, doses) ->
                        item(key = "time_${groupKey}_${section.name}") {
                            TimeSectionHeader(section = section, doseCount = doses.size)
                        }
                        items(doses, key = { it.id }) { dose ->
                            DoseCard(
                                dose = dose,
                                onTaken = { viewModel.markDoseTaken(dose.id) },
                                onSkip = { viewModel.markDoseSkipped(dose.id) },
                                onSnooze = { viewModel.snoozeDose(dose) },
                                onEdit = { onEditMedication(dose.medicationId) }
                            )
                        }
                    }
                }
            }
        } else if (uiState.todayDoses.isNotEmpty()) {
            // Single user - group by time of day
            val timeSections = uiState.todayDoses.groupBy { TimeSection.fromMillis(it.scheduledTime) }
            timeSections.forEach { (section, doses) ->
                item(key = "time_${section.name}") {
                    TimeSectionHeader(section = section, doseCount = doses.size)
                }
                items(doses, key = { it.id }) { dose ->
                    DoseCard(
                        dose = dose,
                        onTaken = { viewModel.markDoseTaken(dose.id) },
                        onSkip = { viewModel.markDoseSkipped(dose.id) },
                        onSnooze = { viewModel.snoozeDose(dose) },
                        onEdit = { onEditMedication(dose.medicationId) }
                    )
                }
            }
        }

        // Bottom spacer for navigation bar
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun TimeSectionHeader(section: TimeSection, doseCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(section.labelRes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun AllDoneCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2ECC71).copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("\u2705", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(R.string.all_done_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF27AE60)
                )
                Text(
                    stringResource(R.string.all_done_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF27AE60).copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun UserSectionHeader(
    userName: String,
    isSelf: Boolean,
    takenCount: Int,
    totalCount: Int,
    expanded: Boolean = true,
    onToggle: () -> Unit = {}
) {
    val accentColor = if (isSelf) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.tertiary
    val progress = if (totalCount > 0) takenCount.toFloat() / totalCount else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "userProgress"
    )
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(300),
        label = "chevron"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelf)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                accentColor.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelf) Icons.Default.Person else Icons.Default.Face,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = accentColor
                        )
                    }
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = stringResource(R.string.taken_of_total_short, takenCount, totalCount),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = rotationAngle },
                    tint = accentColor
                )
            }

            // Per-user progress bar
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                trackColor = accentColor.copy(alpha = 0.15f),
                color = accentColor
            )
        }
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
    val isSnoozed = dose.status == DoseStatus.SNOOZED
    val isOverdue = dose.status == DoseStatus.PENDING &&
            dose.scheduledTime < System.currentTimeMillis()
    val pillColor = try { Color(dose.medicationColor.toColorInt()) } catch (_: Exception) { Color(0xFF4A90D9) }

    val containerColor = when {
        isDone -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        isMissed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        isSnoozed -> Color(0xFFF39C12).copy(alpha = 0.08f)
        isOverdue -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        isOverdue && !isDone && !isMissed -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
        isSnoozed -> Color(0xFFF39C12).copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 0.dp else 2.dp),
        border = if (borderColor != Color.Transparent)
            BorderStroke(1.dp, borderColor) else null
    ) {
        Column {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pill indicator
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            pillColor.copy(alpha = if (isDone) 0.3f else 0.15f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        dose.status == DoseStatus.TAKEN ->
                            Icon(Icons.Default.Check, null, tint = pillColor, modifier = Modifier.size(26.dp))
                        dose.status == DoseStatus.SKIPPED ->
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(26.dp))
                        dose.status == DoseStatus.MISSED ->
                            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(26.dp))
                        dose.status == DoseStatus.SNOOZED ->
                            Icon(Icons.Default.Snooze, null, tint = Color(0xFFF39C12), modifier = Modifier.size(26.dp))
                        else ->
                            Text("\uD83D\uDC8A", fontSize = 22.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Medication info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dose.medicationName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (dose.medicationDosage.isNotBlank()) {
                        Text(
                            text = dose.medicationDosage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Time and status row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = DateUtils.formatTimeOnly(dose.scheduledTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Contextual label
                        if (isOverdue && !isDone && !isMissed) {
                            StatusChip(text = stringResource(R.string.overdue), color = MaterialTheme.colorScheme.error)
                        } else if (isSnoozed && dose.snoozedUntil != null) {
                            StatusChip(
                                text = stringResource(R.string.snoozed_until, DateUtils.formatTimeOnly(dose.snoozedUntil)),
                                color = Color(0xFFF39C12)
                            )
                        } else if (dose.status == DoseStatus.PENDING && dose.scheduledTime > System.currentTimeMillis()) {
                            val minutesUntil = ((dose.scheduledTime - System.currentTimeMillis()) / 60000).toInt()
                            if (minutesUntil in 1..120) {
                                val timeText = if (minutesUntil >= 60) {
                                    "${minutesUntil / 60}h ${minutesUntil % 60}m"
                                } else {
                                    "${minutesUntil}m"
                                }
                                StatusChip(
                                    text = stringResource(R.string.next_dose_in, timeText),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else if (dose.status != DoseStatus.PENDING) {
                            StatusChip(dose.status)
                        }
                    }
                }

                // Action buttons
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
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Default.Check, stringResource(R.string.mark_taken), modifier = Modifier.size(22.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = onSnooze, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    Icons.Default.Snooze, stringResource(R.string.snooze),
                                    modifier = Modifier.size(18.dp),
                                    tint = Color(0xFFF39C12)
                                )
                            }
                            IconButton(onClick = onSkip, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    Icons.Default.Close, stringResource(R.string.skip),
                                    modifier = Modifier.size(18.dp),
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
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(Icons.Default.Check, stringResource(R.string.mark_taken), modifier = Modifier.size(22.dp))
                    }
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
    StatusChip(text = text, color = color)
}

@Composable
fun StatusChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
